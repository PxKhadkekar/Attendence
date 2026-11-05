package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.dto.drive.UpdateDriveRequest;
import com.college.placement.attendance.entity.CompanyDrive;
import com.college.placement.attendance.entity.DriveStatus;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.entity.User;
import com.college.placement.attendance.exception.BadRequestException;
import com.college.placement.attendance.exception.InvalidStateTransitionException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveService {

    private final CompanyDriveRepository driveRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    @Transactional
    public DriveResponse createDrive(CreateDriveRequest request, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        User creator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Creator user not found"));

        CompanyDrive drive = CompanyDrive.builder()
                .companyName(request.getCompanyName().trim())
                .driveTitle(request.getDriveTitle().trim())
                .description(request.getDescription())
                .driveDate(request.getDriveDate())
                .venue(request.getVenue().trim())
                .status(DriveStatus.DRAFT)
                .createdBy(creator)
                .build();

        CompanyDrive saved = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_CREATED", "DRIVE", saved.getId().toString(),
                "Created drive: " + saved.getDriveTitle() + " for " + saved.getCompanyName(), httpRequest);

        return toResponse(saved);
    }

    @Transactional
    public DriveResponse updateDrive(Long driveId, UpdateDriveRequest request, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (drive.getStatus() == DriveStatus.FINALIZED || drive.getStatus() == DriveStatus.CLOSED) {
            throw new InvalidStateTransitionException("Cannot edit drive in " + drive.getStatus() + " state");
        }

        drive.setCompanyName(request.getCompanyName().trim());
        drive.setDriveTitle(request.getDriveTitle().trim());
        drive.setDescription(request.getDescription());
        drive.setDriveDate(request.getDriveDate());
        drive.setVenue(request.getVenue().trim());

        CompanyDrive updated = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_UPDATED", "DRIVE", updated.getId().toString(),
                "Updated drive details", httpRequest);

        return toResponse(updated);
    }

    @Transactional
    public DriveResponse scheduleDrive(Long driveId, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (!drive.getStatus().canTransitionTo(DriveStatus.SCHEDULED)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition drive from " + drive.getStatus() + " to " + DriveStatus.SCHEDULED);
        }

        long eligibleCount = eligibilityRepository.countByDriveId(driveId);
        if (eligibleCount == 0) {
            throw new BadRequestException("Cannot schedule drive without eligible students list. Please import student list first.");
        }

        drive.setStatus(DriveStatus.SCHEDULED);
        CompanyDrive updated = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_SCHEDULED", "DRIVE", driveId.toString(),
                "Transitioned drive to SCHEDULED", httpRequest);

        return toResponse(updated);
    }

    @Transactional
    public DriveResponse openDrive(Long driveId, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (!drive.getStatus().canTransitionTo(DriveStatus.OPEN)) {
            throw new InvalidStateTransitionException(
                    "Cannot open drive from current status: " + drive.getStatus());
        }

        drive.setStatus(DriveStatus.OPEN);
        drive.setOpenedAt(Instant.now());
        CompanyDrive updated = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_OPENED", "DRIVE", driveId.toString(),
                "Opened attendance window for drive", httpRequest);

        return toResponse(updated);
    }

    @Transactional
    public DriveResponse closeDrive(Long driveId, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (!drive.getStatus().canTransitionTo(DriveStatus.CLOSED)) {
            throw new InvalidStateTransitionException(
                    "Cannot close drive from current status: " + drive.getStatus());
        }

        drive.setStatus(DriveStatus.CLOSED);
        drive.setClosedAt(Instant.now());
        CompanyDrive updated = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_CLOSED", "DRIVE", driveId.toString(),
                "Closed attendance window for drive", httpRequest);

        return toResponse(updated);
    }

    @Transactional
    public DriveResponse finalizeDrive(Long driveId, UserPrincipal currentUser, HttpServletRequest httpRequest) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        if (!drive.getStatus().canTransitionTo(DriveStatus.FINALIZED)) {
            throw new InvalidStateTransitionException(
                    "Cannot finalize drive from current status: " + drive.getStatus() + ". Drive must be CLOSED first.");
        }

        drive.setStatus(DriveStatus.FINALIZED);
        drive.setFinalizedAt(Instant.now());
        CompanyDrive updated = driveRepository.save(drive);

        auditService.recordAction(currentUser.getId(), "DRIVE_FINALIZED", "DRIVE", driveId.toString(),
                "Finalized drive. Normal edits are now permanently locked.", httpRequest);

        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public DriveResponse getDriveById(Long driveId) {
        CompanyDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found with id: " + driveId));

        return toResponse(drive);
    }

    @Transactional(readOnly = true)
    public Page<DriveResponse> listDrives(DriveStatus status, LocalDate driveDate, Pageable pageable) {
        Page<CompanyDrive> drives;
        if (status != null && driveDate != null) {
            drives = driveRepository.findByStatusAndDriveDate(status, driveDate, pageable);
        } else if (status != null) {
            drives = driveRepository.findByStatus(status, pageable);
        } else if (driveDate != null) {
            drives = driveRepository.findByDriveDate(driveDate, pageable);
        } else {
            drives = driveRepository.findAll(pageable);
        }

        return drives.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DriveResponse> getMyEligibleDrives(Long studentUserId) {
        Student student = studentRepository.findByUserId(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + studentUserId));

        List<CompanyDrive> drives = driveRepository.findEligibleDrivesByStudentId(student.getId());
        return drives.stream().map(this::toResponse).toList();
    }

    private DriveResponse toResponse(CompanyDrive drive) {
        long eligibleCount = eligibilityRepository.countByDriveId(drive.getId());
        long presentCount = attendanceRecordRepository.countByDriveId(drive.getId());

        return DriveResponse.builder()
                .id(drive.getId())
                .companyName(drive.getCompanyName())
                .driveTitle(drive.getDriveTitle())
                .description(drive.getDescription())
                .driveDate(drive.getDriveDate())
                .venue(drive.getVenue())
                .status(drive.getStatus())
                .createdById(drive.getCreatedBy().getId())
                .createdByName(drive.getCreatedBy().getFullName())
                .eligibleCount(eligibleCount)
                .presentCount(presentCount)
                .openedAt(drive.getOpenedAt())
                .closedAt(drive.getClosedAt())
                .finalizedAt(drive.getFinalizedAt())
                .createdAt(drive.getCreatedAt())
                .build();
    }
}
