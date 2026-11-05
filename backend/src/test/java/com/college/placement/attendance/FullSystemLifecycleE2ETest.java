package com.college.placement.attendance;

import com.college.placement.attendance.dto.attendance.AttendanceSessionRequest;
import com.college.placement.attendance.dto.attendance.AttendanceSessionResponse;
import com.college.placement.attendance.dto.attendance.AttendanceVerifyRequest;
import com.college.placement.attendance.dto.attendance.AttendanceVerifyResponse;
import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.device.DeviceResponse;
import com.college.placement.attendance.dto.device.RegisterDeviceRequest;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.dto.excel.ImportPreviewResponse;
import com.college.placement.attendance.dto.excel.ImportSummaryResponse;
import com.college.placement.attendance.dto.station.BleStationDto;
import com.college.placement.attendance.dto.station.CreateBleStationRequest;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.repository.StudentRepository;
import com.college.placement.attendance.service.crypto.CryptoVerificationService;
import com.college.placement.attendance.service.excel.ExcelTemplateService;
import com.college.placement.attendance.testutil.CryptoTestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullSystemLifecycleE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CryptoVerificationService cryptoService;

    @Test
    @DisplayName("Complete Zero-Trust Placement Drive Lifecycle E2E Test")
    void testCompleteDriveAndAttendanceLifecycle() throws Exception {
        // =========================================================================
        // STEP 1: Officer Authentication
        // =========================================================================
        MvcResult officerLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "Officer@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_OFFICER"))
                .andReturn();

        LoginResponse officerAuth = objectMapper.readValue(
                officerLoginRes.getResponse().getContentAsString(), LoginResponse.class);
        String officerToken = officerAuth.getAccessToken();

        // =========================================================================
        // STEP 2: Create Recruitment Drive (DRAFT)
        // =========================================================================
        CreateDriveRequest createDriveReq = CreateDriveRequest.builder()
                .driveTitle("Google Placement Drive 2026")
                .companyName("Google India")
                .description("Software Engineer - Systems")
                .venue("Auditorium Main Stage")
                .driveDate(LocalDate.now().plusDays(2))
                .build();

        MvcResult createDriveRes = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDriveReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        DriveResponse drive = objectMapper.readValue(
                createDriveRes.getResponse().getContentAsString(), DriveResponse.class);
        Long driveId = drive.getId();

        // =========================================================================
        // STEP 3: Setup BLE Station Beacon in Placement Hall
        // =========================================================================
        CreateBleStationRequest stationReq = CreateBleStationRequest.builder()
                .stationCode("STATION-E2E-01")
                .venueName("Auditorium Main Stage")
                .proximityUuid("0000FD5A-0000-1000-8000-00805F9B34FB")
                .majorId(201)
                .minorId(1)
                .build();

        MvcResult stationRes = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stationReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationCode").value("STATION-E2E-01"))
                .andReturn();

        BleStationDto station = objectMapper.readValue(
                stationRes.getResponse().getContentAsString(), BleStationDto.class);
        Long stationId = station.getId();

        // =========================================================================
        // STEP 4: Excel Student Roster Import (with Anti-Formula Injection Check)
        // =========================================================================
        // Student 1 (Aarav - will attend) and Student 2 (Diya - will be absent)
        Student s1 = studentRepository.findByStudentId("STU2026001").orElseThrow();
        Student s2 = studentRepository.findByStudentId("STU2026002").orElseThrow();

        ByteArrayOutputStream excelOut = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(ExcelTemplateService.SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < ExcelTemplateService.HEADERS.length; i++) {
                header.createCell(i).setCellValue(ExcelTemplateService.HEADERS[i]);
            }

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(s1.getStudentId());
            row1.createCell(1).setCellValue(s1.getFullName());
            row1.createCell(2).setCellValue(s1.getEmail());
            row1.createCell(3).setCellValue(s1.getContactNumber());

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(s2.getStudentId());
            // Formula injection string to ensure server-side escaping
            row2.createCell(1).setCellValue("=cmd|' /C calc'!A0 " + s2.getFullName());
            row2.createCell(2).setCellValue(s2.getEmail());
            row2.createCell(3).setCellValue(s2.getContactNumber());

            wb.write(excelOut);
        }

        MockMultipartFile excelFile = new MockMultipartFile(
                "file", "eligibility_google.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelOut.toByteArray()
        );

        // Upload and Preview
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/drives/" + driveId + "/imports/upload")
                        .file(excelFile)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andReturn();

        ImportPreviewResponse preview = objectMapper.readValue(
                uploadRes.getResponse().getContentAsString(), ImportPreviewResponse.class);

        // Confirm Import into Drive Eligibility
        MvcResult confirmRes = mockMvc.perform(post("/api/v1/drives/" + driveId + "/imports/" + preview.getImportJobId() + "/confirm")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andReturn();

        ImportSummaryResponse importSummary = objectMapper.readValue(
                confirmRes.getResponse().getContentAsString(), ImportSummaryResponse.class);
        assertThat(importSummary.getImportedCount()).isEqualTo(2);

        // =========================================================================
        // STEP 5: Drive Lifecycle Advancement (DRAFT -> SCHEDULED -> OPEN)
        // =========================================================================
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/schedule")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(post("/api/v1/drives/" + driveId + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // =========================================================================
        // STEP 6: Student Authenticates & Registers Android Hardware Key
        // =========================================================================
        MvcResult studentLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student1@college.edu", "Student@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_STUDENT"))
                .andReturn();

        LoginResponse studentAuth = objectMapper.readValue(
                studentLoginRes.getResponse().getContentAsString(), LoginResponse.class);
        String studentToken = studentAuth.getAccessToken();

        // Generate ECDSA NIST P-256 Keypair (representing Android Keystore)
        KeyPair studentKeyPair = CryptoTestHelper.generateP256KeyPair();
        String studentPublicKeyPem = CryptoTestHelper.toPem(studentKeyPair.getPublic());

        RegisterDeviceRequest registerDeviceReq = RegisterDeviceRequest.builder()
                .deviceFingerprint("pixel-e2e-hash-9900")
                .deviceModel("Google Pixel 8 Pro")
                .osVersion("Android 14 (API 34)")
                .publicKeyPem(studentPublicKeyPem)
                .build();

        MvcResult registerDeviceRes = mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDeviceReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        DeviceResponse device = objectMapper.readValue(
                registerDeviceRes.getResponse().getContentAsString(), DeviceResponse.class);
        Long deviceId = device.getId();

        // Officer approves the hardware device
        mockMvc.perform(post("/api/v1/devices/" + deviceId + "/approve")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // =========================================================================
        // STEP 7: Student Requests Single-Use Challenge Session
        // =========================================================================
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(driveId)
                .stationId(stationId)
                .deviceId(deviceId)
                .build();

        MvcResult sessionRes = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeToken").isNotEmpty())
                .andReturn();

        AttendanceSessionResponse session = objectMapper.readValue(
                sessionRes.getResponse().getContentAsString(), AttendanceSessionResponse.class);

        // =========================================================================
        // STEP 8: Cryptographic Signature Generation & Attendance Verification
        // =========================================================================
        long timestamp = Instant.now().getEpochSecond();
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(),
                s1.getId(),
                driveId,
                deviceId,
                stationId,
                timestamp
        );
        String signatureBase64 = CryptoTestHelper.signCanonicalMessage(studentKeyPair.getPrivate(), canonicalMessage);

        AttendanceVerifyRequest verifyReq = AttendanceVerifyRequest.builder()
                .challengeToken(session.getChallengeToken())
                .signatureBase64(signatureBase64)
                .timestampEpochSec(timestamp)
                .build();

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.studentId").value(s1.getId()))
                .andReturn();

        AttendanceVerifyResponse verifyResponse = objectMapper.readValue(
                verifyRes.getResponse().getContentAsString(), AttendanceVerifyResponse.class);
        assertThat(verifyResponse.getVerificationStatus()).isEqualTo("VERIFIED");

        // =========================================================================
        // STEP 9: Replay Attack Defense Verification
        // =========================================================================
        // Attempting to re-submit the same challenge must be rejected with 409 CONFLICT
        mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // =========================================================================
        // STEP 10: Close Recruitment Drive
        // =========================================================================
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/close")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // =========================================================================
        // STEP 11: Export Multi-Sheet SXSSFWorkbook Report & Verify Strict Parity
        // =========================================================================
        MvcResult reportRes = mockMvc.perform(get("/api/v1/reports/drives/" + driveId + "/export?includeAudit=true")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] reportBytes = reportRes.getResponse().getContentAsByteArray();
        assertThat(reportBytes).isNotNull();
        assertThat(reportBytes.length).isGreaterThan(1000);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(reportBytes))) {
            // Verify Sheet structure
            assertThat(wb.getNumberOfSheets()).isEqualTo(5);
            assertThat(wb.getSheet("Summary")).isNotNull();
            assertThat(wb.getSheet("Present")).isNotNull();
            assertThat(wb.getSheet("Absent")).isNotNull();
            assertThat(wb.getSheet("All Attendance")).isNotNull();
            assertThat(wb.getSheet("Verification Audit")).isNotNull();

            // Verify Row Count Parity
            Sheet presentSheet = wb.getSheet("Present");
            Sheet absentSheet = wb.getSheet("Absent");
            Sheet allSheet = wb.getSheet("All Attendance");

            // Header row (0) + 1 student data row = 2 total physical rows
            assertThat(presentSheet.getPhysicalNumberOfRows()).isEqualTo(2);
            // Header row (0) + 1 absent student data row = 2 total physical rows
            assertThat(absentSheet.getPhysicalNumberOfRows()).isEqualTo(2);
            // Header row (0) + 2 students = 3 total physical rows
            assertThat(allSheet.getPhysicalNumberOfRows()).isEqualTo(3);

            // Verify Present Student Identity
            assertThat(presentSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(s1.getStudentId());

            // Verify Absent Student Identity
            assertThat(absentSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(s2.getStudentId());
        }
    }
}
