package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.auth.RefreshTokenRequest;
import com.college.placement.attendance.dto.auth.UserProfileResponse;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and session token operations")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login with username or email and password", description = "Returns access JWT, refresh token, and user profile")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh expired access token", description = "Exchanges a valid refresh token for a fresh access token")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated profile", description = "Returns user information and student details if user is a student")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(authService.buildUserProfile(currentUser.getId()));
    }
}
