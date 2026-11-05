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
public class BleStationDto {
    private Long id;
    private String stationCode;
    private String venueName;
    private String proximityUuid;
    private Integer majorId;
    private Integer minorId;
    private Boolean isActive;
}
