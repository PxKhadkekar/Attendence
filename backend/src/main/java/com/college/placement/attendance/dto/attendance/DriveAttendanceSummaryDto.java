package com.college.placement.attendance.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveAttendanceSummaryDto {
    private Long driveId;
    private String companyName;
    private String driveTitle;
    private LocalDate driveDate;
    private String venue;
    private String status;
    private long totalEligible;
    private long presentCount;
    private long absentCount;
    private double attendancePercentage;
}
