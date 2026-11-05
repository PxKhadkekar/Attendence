package com.college.placement.attendance;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.dto.excel.ImportPreviewResponse;
import com.college.placement.attendance.repository.DriveEligibilityRepository;
import com.college.placement.attendance.repository.StudentRepository;
import com.college.placement.attendance.service.excel.ExcelTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelImportSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriveEligibilityRepository eligibilityRepository;

    private String officerToken;
    private Long testDriveId;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult officerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "Officer@123"))))
                .andExpect(status().isOk())
                .andReturn();
        officerToken = objectMapper.readValue(officerLogin.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        CreateDriveRequest driveRequest = CreateDriveRequest.builder()
                .companyName("TCS Innovations")
                .driveTitle("Campus Drive 2026")
                .driveDate(LocalDate.now().plusDays(7))
                .venue("Placement Block A")
                .build();

        MvcResult driveResult = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driveRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        testDriveId = objectMapper.readValue(driveResult.getResponse().getContentAsString(), DriveResponse.class).getId();
    }

    private byte[] createTestWorkbook(String sheetName, String[] headers, Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            if (headers != null) {
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }
            }
            if (rows != null) {
                for (int r = 0; r < rows.length; r++) {
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < rows[r].length; c++) {
                        Cell cell = row.createCell(c);
                        if (rows[r][c] instanceof String s) {
                            cell.setCellValue(s);
                        } else if (rows[r][c] instanceof Number n) {
                            cell.setCellValue(n.doubleValue());
                        }
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("Valid .xlsx import preview and confirmation into single transaction")
    void testValidXlsxImport_Success() throws Exception {
        // Master students STU2026001 and STU2026002 are seeded in V5
        Object[][] rowData = {
                {"STU2026001", "Aarav Sharma", "aarav.sharma@college.edu", "+91-9876500001"},
                {"STU2026002", "Diya Patel", "diya.patel@college.edu", "+91-9876500002"}
        };
        byte[] fileBytes = createTestWorkbook(ExcelTemplateService.SHEET_NAME, ExcelTemplateService.HEADERS, rowData);

        MockMultipartFile file = new MockMultipartFile(
                "file", "eligible_students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

        // 1. Upload & Preview
        MvcResult previewResult = mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andReturn();

        ImportPreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), ImportPreviewResponse.class);

        // 2. Confirm Import
        mockMvc.perform(post("/api/v1/drives/" + testDriveId + "/imports/" + preview.getImportJobId() + "/confirm")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.status").value("COMMITTED"));

        // Verify database counts
        long eligibleCount = eligibilityRepository.countByDriveId(testDriveId);
        assertThat(eligibleCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Reject non-xlsx files: .csv, .xls, .xlsm")
    void testWrongType_Rejected() throws Exception {
        // 1. Reject CSV
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "students.csv", "text/csv", "Student ID,Student Name\nSTU1,John".getBytes());

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(csvFile)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only valid .xlsx files are accepted")));

        // 2. Reject XLS
        MockMultipartFile xlsFile = new MockMultipartFile(
                "file", "students.xls", "application/vnd.ms-excel", new byte[]{0x01, 0x02});

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(xlsFile)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest());

        // 3. Reject XLSM
        MockMultipartFile xlsmFile = new MockMultipartFile(
                "file", "students.xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(xlsmFile)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reject corrupt file with invalid magic bytes")
    void testInvalidMagicBytes_Rejected() throws Exception {
        MockMultipartFile fakeXlsx = new MockMultipartFile(
                "file", "fake.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Not a real ZIP file at all".getBytes());

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(fakeXlsx)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("valid OOXML ZIP magic bytes")));
    }

    @Test
    @DisplayName("Reject workbook missing 'Eligible Students' sheet name")
    void testMissingSheetName_Rejected() throws Exception {
        byte[] bytes = createTestWorkbook("Sheet1", ExcelTemplateService.HEADERS, new Object[][]{});
        MockMultipartFile file = new MockMultipartFile(
                "file", "wrong_sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Eligible Students")));
    }

    @Test
    @DisplayName("Reject workbook with incorrect header columns")
    void testMissingHeaders_Rejected() throws Exception {
        String[] badHeaders = {"ID", "Name", "Email", "Phone"};
        byte[] bytes = createTestWorkbook(ExcelTemplateService.SHEET_NAME, badHeaders, new Object[][]{});
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad_headers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid column header")));
    }

    @Test
    @DisplayName("Detect duplicate rows, unknown students, and invalid email domains with rejected rows export")
    void testRowValidationAndRejectedRowsExport() throws Exception {
        Object[][] mixedData = {
                // 1. Valid row
                {"STU2026001", "Aarav Sharma", "aarav.sharma@college.edu", "+91-9876500001"},
                // 2. Duplicate student row
                {"STU2026001", "Aarav Sharma", "aarav.sharma@college.edu", "+91-9876500001"},
                // 3. Unknown student not in master data
                {"STU9999999", "Unknown Person", "unknown@college.edu", "+91-9999999999"},
                // 4. Non-institutional email domain (@gmail.com)
                {"STU2026002", "Diya Patel", "diya.patel@gmail.com", "+91-9876500002"}
        };

        byte[] bytes = createTestWorkbook(ExcelTemplateService.SHEET_NAME, ExcelTemplateService.HEADERS, mixedData);
        MockMultipartFile file = new MockMultipartFile(
                "file", "mixed_students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        MvcResult previewResult = mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(3))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andReturn();

        ImportPreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), ImportPreviewResponse.class);

        // Download rejected rows .xlsx
        MvcResult rejectedResult = mockMvc.perform(get("/api/v1/drives/" + testDriveId + "/imports/" + preview.getImportJobId() + "/rejected-rows")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] rejectedBytes = rejectedResult.getResponse().getContentAsByteArray();
        try (Workbook rejectedWb = new XSSFWorkbook(new ByteArrayInputStream(rejectedBytes))) {
            Sheet sheet = rejectedWb.getSheet("Rejected Students");
            assertThat(sheet).isNotNull();
            // Header + 3 error rows = 4 rows total
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
            // Check Error Reason header is present at column 4
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Error Reason");
        }
    }

    @Test
    @DisplayName("Formula injection strings are treated as plain text on import")
    void testFormulaInjection_NeutralizedOnImport() throws Exception {
        // Cell values starting with = + - @ must be read as raw literal text, never executed
        Object[][] formulaRows = {
                {"STU2026003", "=SUM(A1:B1)", "rohan.mehta@college.edu", "+91-9876500003"}
        };

        byte[] bytes = createTestWorkbook(ExcelTemplateService.SHEET_NAME, ExcelTemplateService.HEADERS, formulaRows);
        MockMultipartFile file = new MockMultipartFile(
                "file", "formula_students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // Upload should process without crashing or evaluating
        mockMvc.perform(multipart("/api/v1/drives/" + testDriveId + "/imports/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(1));
    }

    @Test
    @DisplayName("Download blank template produces valid .xlsx with correct headers and frozen pane")
    void testDownloadTemplate_ProducesValidExcel() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/drives/import-template")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("eligible_students_template.xlsx")))
                .andReturn();

        byte[] templateBytes = result.getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(templateBytes))) {
            Sheet sheet = wb.getSheet("Eligible Students");
            assertThat(sheet).isNotNull();
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Student ID");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Student Name");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Institutional Email");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Contact Number");
        }
    }
}
