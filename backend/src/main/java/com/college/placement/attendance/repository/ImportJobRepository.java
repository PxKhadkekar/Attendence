package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findByDriveIdOrderByCreatedAtDesc(Long driveId);
    Optional<ImportJob> findTopByDriveIdAndStatusOrderByCreatedAtDesc(Long driveId, String status);
}
