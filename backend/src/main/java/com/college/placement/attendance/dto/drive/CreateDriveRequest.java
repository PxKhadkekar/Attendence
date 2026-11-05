package com.college.placement.attendance.dto.drive;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDriveRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 128, message = "Company name cannot exceed 128 characters")
    private String companyName;

    @NotBlank(message = "Drive title is required")
    @Size(max = 255, message = "Drive title cannot exceed 255 characters")
    private String driveTitle;

    private String description;

    @NotNull(message = "Drive date is required")
    @FutureOrPresent(message = "Drive date must be today or in the future")
    private LocalDate driveDate;

    @NotBlank(message = "Venue is required")
    @Size(max = 128, message = "Venue cannot exceed 128 characters")
    private String venue;
}
