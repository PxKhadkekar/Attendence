package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.CompanyDrive;
import com.college.placement.attendance.entity.DriveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompanyDriveRepository extends JpaRepository<CompanyDrive, Long> {
    Page<CompanyDrive> findByStatus(DriveStatus status, Pageable pageable);
    Page<CompanyDrive> findByDriveDate(LocalDate driveDate, Pageable pageable);
    Page<CompanyDrive> findByStatusAndDriveDate(DriveStatus status, LocalDate driveDate, Pageable pageable);

    @Query("SELECT d FROM CompanyDrive d JOIN DriveEligibility e ON e.drive = d WHERE e.student.id = :studentId ORDER BY d.driveDate DESC")
    List<CompanyDrive> findEligibleDrivesByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT d FROM CompanyDrive d JOIN DriveEligibility e ON e.drive = d WHERE e.student.id = :studentId AND d.status IN ('SCHEDULED', 'OPEN') ORDER BY d.driveDate ASC")
    List<CompanyDrive> findActiveEligibleDrivesByStudentId(@Param("studentId") Long studentId);
}
