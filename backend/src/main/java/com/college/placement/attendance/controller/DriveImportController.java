package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.excel.ImportPreviewResponse;
import com.college.placement.attendance.dto.excel.ImportSummaryResponse;
import com.college.placement.attendance.entity.ImportError;
import com.college.placement.attendance.repository.ImportErrorRepository;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.excel.ExcelImportService;
import com.college.placement.attendance.service.excel.ExcelTemplateService;
import com.college.placement.attendance.service.excel.RejectedRowsExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drives")
@RequiredArgsConstructor
@Tag(name = "Drive Excel Eligibility", description = "Excel-only data pipeline for uploading and confirming eligible students")
public class DriveImportController {

    private final ExcelTemplateService templateService;
    private final ExcelImportService importService;
    private final RejectedRowsExportService rejectedRowsExportService;
    private final ImportErrorRepository importErrorRepository;

    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Download clean .xlsx template for eligible students")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = templateService.generateBlankTemplate();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eligible_students_template.xlsx\"")
                .contentType(MediaType.parseMediaType(EXCEL_MIME))
                .body(content);
    }

    @PostMapping(value = "/{id}/imports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Upload .xlsx eligibility list and preview results", description = "Performs strict OOXML validation, POI zip-bomb protection, and row-level checks")
    public ResponseEntity<ImportPreviewResponse> uploadAndPreview(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest request) {
        ImportPreviewResponse preview = importService.uploadAndPreview(id, file, currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(preview);
    }

    @PostMapping("/{id}/imports/{jobId}/confirm")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Confirm eligibility import into drive in a single atomic transaction")
    public ResponseEntity<ImportSummaryResponse> confirmImport(
            @PathVariable Long id,
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest request) {
        ImportSummaryResponse summary = importService.confirmImport(id, jobId, currentUser, request);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}/imports/{jobId}/rejected-rows")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Download .xlsx containing only the rejected rows with Error Reason column")
    public ResponseEntity<byte[]> downloadRejectedRows(
            @PathVariable Long id,
            @PathVariable Long jobId) throws IOException {
        List<ImportError> errors = importErrorRepository.findByImportJobIdOrderByRowNumberAsc(jobId);
        byte[] content = rejectedRowsExportService.exportRejectedRows(errors);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rejected_rows_job_" + jobId + ".xlsx\"")
                .contentType(MediaType.parseMediaType(EXCEL_MIME))
                .body(content);
    }
}
