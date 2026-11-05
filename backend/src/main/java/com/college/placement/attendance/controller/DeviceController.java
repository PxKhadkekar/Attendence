package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.device.DeviceResponse;
import com.college.placement.attendance.dto.device.RegisterDeviceRequest;
import com.college.placement.attendance.dto.device.RevokeDeviceRequest;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.DeviceService;
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
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Student Devices", description = "Hardware cryptographic device registration and trust management")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Register student phone device and NIST P-256 public key",
               description = "Enrolls device in PENDING_APPROVAL state awaiting officer verification")
    public ResponseEntity<DeviceResponse> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        DeviceResponse response = deviceService.registerDevice(request, currentUser, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-device")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Get current authenticated student's device registration status")
    public ResponseEntity<DeviceResponse> getMyDevice(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(deviceService.getMyDevice(currentUser));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "List all student devices pending officer approval")
    public ResponseEntity<List<DeviceResponse>> getPendingDevices() {
        return ResponseEntity.ok(deviceService.getPendingDevices());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Approve student device registration")
    public ResponseEntity<DeviceResponse> approveDevice(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(deviceService.approveDevice(id, currentUser, httpRequest));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Revoke device public key with mandatory audit reason")
    public ResponseEntity<DeviceResponse> revokeDevice(
            @PathVariable Long id,
            @Valid @RequestBody RevokeDeviceRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(deviceService.revokeDevice(id, request, currentUser, httpRequest));
    }
}
