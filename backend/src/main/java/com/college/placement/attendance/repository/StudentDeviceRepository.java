package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.DeviceStatus;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.entity.StudentDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDeviceRepository extends JpaRepository<StudentDevice, Long> {
    Optional<StudentDevice> findByStudentAndStatus(Student student, DeviceStatus status);
    Optional<StudentDevice> findByStudentIdAndStatus(Long studentId, DeviceStatus status);
    List<StudentDevice> findByStatus(DeviceStatus status);
    List<StudentDevice> findByStudentId(Long studentId);
    Optional<StudentDevice> findByPublicKeyFingerprint(String fingerprint);
}
