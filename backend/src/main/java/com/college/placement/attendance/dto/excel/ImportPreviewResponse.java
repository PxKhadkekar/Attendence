package com.college.placement.attendance.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewResponse {
    private Long importJobId;
    private Long driveId;
    private String filename;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private List<RowErrorDto> errors;
}
