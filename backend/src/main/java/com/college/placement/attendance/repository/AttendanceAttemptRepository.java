package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.AttendanceAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceAttemptRepository extends JpaRepository<AttendanceAttempt, Long> {
    List<AttendanceAttempt> findByDriveIdOrderByAttemptedAtDesc(Long driveId);
    Page<AttendanceAttempt> findByDriveId(Long driveId, Pageable pageable);
}
