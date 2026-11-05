package com.college.placement.attendance.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowErrorDto {
    private int rowNumber;
    private String studentId;
    private String studentName;
    private String email;
    private String contactNumber;
    private String errorReason;
}
