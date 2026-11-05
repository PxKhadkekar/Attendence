package com.college.placement.attendance;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.dto.drive.UpdateDriveRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriveLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyDriveRepository driveRepository;

    @Autowired
    private DriveEligibilityRepository eligibilityRepository;

    @Autowired
    private StudentRepository studentRepository;

    private String officerToken;
    private String studentToken;

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
    }

    @Test
    @DisplayName("Officer creates drive in DRAFT state")
    void createDrive_AsOfficer_Success() throws Exception {
        CreateDriveRequest request = CreateDriveRequest.builder()
                .companyName("Acme Corp")
                .driveTitle("Software Development Engineer Drive 2026")
                .description("On-campus technical assessment and interview")
                .driveDate(LocalDate.now().plusDays(5))
                .venue("Auditorium Hall A")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andReturn();

        DriveResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), DriveResponse.class);
        assertThat(response.getId()).isNotNull();
    }

    @Test
    @DisplayName("Student cannot create drive (403 Forbidden)")
    void createDrive_AsStudent_Forbidden() throws Exception {
        CreateDriveRequest request = CreateDriveRequest.builder()
                .companyName("Acme Corp")
                .driveTitle("Software Development Engineer Drive 2026")
                .driveDate(LocalDate.now().plusDays(5))
                .venue("Auditorium Hall A")
                .build();

        mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Scheduling drive without eligible students returns 400 Bad Request")
    void scheduleDrive_WithoutEligibleStudents_Fails() throws Exception {
        // Create draft drive
        CreateDriveRequest request = CreateDriveRequest.builder()
                .companyName("Beta Systems")
                .driveTitle("Cloud Architect Drive")
                .driveDate(LocalDate.now().plusDays(2))
                .venue("Seminar Hall 1")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        DriveResponse drive = objectMapper.readValue(result.getResponse().getContentAsString(), DriveResponse.class);

        // Try to schedule without eligible list
        mockMvc.perform(post("/api/v1/drives/" + drive.getId() + "/schedule")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("without eligible students")));
    }

    @Test
    @DisplayName("Full valid lifecycle transition: DRAFT -> SCHEDULED -> OPEN -> CLOSED -> FINALIZED")
    void fullDriveLifecycle_Success() throws Exception {
        // 1. Create drive
        CreateDriveRequest request = CreateDriveRequest.builder()
                .companyName("Gamma Cloud")
                .driveTitle("Full Stack Engineer Drive")
                .driveDate(LocalDate.now().plusDays(1))
                .venue("Main Lab")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        DriveResponse drive = objectMapper.readValue(result.getResponse().getContentAsString(), DriveResponse.class);
        Long driveId = drive.getId();

        // Add 1 eligible student directly in DB to simulate post-import state
        Student student = studentRepository.findById(1L).orElseThrow();
        eligibilityRepository.save(DriveEligibility.builder()
                .drive(driveRepository.findById(driveId).orElseThrow())
                .student(student)
                .build());

        // 2. Transition DRAFT -> SCHEDULED
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/schedule")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // 3. Transition SCHEDULED -> OPEN
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty());

        // 4. Transition OPEN -> CLOSED
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/close")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        // 5. Transition CLOSED -> FINALIZED
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/finalize")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedAt").isNotEmpty());

        // 6. Attempting illegal transition from FINALIZED -> OPEN must be rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/drives/" + driveId + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 7. Attempting edit on FINALIZED drive must be rejected with 409 Conflict
        UpdateDriveRequest updateRequest = UpdateDriveRequest.builder()
                .companyName("Gamma Cloud Modified")
                .driveTitle("New Title")
                .driveDate(LocalDate.now().plusDays(10))
                .venue("New Venue")
                .build();

        mockMvc.perform(put("/api/v1/drives/" + driveId)
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("Illegal transition from DRAFT directly to OPEN should be rejected (409 Conflict)")
    void invalidTransition_DraftToOpen_Rejected() throws Exception {
        CreateDriveRequest request = CreateDriveRequest.builder()
                .companyName("Delta Corp")
                .driveTitle("Data Scientist Drive")
                .driveDate(LocalDate.now().plusDays(3))
                .venue("Conference Room 2")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        DriveResponse drive = objectMapper.readValue(result.getResponse().getContentAsString(), DriveResponse.class);

        mockMvc.perform(post("/api/v1/drives/" + drive.getId() + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }
}
