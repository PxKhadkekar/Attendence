package com.college.placement.attendance.dto.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCorrectionRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotBlank(message = "New status is required (PRESENT or ABSENT)")
    private String newStatus;

    @NotBlank(message = "Correction reason is mandatory for audit accountability")
    private String reason;
}
