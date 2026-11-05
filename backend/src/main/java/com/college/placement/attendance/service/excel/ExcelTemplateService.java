package com.college.placement.attendance.service.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ExcelTemplateService {

    public static final String SHEET_NAME = "Eligible Students";
    public static final String[] HEADERS = {
            "Student ID",
            "Student Name",
            "Institutional Email",
            "Contact Number"
    };

    public byte[] generateBlankTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            // Styling for header row
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Create Header Row (Row 0)
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);

            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Freeze top row
            sheet.createFreezePane(0, 1);

            // Set auto-filter across headers
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // Set column widths
            sheet.setColumnWidth(0, 20 * 256); // Student ID
            sheet.setColumnWidth(1, 30 * 256); // Student Name
            sheet.setColumnWidth(2, 35 * 256); // Institutional Email
            sheet.setColumnWidth(3, 20 * 256); // Contact Number

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
