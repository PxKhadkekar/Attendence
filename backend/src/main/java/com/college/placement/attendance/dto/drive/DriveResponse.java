package com.college.placement.attendance.dto.drive;

import com.college.placement.attendance.entity.DriveStatus;
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
public class DriveResponse {
    private Long id;
    private String companyName;
    private String driveTitle;
    private String description;
    private LocalDate driveDate;
    private String venue;
    private DriveStatus status;
    private Long createdById;
    private String createdByName;
    private long eligibleCount;
    private long presentCount;
    private Instant openedAt;
    private Instant closedAt;
    private Instant finalizedAt;
    private Instant createdAt;
}
