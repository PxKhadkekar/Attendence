package com.college.placement.attendance.dto.station;

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
public class CreateBleStationRequest {
    @NotBlank(message = "Station code is required")
    private String stationCode;

    @NotBlank(message = "Venue name is required")
    private String venueName;

    @NotBlank(message = "Proximity UUID is required")
    private String proximityUuid;

    @NotNull(message = "Major ID is required")
    private Integer majorId;

    @NotNull(message = "Minor ID is required")
    private Integer minorId;
}
