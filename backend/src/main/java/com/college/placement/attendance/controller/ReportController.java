package com.college.placement.attendance.controller;

import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.excel.ExcelReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports & Exports", description = "Audited Excel report generation using SXSSFWorkbook with database count parity")
public class ReportController {

    private final ExcelReportService excelReportService;

    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @GetMapping("/drives/{id}/export")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Export multi-sheet .xlsx attendance report for recruitment drive",
               description = "Sheets: Summary, Present, Absent, All Attendance, and optional Verification Audit. Verified against database count parity.")
    public ResponseEntity<byte[]> exportDriveReport(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeAudit,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest request) throws IOException {

        byte[] reportContent = excelReportService.exportDriveReport(id, includeAudit, currentUser, request);

        String filename = "drive_" + id + "_attendance_report.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_MIME))
                .body(reportContent);
    }
}
