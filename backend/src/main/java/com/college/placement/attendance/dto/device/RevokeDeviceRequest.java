package com.college.placement.attendance.dto.device;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeDeviceRequest {
    @NotBlank(message = "Revocation reason is mandatory for audit accountability")
    private String reason;
}
