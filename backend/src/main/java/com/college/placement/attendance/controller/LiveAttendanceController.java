package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.attendance.AttendanceCorrectionRequest;
import com.college.placement.attendance.dto.attendance.DriveAttendanceSummaryDto;
import com.college.placement.attendance.dto.attendance.DriveStudentRecordDto;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.LiveAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drives/{id}/attendance")
@RequiredArgsConstructor
@Tag(name = "Live Attendance & Corrections", description = "Real-time SSE attendance monitoring and audited manual overrides")
public class LiveAttendanceController {

    private final LiveAttendanceService liveAttendanceService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Subscribe to live Server-Sent Events (SSE) feed for drive check-ins")
    public SseEmitter streamLiveAttendance(@PathVariable Long id) {
        return liveAttendanceService.subscribeToStream(id);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Get aggregated live attendance statistics for drive")
    public ResponseEntity<DriveAttendanceSummaryDto> getAttendanceSummary(@PathVariable Long id) {
        return ResponseEntity.ok(liveAttendanceService.getAttendanceSummary(id));
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Get searchable eligible student records and attendance status")
    public ResponseEntity<List<DriveStudentRecordDto>> getAttendanceRecords(
            @PathVariable Long id,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(liveAttendanceService.getAttendanceRecords(id, query, status));
    }

    @PostMapping("/corrections")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Submit audited manual attendance correction with mandatory reason")
    public ResponseEntity<DriveStudentRecordDto> recordCorrection(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        DriveStudentRecordDto dto = liveAttendanceService.recordCorrection(id, request, currentUser, httpRequest);
        return ResponseEntity.ok(dto);
    }
}
