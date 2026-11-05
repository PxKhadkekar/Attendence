package com.college.placement.attendance.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryResponse {
    private Long importJobId;
    private Long driveId;
    private int importedCount;
    private String status;
    private String message;
}
