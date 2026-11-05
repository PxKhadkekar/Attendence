package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentId(String studentId);
    Optional<Student> findByEmail(String email);
    Optional<Student> findByUser(User user);
    Optional<Student> findByUserId(Long userId);
    boolean existsByStudentId(String studentId);
    boolean existsByEmail(String email);
}
