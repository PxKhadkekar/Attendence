package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.attendance.*;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance Protocol", description = "Cryptographic biometric attendance challenge issuance and verification")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Request single-use attendance challenge session",
               description = "Validates student eligibility, drive status=OPEN, device=APPROVED, and station status before issuing nonce")
    public ResponseEntity<AttendanceSessionResponse> createSession(
            @Valid @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        AttendanceSessionResponse response = attendanceService.createAttendanceSession(request, currentUser, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Submit biometric cryptographic signature to record attendance",
               description = "Verifies ECDSA P-256 signature against registered public key; atomically commits attendance record")
    public ResponseEntity<AttendanceVerifyResponse> verifyAttendance(
            @Valid @RequestBody AttendanceVerifyRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        AttendanceVerifyResponse response = attendanceService.verifyAttendance(request, currentUser, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Get authenticated student's attendance history",
               description = "Protected against IDOR; returns only the current student's records")
    public ResponseEntity<List<StudentAttendanceHistoryDto>> getHistory(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(attendanceService.getStudentHistory(currentUser));
    }
}
