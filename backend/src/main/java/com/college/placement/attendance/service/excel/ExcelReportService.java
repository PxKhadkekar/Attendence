package com.college.placement.attendance.service.excel;

import com.college.placement.attendance.entity.*;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final CompanyDriveRepository driveRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceAttemptRepository attemptRepository;
    private final AuditService auditService;

    private static final String[] ATTENDANCE_HEADERS = {
            "Student ID",
            "Student Name",
            "Institutional Email",
            "Contact Number",
            "Company",
            "Drive Date",
            "Status",
            "Attendance Timestamp",
            "Verification Status"
    };

    private static final String[] AUDIT_HEADERS = {
            "Attempt Time",
            "Student ID",
            "Student Name",
            "Outcome",
            "Failure Reason"
    };

    @Transactional(readOnly = true)
    public byte[] exportDriveReport(Long driveId, boolean includeAudit, UserPrincipal currentUser, HttpServletRequest request) throws IOException {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        // 1. Fetch data from DB
        List<DriveEligibility> eligibleList = eligibilityRepository.findByDriveId(driveId);
        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByDriveId(driveId);
        List<AttendanceAttempt> attempts = includeAudit ? attemptRepository.findByDriveIdOrderByAttemptedAtDesc(driveId) : Collections.emptyList();

        Map<Long, AttendanceRecord> recordByStudentId = attendanceRecords.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        List<DriveEligibility> presentEligibles = new ArrayList<>();
        List<DriveEligibility> absentEligibles = new ArrayList<>();

        for (DriveEligibility eligibility : eligibleList) {
            if (recordByStudentId.containsKey(eligibility.getStudent().getId())) {
                presentEligibles.add(eligibility);
            } else {
                absentEligibles.add(eligibility);
            }
        }

        // Verify count parity between DB and generated data before writing
        int dbEligibleCount = eligibleList.size();
        int dbPresentCount = attendanceRecords.size();
        int dbAbsentCount = dbEligibleCount - dbPresentCount;

        if (presentEligibles.size() != dbPresentCount) {
            throw new BadRequestException("Data count parity mismatch for Present list: DB count=" + dbPresentCount + ", list=" + presentEligibles.size());
        }
        if (absentEligibles.size() != dbAbsentCount) {
            throw new BadRequestException("Data count parity mismatch for Absent list: DB count=" + dbAbsentCount + ", list=" + absentEligibles.size());
        }

        // 2. Build SXSSFWorkbook (streaming low-memory workbook)
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);

            // Styles & Formats
            CreationHelper createHelper = workbook.getCreationHelper();

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(createHelper.createDataFormat().getFormat("0.0%"));

            // ----------------------------------------------------
            // SHEET 1: Summary
            // ----------------------------------------------------
            SXSSFSheet summarySheet = workbook.createSheet("Summary");
            createSummarySheet(summarySheet, drive, dbEligibleCount, dbPresentCount, dbAbsentCount, headerStyle, dateStyle, dateTimeStyle, percentStyle);

            // ----------------------------------------------------
            // SHEET 2: Present
            // ----------------------------------------------------
            SXSSFSheet presentSheet = workbook.createSheet("Present");
            createAttendanceSheet(presentSheet, drive, presentEligibles, recordByStudentId, true, headerStyle, dateStyle, dateTimeStyle);

            // ----------------------------------------------------
            // SHEET 3: Absent
            // ----------------------------------------------------
            SXSSFSheet absentSheet = workbook.createSheet("Absent");
            createAttendanceSheet(absentSheet, drive, absentEligibles, recordByStudentId, false, headerStyle, dateStyle, dateTimeStyle);

            // ----------------------------------------------------
            // SHEET 4: All Attendance
            // ----------------------------------------------------
            SXSSFSheet allSheet = workbook.createSheet("All Attendance");
            createAttendanceSheet(allSheet, drive, eligibleList, recordByStudentId, null, headerStyle, dateStyle, dateTimeStyle);

            // ----------------------------------------------------
            // SHEET 5: Verification Audit (Optional Officer Flag)
            // ----------------------------------------------------
            if (includeAudit) {
                SXSSFSheet auditSheet = workbook.createSheet("Verification Audit");
                createAuditSheet(auditSheet, attempts, headerStyle, dateTimeStyle);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            // 3. Log audit event
            auditService.recordAction(currentUser.getId(), "EXCEL_REPORT_EXPORTED", "DRIVE", driveId.toString(),
                    "Exported drive report. Eligible: " + dbEligibleCount + ", Present: " + dbPresentCount + ", IncludeAudit: " + includeAudit, request);

            return outputStream.toByteArray();
        }
    }

    private void createSummarySheet(SXSSFSheet sheet, CompanyDrive drive, int total, int present, int absent,
                                    CellStyle headerStyle, CellStyle dateStyle, CellStyle dateTimeStyle, CellStyle percentStyle) {
        String[][] metrics = {
                {"Company Name", ExcelSecurityValidator.escapeForExport(drive.getCompanyName())},
                {"Drive Title", ExcelSecurityValidator.escapeForExport(drive.getDriveTitle())},
                {"Venue", ExcelSecurityValidator.escapeForExport(drive.getVenue())},
                {"Drive Date", drive.getDriveDate().toString()},
                {"Total Eligible Students", String.valueOf(total)},
                {"Total Present Students", String.valueOf(present)},
                {"Total Absent Students", String.valueOf(absent)},
                {"Attendance Rate", total > 0 ? String.format("%.2f%%", ((double) present / total) * 100) : "0.00%"},
                {"Report Generated At", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault()))}
        };

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        Cell h1 = headerRow.createCell(0);
        h1.setCellValue("Metric");
        h1.setCellStyle(headerStyle);
        Cell h2 = headerRow.createCell(1);
        h2.setCellValue("Value");
        h2.setCellStyle(headerStyle);

        for (int i = 0; i < metrics.length; i++) {
            Row row = sheet.createRow(i + 1);
            Cell c1 = row.createCell(0);
            c1.setCellValue(metrics[i][0]);

            Cell c2 = row.createCell(1);
            c2.setCellValue(metrics[i][1]);
        }

        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 45 * 256);
    }

    private void createAttendanceSheet(SXSSFSheet sheet, CompanyDrive drive, List<DriveEligibility> list,
                                       Map<Long, AttendanceRecord> recordMap, Boolean isPresentFilter,
                                       CellStyle headerStyle, CellStyle dateStyle, CellStyle dateTimeStyle) {
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < ATTENDANCE_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(ATTENDANCE_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (DriveEligibility eligibility : list) {
            Student student = eligibility.getStudent();
            AttendanceRecord record = recordMap.get(student.getId());

            boolean isPresent = record != null;

            Row row = sheet.createRow(rowIdx++);

            // Student ID (Escaped)
            Cell c0 = row.createCell(0);
            c0.setCellValue(ExcelSecurityValidator.escapeForExport(student.getStudentId()));

            // Student Name (Escaped)
            Cell c1 = row.createCell(1);
            c1.setCellValue(ExcelSecurityValidator.escapeForExport(student.getFullName()));

            // Email (Escaped)
            Cell c2 = row.createCell(2);
            c2.setCellValue(ExcelSecurityValidator.escapeForExport(student.getEmail()));

            // Contact (Escaped)
            Cell c3 = row.createCell(3);
            c3.setCellValue(ExcelSecurityValidator.escapeForExport(student.getContactNumber()));

            // Company (Escaped)
            Cell c4 = row.createCell(4);
            c4.setCellValue(ExcelSecurityValidator.escapeForExport(drive.getCompanyName()));

            // Drive Date (Real Excel Date)
            Cell c5 = row.createCell(5);
            c5.setCellValue(Date.valueOf(drive.getDriveDate()));
            c5.setCellStyle(dateStyle);

            // Status
            Cell c6 = row.createCell(6);
            c6.setCellValue(isPresent ? "PRESENT" : "ABSENT");

            // Attendance Timestamp (Real Excel DateTime)
            Cell c7 = row.createCell(7);
            if (isPresent && record.getRecordedAt() != null) {
                c7.setCellValue(java.util.Date.from(record.getRecordedAt()));
                c7.setCellStyle(dateTimeStyle);
            } else {
                c7.setCellValue("");
            }

            // Verification Status
            Cell c8 = row.createCell(8);
            c8.setCellValue(isPresent ? record.getVerificationStatus() : "NOT_ATTENDED");
        }

        sheet.createFreezePane(0, 1);
        if (!list.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, list.size(), 0, ATTENDANCE_HEADERS.length - 1));
        }

        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 26 * 256);
        sheet.setColumnWidth(2, 32 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 22 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 14 * 256);
        sheet.setColumnWidth(7, 24 * 256);
        sheet.setColumnWidth(8, 20 * 256);
    }

    private void createAuditSheet(SXSSFSheet sheet, List<AttendanceAttempt> attempts, CellStyle headerStyle, CellStyle dateTimeStyle) {
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < AUDIT_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(AUDIT_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (AttendanceAttempt attempt : attempts) {
            Row row = sheet.createRow(rowIdx++);

            // Attempt Time (Real Excel DateTime)
            Cell c0 = row.createCell(0);
            if (attempt.getAttemptedAt() != null) {
                c0.setCellValue(java.util.Date.from(attempt.getAttemptedAt()));
                c0.setCellStyle(dateTimeStyle);
            } else {
                c0.setCellValue("");
            }

            // Student ID (Escaped)
            Cell c1 = row.createCell(1);
            c1.setCellValue(attempt.getStudent() != null ? ExcelSecurityValidator.escapeForExport(attempt.getStudent().getStudentId()) : "N/A");

            // Student Name (Escaped)
            Cell c2 = row.createCell(2);
            c2.setCellValue(attempt.getStudent() != null ? ExcelSecurityValidator.escapeForExport(attempt.getStudent().getFullName()) : "N/A");

            // Outcome
            Cell c3 = row.createCell(3);
            c3.setCellValue(attempt.getOutcome());

            // Failure Reason (Escaped, sanitized - no keys/tokens)
            Cell c4 = row.createCell(4);
            c4.setCellValue(attempt.getFailureReason() != null ? ExcelSecurityValidator.escapeForExport(attempt.getFailureReason()) : "");
        }

        sheet.createFreezePane(0, 1);
        if (!attempts.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, attempts.size(), 0, AUDIT_HEADERS.length - 1));
        }

        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 26 * 256);
        sheet.setColumnWidth(3, 20 * 256);
        sheet.setColumnWidth(4, 45 * 256);
    }
}
