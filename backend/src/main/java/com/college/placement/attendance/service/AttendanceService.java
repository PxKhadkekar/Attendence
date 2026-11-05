package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.attendance.*;
import com.college.placement.attendance.entity.*;
import com.college.placement.attendance.exception.*;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.crypto.CryptoVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceChallengeRepository challengeRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceAttemptRepository attemptRepository;
    private final CompanyDriveRepository driveRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final StudentRepository studentRepository;
    private final StudentDeviceRepository deviceRepository;
    private final BleStationRepository stationRepository;
    private final CryptoVerificationService cryptoService;
    private final AuditService auditService;
    private final LiveAttendanceService liveAttendanceService;

    @Value("${app.attendance.challenge-expiration-seconds:120}")
    private long challengeExpirationSeconds;

    @Transactional
    public AttendanceSessionResponse createAttendanceSession(AttendanceSessionRequest request, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        Student student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + currentUser.getId()));

        CompanyDrive drive = driveRepository.findById(request.getDriveId())
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + request.getDriveId()));

        StudentDevice device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + request.getDeviceId()));

        BleStation station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + request.getStationId()));

        // 1. Validate device ownership & status
        if (!device.getStudent().getId().equals(student.getId())) {
            throw new BadRequestException("Device does not belong to the authenticated student.");
        }
        if (device.getStatus() == DeviceStatus.REVOKED) {
            recordAttempt(drive, student, device, station, "DEVICE_REVOKED", "Device has been revoked", httpRequest);
            throw new UnauthorizedException("This device has been revoked and cannot be used for attendance.");
        }
        if (device.getStatus() != DeviceStatus.APPROVED) {
            throw new BadRequestException("Device is pending officer approval.");
        }

        // 2. Validate drive status
        if (drive.getStatus() != DriveStatus.OPEN) {
            recordAttempt(drive, student, device, station, "DRIVE_CLOSED", "Drive is not currently OPEN (Status: " + drive.getStatus() + ")", httpRequest);
            throw new ConflictException("Drive attendance window is not OPEN. Current status: " + drive.getStatus());
        }

        // 3. Validate station status
        if (!Boolean.TRUE.equals(station.getIsActive())) {
            throw new BadRequestException("Selected BLE station is not currently active.");
        }

        // 4. Validate student eligibility
        boolean isEligible = eligibilityRepository.existsByDriveIdAndStudentId(drive.getId(), student.getId());
        if (!isEligible) {
            recordAttempt(drive, student, device, station, "INELIGIBLE", "Student not on confirmed drive eligibility list", httpRequest);
            throw new BadRequestException("You are not on the eligible students list for this drive.");
        }

        // 5. Validate no existing attendance
        boolean alreadyMarked = recordRepository.existsByDriveIdAndStudentId(drive.getId(), student.getId());
        if (alreadyMarked) {
            recordAttempt(drive, student, device, station, "ALREADY_MARKED", "Duplicate check-in attempt", httpRequest);
            throw new ConflictException("Attendance has already been recorded for this drive.");
        }

        // 6. Issue cryptographically secure single-use challenge
        String challengeToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(challengeExpirationSeconds);

        String canonicalTemplate = challengeToken + "|" + student.getId() + "|" + drive.getId() + "|" + device.getId() + "|" + station.getId() + "|<timestampEpochSec>";

        AttendanceChallenge challenge = AttendanceChallenge.builder()
                .challengeToken(challengeToken)
                .drive(drive)
                .student(student)
                .device(device)
                .station(station)
                .canonicalPayload(canonicalTemplate)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        challengeRepository.save(challenge);

        return AttendanceSessionResponse.builder()
                .challengeToken(challengeToken)
                .expiresAt(expiresAt)
                .validitySeconds(challengeExpirationSeconds)
                .driveId(drive.getId())
                .stationId(station.getId())
                .deviceId(device.getId())
                .canonicalFormat(canonicalTemplate)
                .build();
    }

    @Transactional
    public AttendanceVerifyResponse verifyAttendance(AttendanceVerifyRequest request, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        // 1. Fetch challenge with pessimistic write lock to prevent race conditions
        AttendanceChallenge challenge = challengeRepository.findByChallengeTokenForUpdate(request.getChallengeToken())
                .orElseThrow(() -> new BadRequestException("Invalid or non-existent challenge token."));

        Student student = challenge.getStudent();
        CompanyDrive drive = challenge.getDrive();
        StudentDevice device = challenge.getDevice();
        BleStation station = challenge.getStation();

        // 2. Validate challenge ownership
        if (!student.getUser().getId().equals(currentUser.getId())) {
            recordAttempt(drive, student, device, station, "UNAUTHORIZED_CHALLENGE", "Challenge was not issued to authenticated user", httpRequest);
            throw new BadRequestException("Challenge token was not issued to the authenticated student.");
        }

        // 3. Validate single-use flag
        if (Boolean.TRUE.equals(challenge.getUsed())) {
            recordAttempt(drive, student, device, station, "ALREADY_MARKED", "Replay attempt on consumed challenge", httpRequest);
            throw new ConflictException("Attendance challenge has already been used. Replay rejected.");
        }

        // 4. Validate expiry
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            recordAttempt(drive, student, device, station, "CHALLENGE_EXPIRED", "Challenge expired before submission", httpRequest);
            throw new BadRequestException("Attendance challenge has expired. Please request a new session.");
        }

        // 5. Validate drive status is still OPEN
        if (drive.getStatus() != DriveStatus.OPEN) {
            recordAttempt(drive, student, device, station, "DRIVE_CLOSED", "Drive is no longer open", httpRequest);
            throw new ConflictException("Drive attendance window has closed.");
        }

        // 6. Validate device has not been revoked in the interim
        if (device.getStatus() == DeviceStatus.REVOKED) {
            recordAttempt(drive, student, device, station, "DEVICE_REVOKED", "Device revoked prior to verification", httpRequest);
            throw new UnauthorizedException("Device has been revoked.");
        }

        // 7. Validate duplicate attendance prevention safety net
        if (recordRepository.existsByDriveIdAndStudentId(drive.getId(), student.getId())) {
            recordAttempt(drive, student, device, station, "ALREADY_MARKED", "Attendance already recorded for drive", httpRequest);
            throw new ConflictException("Attendance has already been recorded for this drive.");
        }

        // 8. Reconstruct canonical message and verify ECDSA signature
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                challenge.getChallengeToken(),
                student.getId(),
                drive.getId(),
                device.getId(),
                station.getId(),
                request.getTimestampEpochSec()
        );

        PublicKey publicKey = cryptoService.parsePublicKeyFromPem(device.getPublicKeyPem());
        boolean signatureValid = cryptoService.verifySignature(publicKey, canonicalMessage, request.getSignatureBase64());

        if (!signatureValid) {
            recordAttempt(drive, student, device, station, "SIGNATURE_INVALID", "ECDSA P-256 signature verification failed", httpRequest);
            throw new BadRequestException("Biometric cryptographic signature verification failed. Attendance rejected.");
        }

        // 9. Consume challenge and insert attendance record in single atomic transaction
        challenge.setUsed(true);
        challenge.setUsedAt(Instant.now());
        challengeRepository.save(challenge);

        AttendanceRecord record = AttendanceRecord.builder()
                .drive(drive)
                .student(student)
                .device(device)
                .station(station)
                .challenge(challenge)
                .verificationStatus("VERIFIED")
                .signatureBase64(request.getSignatureBase64())
                .recordedAt(Instant.now())
                .build();

        AttendanceRecord savedRecord = recordRepository.save(record);

        // 10. Record success attempt, audit, and broadcast live update
        recordAttempt(drive, student, device, station, "SUCCESS", null, httpRequest);

        liveAttendanceService.broadcastUpdate(drive.getId(), "ATTENDANCE_MARKED", java.util.Map.of(
                "recordId", savedRecord.getId(),
                "studentId", student.getId(),
                "studentRollNumber", student.getStudentId(),
                "studentName", student.getFullName(),
                "status", "PRESENT",
                "verificationStatus", "VERIFIED",
                "recordedAt", savedRecord.getRecordedAt().toString()
        ));

        auditService.recordAction(currentUser.getId(), "ATTENDANCE_VERIFIED", "ATTENDANCE_RECORD", savedRecord.getId().toString(),
                "Verified attendance for student " + student.getStudentId() + " in drive " + drive.getId(), httpRequest);

        return AttendanceVerifyResponse.builder()
                .attendanceRecordId(savedRecord.getId())
                .driveId(drive.getId())
                .companyName(drive.getCompanyName())
                .driveTitle(drive.getDriveTitle())
                .studentId(student.getId())
                .studentName(student.getFullName())
                .recordedAt(savedRecord.getRecordedAt())
                .verificationStatus(savedRecord.getVerificationStatus())
                .message("Attendance successfully verified and recorded.")
                .build();
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceHistoryDto> getStudentHistory(UserPrincipal currentUser) {
        Student student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        List<AttendanceRecord> records = recordRepository.findByStudentIdOrderByRecordedAtDesc(student.getId());

        return records.stream().map(r -> StudentAttendanceHistoryDto.builder()
                .recordId(r.getId())
                .driveId(r.getDrive().getId())
                .companyName(r.getDrive().getCompanyName())
                .driveTitle(r.getDrive().getDriveTitle())
                .driveDate(r.getDrive().getDriveDate())
                .venue(r.getDrive().getVenue())
                .recordedAt(r.getRecordedAt())
                .verificationStatus(r.getVerificationStatus())
                .build()).toList();
    }

    private void recordAttempt(CompanyDrive drive, Student student, StudentDevice device, BleStation station,
                               String outcome, String failureReason, HttpServletRequest request) {
        String ip = request != null ? request.getRemoteAddr() : null;
        String ua = request != null ? request.getHeader("User-Agent") : null;

        AttendanceAttempt attempt = AttendanceAttempt.builder()
                .drive(drive)
                .student(student)
                .device(device)
                .station(station)
                .outcome(outcome)
                .failureReason(failureReason)
                .ipAddress(ip)
                .userAgent(ua)
                .attemptedAt(Instant.now())
                .build();

        attemptRepository.save(attempt);
    }
}
