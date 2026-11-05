package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.device.DeviceResponse;
import com.college.placement.attendance.dto.device.RegisterDeviceRequest;
import com.college.placement.attendance.dto.device.RevokeDeviceRequest;
import com.college.placement.attendance.entity.DeviceStatus;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.entity.StudentDevice;
import com.college.placement.attendance.entity.User;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.ConflictException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.StudentDeviceRepository;
import com.college.placement.attendance.repository.StudentRepository;
import com.college.placement.attendance.repository.UserRepository;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.crypto.CryptoVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final StudentDeviceRepository deviceRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CryptoVerificationService cryptoService;
    private final AuditService auditService;

    @Transactional
    public DeviceResponse registerDevice(RegisterDeviceRequest request, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        Student student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + currentUser.getId()));

        // 1. Verify and parse public key PEM
        PublicKey publicKey = cryptoService.parsePublicKeyFromPem(request.getPublicKeyPem());
        String fingerprint = cryptoService.computePublicKeyFingerprint(publicKey);

        // Check if key fingerprint is already registered on another student's device
        Optional<StudentDevice> existingWithKey = deviceRepository.findByPublicKeyFingerprint(fingerprint);
        if (existingWithKey.isPresent() && !existingWithKey.get().getStudent().getId().equals(student.getId())) {
            throw new ConflictException("Public key is already registered to another student account.");
        }

        // 2. Revoke any previously active or pending device for this student to maintain single-device policy
        List<StudentDevice> existingDevices = deviceRepository.findByStudentId(student.getId());
        for (StudentDevice dev : existingDevices) {
            if (dev.getStatus() != DeviceStatus.REVOKED) {
                dev.setStatus(DeviceStatus.REVOKED);
                dev.setRevokedAt(Instant.now());
                dev.setRevocationReason("Replaced by new device registration");
                deviceRepository.save(dev);
            }
        }

        // 3. Register new device
        StudentDevice newDevice = StudentDevice.builder()
                .student(student)
                .deviceFingerprint(request.getDeviceFingerprint().trim())
                .deviceModel(request.getDeviceModel().trim())
                .osVersion(request.getOsVersion().trim())
                .publicKeyPem(request.getPublicKeyPem().trim())
                .publicKeyFingerprint(fingerprint)
                .status(DeviceStatus.PENDING_APPROVAL)
                .build();

        StudentDevice saved = deviceRepository.save(newDevice);

        auditService.recordAction(currentUser.getId(), "DEVICE_REGISTERED", "DEVICE", saved.getId().toString(),
                "Registered device: " + saved.getDeviceModel() + " (Fingerprint: " + fingerprint + ")", httpRequest);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DeviceResponse getMyDevice(UserPrincipal currentUser) {
        Student student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        // Return approved device first, otherwise latest pending/registered
        List<StudentDevice> devices = deviceRepository.findByStudentId(student.getId());
        StudentDevice device = devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.APPROVED)
                .findFirst()
                .or(() -> devices.stream().filter(d -> d.getStatus() == DeviceStatus.PENDING_APPROVAL).findFirst())
                .or(() -> devices.stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("No device registered for student"));

        return toResponse(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getPendingDevices() {
        return deviceRepository.findByStatus(DeviceStatus.PENDING_APPROVAL)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public DeviceResponse approveDevice(Long deviceId, UserPrincipal officerUser, HttpServletRequest httpRequest) {
        StudentDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        if (device.getStatus() == DeviceStatus.APPROVED) {
            throw new BadRequestException("Device is already approved.");
        }

        User approver = userRepository.findById(officerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Approver user not found"));

        device.setStatus(DeviceStatus.APPROVED);
        device.setApprovedBy(approver);
        device.setApprovedAt(Instant.now());
        StudentDevice updated = deviceRepository.save(device);

        auditService.recordAction(officerUser.getId(), "DEVICE_APPROVED", "DEVICE", deviceId.toString(),
                "Approved device for student " + device.getStudent().getStudentId(), httpRequest);

        return toResponse(updated);
    }

    @Transactional
    public DeviceResponse revokeDevice(Long deviceId, RevokeDeviceRequest request, UserPrincipal officerUser, HttpServletRequest httpRequest) {
        StudentDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        User revoker = userRepository.findById(officerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Revoker user not found"));

        device.setStatus(DeviceStatus.REVOKED);
        device.setRevokedBy(revoker);
        device.setRevokedAt(Instant.now());
        device.setRevocationReason(request.getReason().trim());
        StudentDevice updated = deviceRepository.save(device);

        auditService.recordAction(officerUser.getId(), "DEVICE_REVOKED", "DEVICE", deviceId.toString(),
                "Revoked device for student " + device.getStudent().getStudentId() + ": " + request.getReason(), httpRequest);

        return toResponse(updated);
    }

    private DeviceResponse toResponse(StudentDevice device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .studentId(device.getStudent().getId())
                .studentRollNumber(device.getStudent().getStudentId())
                .studentName(device.getStudent().getFullName())
                .deviceFingerprint(device.getDeviceFingerprint())
                .deviceModel(device.getDeviceModel())
                .osVersion(device.getOsVersion())
                .publicKeyFingerprint(device.getPublicKeyFingerprint())
                .status(device.getStatus())
                .approvedAt(device.getApprovedAt())
                .revokedAt(device.getRevokedAt())
                .revocationReason(device.getRevocationReason())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
