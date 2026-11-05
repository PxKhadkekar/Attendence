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
public class AttendanceVerifyRequest {

    @NotBlank(message = "Challenge token is required")
    private String challengeToken;

    @NotBlank(message = "Base64 ECDSA signature is required")
    private String signatureBase64;

    @NotNull(message = "Timestamp (epoch seconds) is required")
    private Long timestampEpochSec;
}
