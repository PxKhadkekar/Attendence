package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.auth.RefreshTokenRequest;
import com.college.placement.attendance.dto.auth.UserProfileResponse;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.entity.User;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.exception.UnauthorizedException;
import com.college.placement.attendance.repository.StudentRepository;
import com.college.placement.attendance.repository.UserRepository;
import com.college.placement.attendance.security.JwtTokenProvider;
import com.college.placement.attendance.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);
        UserProfileResponse profile = buildUserProfile(userPrincipal.getId());

        auditService.recordAction(userPrincipal.getId(), "USER_LOGIN", "USER", userPrincipal.getId().toString(),
                "User successfully logged in", httpRequest);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(profile)
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (!tokenProvider.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String tokenType = tokenProvider.getTokenType(token);
        if (!"REFRESH".equalsIgnoreCase(tokenType)) {
            throw new BadRequestException("Supplied token is not a refresh token");
        }

        Long userId = tokenProvider.getUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for refresh token"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("User account is inactive");
        }

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String newAccessToken = tokenProvider.generateAccessToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(userPrincipal);
        UserProfileResponse profile = buildUserProfile(userId);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(profile)
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse buildUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Optional<Student> studentOpt = studentRepository.findByUserId(userId);

        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().getName());

        studentOpt.ifPresent(student -> {
            builder.studentDatabaseId(student.getId());
            builder.studentId(student.getStudentId());
            builder.department(student.getDepartment());
            builder.batchYear(student.getBatchYear());
        });

        return builder.build();
    }
}
