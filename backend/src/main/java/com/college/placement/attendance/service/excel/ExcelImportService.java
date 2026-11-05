package com.college.placement.attendance.service.excel;

import com.college.placement.attendance.dto.excel.ImportPreviewResponse;
import com.college.placement.attendance.dto.excel.ImportSummaryResponse;
import com.college.placement.attendance.dto.excel.RowErrorDto;
import com.college.placement.attendance.entity.*;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.InvalidStateTransitionException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    private final ExcelSecurityValidator securityValidator;
    private final CompanyDriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final ImportJobRepository importJobRepository;
    private final ImportErrorRepository importErrorRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${app.institution.email-domain:@college.edu}")
    private String institutionalEmailDomain;

    @Value("${app.excel.max-rows:5000}")
    private int maxRowsLimit;

    @Transactional
    public ImportPreviewResponse uploadAndPreview(Long driveId, MultipartFile file, UserPrincipal currentUser, HttpServletRequest request) {
        // 1. Security & Format Validation
        securityValidator.validateFile(file);

        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (drive.getStatus() == DriveStatus.FINALIZED || drive.getStatus() == DriveStatus.CLOSED) {
            throw new InvalidStateTransitionException("Cannot import eligibility list for a drive in " + drive.getStatus() + " state.");
        }

        User uploader = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUser.getId()));

        List<RowErrorDto> errors = new ArrayList<>();
        List<Long> validStudentIds = new ArrayList<>();
        Set<String> seenStudentIds = new HashSet<>();

        int totalRows = 0;

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            // 2. Validate Sheet Presence
            Sheet sheet = workbook.getSheet(ExcelTemplateService.SHEET_NAME);
            if (sheet == null) {
                throw new BadRequestException("Required sheet '" + ExcelTemplateService.SHEET_NAME + "' not found in workbook.");
            }

            // 3. Validate Header Row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("Workbook header row (Row 1) is missing.");
            }

            validateHeaders(headerRow);

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum > maxRowsLimit) {
                throw new BadRequestException("File exceeds maximum allowed rows limit of " + maxRowsLimit + " (found " + lastRowNum + " rows).");
            }

            // 4. Process Data Rows (Row 1 to lastRowNum)
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRowNumber = r + 1; // 1-indexed for user visibility

                String studentId = getCellStringValue(row, 0);
                String studentName = getCellStringValue(row, 1);
                String email = getCellStringValue(row, 2);
                String contactNumber = getCellStringValue(row, 3);

                // Row-level validation checks
                String validationError = validateRowData(studentId, studentName, email, contactNumber, seenStudentIds);

                if (validationError != null) {
                    errors.add(RowErrorDto.builder()
                            .rowNumber(displayRowNumber)
                            .studentId(studentId)
                            .studentName(studentName)
                            .email(email)
                            .contactNumber(contactNumber)
                            .errorReason(validationError)
                            .build());
                    continue;
                }

                // Check against master student database
                Optional<Student> studentOpt = studentRepository.findByStudentId(studentId);
                if (studentOpt.isEmpty()) {
                    errors.add(RowErrorDto.builder()
                            .rowNumber(displayRowNumber)
                            .studentId(studentId)
                            .studentName(studentName)
                            .email(email)
                            .contactNumber(contactNumber)
                            .errorReason("Student ID '" + studentId + "' is not registered in institutional master student records")
                            .build());
                    continue;
                }

                Student student = studentOpt.get();
                if (!student.getEmail().equalsIgnoreCase(email)) {
                    errors.add(RowErrorDto.builder()
                            .rowNumber(displayRowNumber)
                            .studentId(studentId)
                            .studentName(studentName)
                            .email(email)
                            .contactNumber(contactNumber)
                            .errorReason("Email '" + email + "' does not match master record email for Student ID '" + studentId + "'")
                            .build());
                    continue;
                }

                // Valid row
                seenStudentIds.add(studentId.toUpperCase());
                validStudentIds.add(student.getId());
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Excel workbook: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to read Excel workbook: " + e.getMessage());
        }

        // 5. Persist ImportJob and ImportErrors
        ImportJob importJob = ImportJob.builder()
                .drive(drive)
                .uploadedBy(uploader)
                .originalFilename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .totalRows(totalRows)
                .validRows(validStudentIds.size())
                .invalidRows(errors.size())
                .status("PREVIEWED")
                .errorSummary(errors.isEmpty() ? null : errors.size() + " rows failed validation")
                .storedFilePath(serializeValidStudentIds(validStudentIds)) // Stash valid student IDs as JSON string in storedFilePath
                .build();

        ImportJob savedJob = importJobRepository.save(importJob);

        for (RowErrorDto err : errors) {
            ImportError importError = ImportError.builder()
                    .importJob(savedJob)
                    .rowNumber(err.getRowNumber())
                    .studentId(err.getStudentId())
                    .studentName(err.getStudentName())
                    .email(err.getEmail())
                    .contactNumber(err.getContactNumber())
                    .errorReason(err.getErrorReason())
                    .build();
            importErrorRepository.save(importError);
        }

        auditService.recordAction(currentUser.getId(), "EXCEL_IMPORT_PREVIEWED", "DRIVE", driveId.toString(),
                "Previewed Excel import for drive " + driveId + ": " + validStudentIds.size() + " valid, " + errors.size() + " invalid", request);

        return ImportPreviewResponse.builder()
                .importJobId(savedJob.getId())
                .driveId(driveId)
                .filename(file.getOriginalFilename())
                .totalRows(totalRows)
                .validRows(validStudentIds.size())
                .invalidRows(errors.size())
                .errors(errors)
                .build();
    }

    @Transactional
    public ImportSummaryResponse confirmImport(Long driveId, Long jobId, UserPrincipal currentUser, HttpServletRequest request) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        if (drive.getStatus() == DriveStatus.FINALIZED || drive.getStatus() == DriveStatus.CLOSED) {
            throw new InvalidStateTransitionException("Cannot import eligibility list into a drive in " + drive.getStatus() + " state.");
        }

        ImportJob importJob = importJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + jobId));

        if (!importJob.getDrive().getId().equals(driveId)) {
            throw new BadRequestException("Import job does not belong to specified drive.");
        }

        if ("COMMITTED".equalsIgnoreCase(importJob.getStatus())) {
            throw new BadRequestException("Import job has already been committed.");
        }

        List<Long> studentIdsToImport = deserializeValidStudentIds(importJob.getStoredFilePath());

        int committedCount = 0;
        for (Long studentId : studentIdsToImport) {
            if (!eligibilityRepository.existsByDriveIdAndStudentId(driveId, studentId)) {
                Student student = studentRepository.findById(studentId).orElse(null);
                if (student != null) {
                    DriveEligibility eligibility = DriveEligibility.builder()
                            .drive(drive)
                            .student(student)
                            .importJob(importJob)
                            .build();
                    eligibilityRepository.save(eligibility);
                    committedCount++;
                }
            }
        }

        importJob.setStatus("COMMITTED");
        importJobRepository.save(importJob);

        auditService.recordAction(currentUser.getId(), "EXCEL_IMPORT_CONFIRMED", "DRIVE", driveId.toString(),
                "Committed " + committedCount + " eligible students to drive " + driveId, request);

        return ImportSummaryResponse.builder()
                .importJobId(jobId)
                .driveId(driveId)
                .importedCount(committedCount)
                .status("COMMITTED")
                .message("Successfully imported " + committedCount + " eligible students.")
                .build();
    }

    private void validateHeaders(Row headerRow) {
        for (int i = 0; i < ExcelTemplateService.HEADERS.length; i++) {
            Cell cell = headerRow.getCell(i);
            String cellVal = cell != null ? cell.getStringCellValue().trim() : "";
            if (!ExcelTemplateService.HEADERS[i].equalsIgnoreCase(cellVal)) {
                throw new BadRequestException("Invalid column header at position " + (i + 1) + ". Expected '" +
                        ExcelTemplateService.HEADERS[i] + "', found '" + cellVal + "'.");
            }
        }
    }

    private String validateRowData(String studentId, String studentName, String email, String contactNumber, Set<String> seenStudentIds) {
        if (studentId == null || studentId.isBlank()) {
            return "Student ID is required";
        }
        if (studentName == null || studentName.isBlank()) {
            return "Student Name is required";
        }
        if (email == null || email.isBlank()) {
            return "Institutional Email is required";
        }
        if (contactNumber == null || contactNumber.isBlank()) {
            return "Contact Number is required";
        }

        // Email format check
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Email format is invalid";
        }

        // Institutional domain check
        if (!email.toLowerCase().endsWith(institutionalEmailDomain.toLowerCase())) {
            return "Email must belong to the institutional domain: " + institutionalEmailDomain;
        }

        // Duplicate within file
        if (seenStudentIds.contains(studentId.toUpperCase())) {
            return "Duplicate row: Student ID '" + studentId + "' appears more than once in this file";
        }

        return null;
    }

    private String getCellStringValue(Row row, int colIndex) {
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                long numVal = (long) cell.getNumericCellValue();
                yield String.valueOf(numVal);
            }
            case FORMULA -> {
                // Threat defense: treat formula cells strictly as raw formula string, do NOT evaluate
                yield cell.getCellFormula().trim();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String serializeValidStudentIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> deserializeValidStudentIds(String json) {
        try {
            if (json == null || json.isBlank()) return Collections.emptyList();
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
