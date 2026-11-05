package com.college.placement.attendance;

import com.college.placement.attendance.dto.attendance.AttendanceCorrectionRequest;
import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.entity.CompanyDrive;
import com.college.placement.attendance.entity.DriveEligibility;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.repository.CompanyDriveRepository;
import com.college.placement.attendance.repository.DriveEligibilityRepository;
import com.college.placement.attendance.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAttendanceIntegrationTest {

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

        CreateDriveRequest driveReq = CreateDriveRequest.builder()
                .companyName("Amazon Web Services")
                .driveTitle("Cloud Support Associate Drive")
                .driveDate(LocalDate.now().plusDays(4))
                .venue("Auditorium East")
                .build();

        MvcResult driveRes = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driveReq)))
                .andExpect(status().isCreated())
                .andReturn();

        testDriveId = objectMapper.readValue(driveRes.getResponse().getContentAsString(), DriveResponse.class).getId();
        CompanyDrive drive = driveRepository.findById(testDriveId).orElseThrow();

        // Seed 2 eligible students: Student 1 & Student 2
        Student s1 = studentRepository.findById(1L).orElseThrow();
        Student s2 = studentRepository.findById(2L).orElseThrow();
        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s1).build());
        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s2).build());

        // Open drive
        mockMvc.perform(post("/api/v1/drives/" + testDriveId + "/schedule")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/drives/" + testDriveId + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Fetch live attendance summary and searchable records")
    void testSummaryAndSearchRecords() throws Exception {
        // Summary: 2 eligible, 0 present, 2 absent
        mockMvc.perform(get("/api/v1/drives/" + testDriveId + "/attendance/summary")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEligible").value(2))
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.absentCount").value(2))
                .andExpect(jsonPath("$.attendancePercentage").value(0.0));

        // Records: query for Student 1
        mockMvc.perform(get("/api/v1/drives/" + testDriveId + "/attendance/records?query=Aarav")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentRollNumber").value("STU2026001"))
                .andExpect(jsonPath("$[0].status").value("ABSENT"));
    }

    @Test
    @DisplayName("Audited manual override marks student present with reason")
    void testManualCorrectionOverride_Success() throws Exception {
        AttendanceCorrectionRequest correction = AttendanceCorrectionRequest.builder()
                .studentId(1L)
                .newStatus("PRESENT")
                .reason("Physical College ID verified at T&P Help Desk due to broken biometric sensor")
                .build();

        mockMvc.perform(post("/api/v1/drives/" + testDriveId + "/attendance/corrections")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correction)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("MANUAL_OVERRIDE"));

        // Summary updated: 1 present, 1 absent, 50% attendance
        mockMvc.perform(get("/api/v1/drives/" + testDriveId + "/attendance/summary")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.attendancePercentage").value(50.0));
    }
}
