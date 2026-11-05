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
public class RegisterDeviceRequest {

    @NotBlank(message = "Device fingerprint is required")
    private String deviceFingerprint;

    @NotBlank(message = "Device model is required")
    private String deviceModel;

    @NotBlank(message = "OS version is required")
    private String osVersion;

    @NotBlank(message = "Public key PEM is required")
    private String publicKeyPem;
}
