package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByDriveIdAndStudentId(Long driveId, Long studentId);
    boolean existsByDriveIdAndStudentId(Long driveId, Long studentId);
    long countByDriveId(Long driveId);
    List<AttendanceRecord> findByDriveId(Long driveId);
    Page<AttendanceRecord> findByDriveId(Long driveId, Pageable pageable);
    List<AttendanceRecord> findByStudentIdOrderByRecordedAtDesc(Long studentId);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.drive.id = :driveId AND (LOWER(r.student.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.student.studentId) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.student.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<AttendanceRecord> searchByDriveIdAndQuery(@Param("driveId") Long driveId, @Param("query") String query, Pageable pageable);
}
