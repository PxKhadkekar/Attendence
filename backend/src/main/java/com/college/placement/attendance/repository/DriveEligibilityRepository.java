package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.CompanyDrive;
import com.college.placement.attendance.entity.DriveEligibility;
import com.college.placement.attendance.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriveEligibilityRepository extends JpaRepository<DriveEligibility, Long> {
    Optional<DriveEligibility> findByDriveAndStudent(CompanyDrive drive, Student student);
    Optional<DriveEligibility> findByDriveIdAndStudentId(Long driveId, Long studentId);
    boolean existsByDriveIdAndStudentId(Long driveId, Long studentId);
    List<DriveEligibility> findByDriveId(Long driveId);
    long countByDriveId(Long driveId);
}
