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
public class AttendanceVerifyResponse {
    private Long attendanceRecordId;
    private Long driveId;
    private String companyName;
    private String driveTitle;
    private Long studentId;
    private String studentName;
    private Instant recordedAt;
    private String verificationStatus;
    private String message;
}
