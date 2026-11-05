package com.college.placement.attendance.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveStudentRecordDto {
    private Long studentId;
    private String studentRollNumber;
    private String studentName;
    private String email;
    private String contactNumber;
    private String department;
    private String status; // PRESENT or ABSENT
    private Instant recordedAt;
    private String verificationStatus;
}
