package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.dto.drive.UpdateDriveRequest;
import com.college.placement.attendance.entity.DriveStatus;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.DriveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drives")
@RequiredArgsConstructor
@Tag(name = "Recruitment Drives", description = "Management and lifecycle transitions for placement recruitment drives")
public class DriveController {

    private final DriveService driveService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Create a new placement drive in DRAFT state", description = "Officer-only endpoint to register a new drive")
    public ResponseEntity<DriveResponse> createDrive(
            @Valid @RequestBody CreateDriveRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        DriveResponse response = driveService.createDrive(request, currentUser, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Update drive metadata", description = "Allowed only when drive is in DRAFT or SCHEDULED state")
    public ResponseEntity<DriveResponse> updateDrive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDriveRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        DriveResponse response = driveService.updateDrive(id, request, currentUser, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get drive by ID", description = "Returns drive details and current attendance statistics")
    public ResponseEntity<DriveResponse> getDriveById(@PathVariable Long id) {
        return ResponseEntity.ok(driveService.getDriveById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "List all placement drives with pagination and filters")
    public ResponseEntity<Page<DriveResponse>> listDrives(
            @RequestParam(required = false) DriveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "driveDate"));
        return ResponseEntity.ok(driveService.listDrives(status, date, pageRequest));
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Transition drive from DRAFT to SCHEDULED", description = "Requires confirmed eligible student list")
    public ResponseEntity<DriveResponse> scheduleDrive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(driveService.scheduleDrive(id, currentUser, httpRequest));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Open attendance window for recruitment drive")
    public ResponseEntity<DriveResponse> openDrive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(driveService.openDrive(id, currentUser, httpRequest));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Close attendance window for recruitment drive")
    public ResponseEntity<DriveResponse> closeDrive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(driveService.closeDrive(id, currentUser, httpRequest));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Finalize drive", description = "Locks drive records against further standard edits")
    public ResponseEntity<DriveResponse> finalizeDrive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(driveService.finalizeDrive(id, currentUser, httpRequest));
    }

    @GetMapping("/my-eligible")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Get authenticated student's eligible drives", description = "Protected against IDOR; uses JWT principal")
    public ResponseEntity<List<DriveResponse>> getMyEligibleDrives(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(driveService.getMyEligibleDrives(currentUser.getId()));
    }
}
