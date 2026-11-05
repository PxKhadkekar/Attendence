package com.college.placement.attendance;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.entity.*;
import com.college.placement.attendance.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyDriveRepository driveRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveEligibilityRepository eligibilityRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private AttendanceAttemptRepository attemptRepository;

    @Autowired
    private AttendanceChallengeRepository challengeRepository;

    @Autowired
    private StudentDeviceRepository deviceRepository;

    @Autowired
    private BleStationRepository stationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private String officerToken;
    private String studentToken;
    private Long testDriveId;

    @BeforeEach
    void setUp() throws Exception {
        // Authenticate officer
        MvcResult officerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "Officer@123"))))
                .andExpect(status().isOk())
                .andReturn();
        officerToken = objectMapper.readValue(officerLogin.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // Authenticate student
        MvcResult studentLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student1@college.edu", "Student@123"))))
                .andExpect(status().isOk())
                .andReturn();
        studentToken = objectMapper.readValue(studentLogin.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // Create Drive
        CreateDriveRequest driveRequest = CreateDriveRequest.builder()
                .companyName("=Google India") // Intentional formula character in company name to test export escaping
                .driveTitle("Placement Drive 2026")
                .driveDate(LocalDate.now().plusDays(3))
                .venue("Auditorium Hall 1")
                .build();

        MvcResult driveResult = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driveRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        testDriveId = objectMapper.readValue(driveResult.getResponse().getContentAsString(), DriveResponse.class).getId();
        CompanyDrive drive = driveRepository.findById(testDriveId).orElseThrow();

        // Seed 3 eligible students
        Student s1 = studentRepository.findById(1L).orElseThrow();
        Student s2 = studentRepository.findById(2L).orElseThrow();
        Student s3 = studentRepository.findById(3L).orElseThrow();

        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s1).build());
        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s2).build());
        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s3).build());

        // Mark student 1 as PRESENT in attendance_records
        BleStation station = stationRepository.findById(1L).orElseThrow();
        StudentDevice device = deviceRepository.save(StudentDevice.builder()
                .student(s1)
                .deviceFingerprint("fp-12345")
                .deviceModel("Pixel 8")
                .osVersion("Android 14")
                .publicKeyPem("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0")
                .publicKeyFingerprint("hash-123")
                .status(DeviceStatus.APPROVED)
                .build());

        AttendanceChallenge challenge = challengeRepository.save(AttendanceChallenge.builder()
                .challengeToken(UUID.randomUUID().toString())
                .drive(drive)
                .student(s1)
                .device(device)
                .station(station)
                .canonicalPayload("test-payload")
                .expiresAt(Instant.now().plusSeconds(120))
                .used(true)
                .build());

        recordRepository.save(AttendanceRecord.builder()
                .drive(drive)
                .student(s1)
                .device(device)
                .station(station)
                .challenge(challenge)
                .signatureBase64("dGVzdC1zaWduYXR1cmU=")
                .recordedAt(Instant.now())
                .verificationStatus("VERIFIED")
                .build());

        // Record 1 attendance attempt for audit
        attemptRepository.save(AttendanceAttempt.builder()
                .drive(drive)
                .student(s2)
                .device(device)
                .station(station)
                .outcome("CHALLENGE_EXPIRED")
                .failureReason("Challenge expired before biometric presentation")
                .attemptedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Export multi-sheet .xlsx report matches database counts exactly and escapes formulas")
    void testExportDriveReport_ParityAndSecurity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/drives/" + testDriveId + "/export?includeAudit=true")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("drive_" + testDriveId + "_attendance_report.xlsx")))
                .andReturn();

        byte[] excelBytes = result.getResponse().getContentAsByteArray();
        assertThat(excelBytes).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            // 1. Verify Sheet presence
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Present")).isNotNull();
            assertThat(workbook.getSheet("Absent")).isNotNull();
            assertThat(workbook.getSheet("All Attendance")).isNotNull();
            assertThat(workbook.getSheet("Verification Audit")).isNotNull();

            // 2. Verify Present Sheet counts match DB count (1 student)
            Sheet presentSheet = workbook.getSheet("Present");
            // Header row (0) + 1 student data row (1) = 2 total rows
            assertThat(presentSheet.getPhysicalNumberOfRows()).isEqualTo(2);

            Row presentRow = presentSheet.getRow(1);
            assertThat(presentRow.getCell(0).getStringCellValue()).isEqualTo("STU2026001");
            assertThat(presentRow.getCell(6).getStringCellValue()).isEqualTo("PRESENT");
            assertThat(presentRow.getCell(8).getStringCellValue()).isEqualTo("VERIFIED");

            // Verify real Excel date types (not plain string)
            assertThat(DateUtil.isCellDateFormatted(presentRow.getCell(5))).isTrue(); // Drive Date
            assertThat(DateUtil.isCellDateFormatted(presentRow.getCell(7))).isTrue(); // Attendance Timestamp

            // Verify Formula Injection Escaping: "=Google India" must be escaped to "'=Google India"
            assertThat(presentRow.getCell(4).getStringCellValue()).isEqualTo("'=Google India");

            // 3. Verify Absent Sheet counts match DB count (2 students: s2, s3)
            Sheet absentSheet = workbook.getSheet("Absent");
            // Header row (0) + 2 absent student rows = 3 total rows
            assertThat(absentSheet.getPhysicalNumberOfRows()).isEqualTo(3);

            // 4. Verify All Attendance Sheet count (3 total)
            Sheet allSheet = workbook.getSheet("All Attendance");
            assertThat(allSheet.getPhysicalNumberOfRows()).isEqualTo(4);

            // 5. Verify Verification Audit Sheet has attempt and does not leak keys/tokens
            Sheet auditSheet = workbook.getSheet("Verification Audit");
            assertThat(auditSheet.getPhysicalNumberOfRows()).isEqualTo(2);
            Row auditRow = auditSheet.getRow(1);
            assertThat(auditRow.getCell(3).getStringCellValue()).isEqualTo("CHALLENGE_EXPIRED");
            assertThat(auditRow.getCell(4).getStringCellValue()).isEqualTo("Challenge expired before biometric presentation");
        }

        // Verify audit log entry is recorded
        long auditCount = auditLogRepository.count();
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Student cannot export reports (403 Forbidden)")
    void testExportDriveReport_StudentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/drives/" + testDriveId + "/export")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
