package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.AttendanceCorrection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {
    List<AttendanceCorrection> findByDriveIdOrderByCreatedAtDesc(Long driveId);
    Page<AttendanceCorrection> findByDriveId(Long driveId, Pageable pageable);
}
