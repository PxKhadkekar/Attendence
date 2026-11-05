package com.college.placement.attendance.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceHistoryDto {
    private Long recordId;
    private Long driveId;
    private String companyName;
    private String driveTitle;
    private LocalDate driveDate;
    private String venue;
    private Instant recordedAt;
    private String verificationStatus;
}
