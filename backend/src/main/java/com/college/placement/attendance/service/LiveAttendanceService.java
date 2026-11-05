package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.attendance.AttendanceCorrectionRequest;
import com.college.placement.attendance.dto.attendance.DriveAttendanceSummaryDto;
import com.college.placement.attendance.dto.attendance.DriveStudentRecordDto;
import com.college.placement.attendance.entity.*;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.ConflictException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAttendanceService {

    private final CompanyDriveRepository driveRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // Thread-safe map of SSE emitters keyed by drive ID
    private final Map<Long, List<SseEmitter>> driveEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeToStream(Long driveId) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout

        driveEmitters.computeIfAbsent(driveId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(driveId, emitter));
        emitter.onTimeout(() -> removeEmitter(driveId, emitter));
        emitter.onError(e -> removeEmitter(driveId, emitter));

        try {
            // Send initial connection handshake event
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Connected to live attendance stream for drive " + drive.getDriveTitle(),
                                 "driveId", driveId, "timestamp", Instant.now().toString())));
        } catch (IOException e) {
            removeEmitter(driveId, emitter);
        }

        return emitter;
    }

    public void broadcastUpdate(Long driveId, String eventName, Object data) {
        List<SseEmitter> emitters = driveEmitters.get(driveId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private void removeEmitter(Long driveId, SseEmitter emitter) {
        List<SseEmitter> list = driveEmitters.get(driveId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                driveEmitters.remove(driveId);
            }
        }
    }

    @Transactional(readOnly = true)
    public DriveAttendanceSummaryDto getAttendanceSummary(Long driveId) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        long totalEligible = eligibilityRepository.countByDriveId(driveId);
        long presentCount = recordRepository.countByDriveId(driveId);
        long absentCount = Math.max(0, totalEligible - presentCount);
        double percentage = totalEligible > 0 ? ((double) presentCount / totalEligible) * 100.0 : 0.0;

        return DriveAttendanceSummaryDto.builder()
                .driveId(drive.getId())
                .companyName(drive.getCompanyName())
                .driveTitle(drive.getDriveTitle())
                .driveDate(drive.getDriveDate())
                .venue(drive.getVenue())
                .status(drive.getStatus().name())
                .totalEligible(totalEligible)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .attendancePercentage(Math.round(percentage * 100.0) / 100.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DriveStudentRecordDto> getAttendanceRecords(Long driveId, String query, String statusFilter) {
        List<DriveEligibility> eligibilities = eligibilityRepository.findByDriveId(driveId);
        List<AttendanceRecord> records = recordRepository.findByDriveId(driveId);

        Map<Long, AttendanceRecord> recordMap = new HashMap<>();
        for (AttendanceRecord r : records) {
            recordMap.put(r.getStudent().getId(), r);
        }

        List<DriveStudentRecordDto> result = new ArrayList<>();
        for (DriveEligibility eligibility : eligibilities) {
            Student s = eligibility.getStudent();
            AttendanceRecord record = recordMap.get(s.getId());
            boolean isPresent = record != null;

            String status = isPresent ? "PRESENT" : "ABSENT";

            // Status filter
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
                if (!status.equalsIgnoreCase(statusFilter)) {
                    continue;
                }
            }

            // Query filter (roll number, name, email)
            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase().trim();
                boolean matches = s.getStudentId().toLowerCase().contains(q)
                        || s.getFullName().toLowerCase().contains(q)
                        || s.getEmail().toLowerCase().contains(q);
                if (!matches) {
                    continue;
                }
            }

            result.add(DriveStudentRecordDto.builder()
                    .studentId(s.getId())
                    .studentRollNumber(s.getStudentId())
                    .studentName(s.getFullName())
                    .email(s.getEmail())
                    .contactNumber(s.getContactNumber())
                    .department(s.getDepartment())
                    .status(status)
                    .recordedAt(isPresent ? record.getRecordedAt() : null)
                    .verificationStatus(isPresent ? record.getVerificationStatus() : "NOT_ATTENDED")
                    .build());
        }

        // Sort: PRESENT first, then alphabetical by student roll number
        result.sort(Comparator.comparing(DriveStudentRecordDto::getStatus).reversed()
                .thenComparing(DriveStudentRecordDto::getStudentRollNumber));

        return result;
    }

    @Transactional
    public DriveStudentRecordDto recordCorrection(Long driveId, AttendanceCorrectionRequest request, UserPrincipal officerUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + request.getStudentId()));

        if (!eligibilityRepository.existsByDriveIdAndStudentId(driveId, student.getId())) {
            throw new BadRequestException("Student is not eligible for this drive.");
        }

        User officer = userRepository.findById(officerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found"));

        Optional<AttendanceRecord> existingRecord = recordRepository.findByDriveIdAndStudentId(driveId, student.getId());
        String targetStatus = request.getNewStatus().trim().toUpperCase();

        if ("PRESENT".equals(targetStatus)) {
            if (existingRecord.isPresent()) {
                throw new ConflictException("Student is already marked PRESENT.");
            }

            // Create override record
            AttendanceRecord overrideRecord = AttendanceRecord.builder()
                    .drive(drive)
                    .student(student)
                    .device(null) // Manual override has no device
                    .station(null)
                    .challenge(null)
                    .verificationStatus("MANUAL_OVERRIDE")
                    .signatureBase64("MANUAL_OVERRIDE_OFFICER_" + officer.getId())
                    .recordedAt(Instant.now())
                    .build();

            AttendanceRecord saved = recordRepository.save(overrideRecord);

            // Log correction
            AttendanceCorrection correction = AttendanceCorrection.builder()
                    .drive(drive)
                    .student(student)
                    .officer(officer)
                    .previousStatus("ABSENT")
                    .newStatus("PRESENT")
                    .reason(request.getReason().trim())
                    .build();
            correctionRepository.save(correction);

            auditService.recordAction(officerUser.getId(), "ATTENDANCE_CORRECTION_OVERRIDE", "DRIVE", driveId.toString(),
                    "Manual override: marked student " + student.getStudentId() + " PRESENT. Reason: " + request.getReason(), httpRequest);

            DriveStudentRecordDto dto = DriveStudentRecordDto.builder()
                    .studentId(student.getId())
                    .studentRollNumber(student.getStudentId())
                    .studentName(student.getFullName())
                    .email(student.getEmail())
                    .contactNumber(student.getContactNumber())
                    .department(student.getDepartment())
                    .status("PRESENT")
                    .recordedAt(saved.getRecordedAt())
                    .verificationStatus("MANUAL_OVERRIDE")
                    .build();

            broadcastUpdate(driveId, "ATTENDANCE_CORRECTED", dto);
            return dto;

        } else if ("ABSENT".equals(targetStatus)) {
            if (existingRecord.isEmpty()) {
                throw new ConflictException("Student is already ABSENT.");
            }

            recordRepository.delete(existingRecord.get());

            AttendanceCorrection correction = AttendanceCorrection.builder()
                    .drive(drive)
                    .student(student)
                    .officer(officer)
                    .previousStatus("PRESENT")
                    .newStatus("ABSENT")
                    .reason(request.getReason().trim())
                    .build();
            correctionRepository.save(correction);

            auditService.recordAction(officerUser.getId(), "ATTENDANCE_CORRECTION_OVERRIDE", "DRIVE", driveId.toString(),
                    "Manual override: marked student " + student.getStudentId() + " ABSENT. Reason: " + request.getReason(), httpRequest);

            DriveStudentRecordDto dto = DriveStudentRecordDto.builder()
                    .studentId(student.getId())
                    .studentRollNumber(student.getStudentId())
                    .studentName(student.getFullName())
                    .email(student.getEmail())
                    .contactNumber(student.getContactNumber())
                    .department(student.getDepartment())
                    .status("ABSENT")
                    .recordedAt(null)
                    .verificationStatus("NOT_ATTENDED")
                    .build();

            broadcastUpdate(driveId, "ATTENDANCE_CORRECTED", dto);
            return dto;
        } else {
            throw new BadRequestException("Invalid status: " + request.getNewStatus() + ". Expected PRESENT or ABSENT.");
        }
    }
}
