package com.college.placement.attendance.dto.attendance;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSessionRequest {

    @NotNull(message = "Drive ID is required")
    private Long driveId;

    @NotNull(message = "Station ID is required")
    private Long stationId;

    @NotNull(message = "Device ID is required")
    private Long deviceId;
}
