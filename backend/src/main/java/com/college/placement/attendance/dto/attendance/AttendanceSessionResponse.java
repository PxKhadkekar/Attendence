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
public class AttendanceSessionResponse {
    private String challengeToken;
    private Instant expiresAt;
    private long validitySeconds;
    private Long driveId;
    private Long stationId;
    private Long deviceId;
    private String canonicalFormat;
}
