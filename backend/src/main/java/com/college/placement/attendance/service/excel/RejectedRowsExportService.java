package com.college.placement.attendance.service.excel;

import com.college.placement.attendance.entity.ImportError;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class RejectedRowsExportService {

    public static final String SHEET_NAME = "Rejected Students";
    public static final String[] HEADERS = {
            "Student ID",
            "Student Name",
            "Institutional Email",
            "Contact Number",
            "Error Reason"
    };

    public byte[] exportRejectedRows(List<ImportError> errors) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle textStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            textStyle.setDataFormat(format.getFormat("@")); // Plain text

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ImportError error : errors) {
                Row row = sheet.createRow(rowIdx++);

                createEscapedCell(row, 0, error.getStudentId(), textStyle);
                createEscapedCell(row, 1, error.getStudentName(), textStyle);
                createEscapedCell(row, 2, error.getEmail(), textStyle);
                createEscapedCell(row, 3, error.getContactNumber(), textStyle);
                createEscapedCell(row, 4, error.getErrorReason(), textStyle);
            }

            sheet.createFreezePane(0, 1);
            if (!errors.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, errors.size(), 0, HEADERS.length - 1));
            }

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 28 * 256);
            sheet.setColumnWidth(2, 32 * 256);
            sheet.setColumnWidth(3, 20 * 256);
            sheet.setColumnWidth(4, 50 * 256);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void createEscapedCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex, CellType.STRING);
        cell.setCellValue(ExcelSecurityValidator.escapeForExport(value));
        cell.setCellStyle(style);
    }
}
