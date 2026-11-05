package com.college.placement.attendance.dto.device;

import com.college.placement.attendance.entity.DeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private Long studentId;
    private String studentRollNumber;
    private String studentName;
    private String deviceFingerprint;
    private String deviceModel;
    private String osVersion;
    private String publicKeyFingerprint;
    private DeviceStatus status;
    private Instant approvedAt;
    private Instant revokedAt;
    private String revocationReason;
    private Instant createdAt;
}
