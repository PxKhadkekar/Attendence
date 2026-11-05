package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {
    List<ImportError> findByImportJobIdOrderByRowNumberAsc(Long importJobId);
}
