package com.college.placement.attendance;

import com.college.placement.attendance.dto.attendance.*;
import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.device.DeviceResponse;
import com.college.placement.attendance.dto.device.RegisterDeviceRequest;
import com.college.placement.attendance.dto.device.RevokeDeviceRequest;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.college.placement.attendance.dto.drive.DriveResponse;
import com.college.placement.attendance.entity.CompanyDrive;
import com.college.placement.attendance.entity.DriveEligibility;
import com.college.placement.attendance.entity.Student;
import com.college.placement.attendance.repository.*;
import com.college.placement.attendance.service.crypto.CryptoVerificationService;
import com.college.placement.attendance.testutil.CryptoTestHelper;
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

import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceVerificationIntegrationTest {

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
    private AttendanceChallengeRepository challengeRepository;

    @Autowired
    private CryptoVerificationService cryptoService;

    private String officerToken;
    private String student1Token;
    private String student2Token;

    private KeyPair student1KeyPair;
    private Long student1DeviceId;
    private Long openDriveId;
    private Long stationId = 1L; // Seeded in V5

    @BeforeEach
    void setUp() throws Exception {
        // 1. Authenticate Officer
        MvcResult officerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "Officer@123"))))
                .andExpect(status().isOk())
                .andReturn();
        officerToken = objectMapper.readValue(officerLogin.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // 2. Authenticate Student 1 & Student 2
        MvcResult s1Login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student1@college.edu", "Student@123"))))
                .andExpect(status().isOk())
                .andReturn();
        student1Token = objectMapper.readValue(s1Login.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        MvcResult s2Login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student2@college.edu", "Student@123"))))
                .andExpect(status().isOk())
                .andReturn();
        student2Token = objectMapper.readValue(s2Login.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // 3. Register Student 1 Device with real P-256 Keypair
        student1KeyPair = CryptoTestHelper.generateP256KeyPair();
        String student1Pem = CryptoTestHelper.toPem(student1KeyPair.getPublic());

        RegisterDeviceRequest devReq = RegisterDeviceRequest.builder()
                .deviceFingerprint("pixel-fp-8899")
                .deviceModel("Google Pixel 8 Pro")
                .osVersion("Android 14")
                .publicKeyPem(student1Pem)
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isCreated())
                .andReturn();

        student1DeviceId = objectMapper.readValue(regResult.getResponse().getContentAsString(), DeviceResponse.class).getId();

        // 4. Officer approves Student 1 Device
        mockMvc.perform(post("/api/v1/devices/" + student1DeviceId + "/approve")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());

        // 5. Create, Schedule, and Open a Recruitment Drive
        CreateDriveRequest driveReq = CreateDriveRequest.builder()
                .companyName("Microsoft IDC")
                .driveTitle("Software Development Engineer Campus Drive")
                .driveDate(LocalDate.now().plusDays(2))
                .venue("Auditorium Main Stage")
                .build();

        MvcResult driveRes = mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driveReq)))
                .andExpect(status().isCreated())
                .andReturn();

        openDriveId = objectMapper.readValue(driveRes.getResponse().getContentAsString(), DriveResponse.class).getId();
        CompanyDrive drive = driveRepository.findById(openDriveId).orElseThrow();

        // Add Student 1 to drive eligibility list
        Student s1 = studentRepository.findById(1L).orElseThrow();
        eligibilityRepository.save(DriveEligibility.builder().drive(drive).student(s1).build());

        // Transition: DRAFT -> SCHEDULED -> OPEN
        mockMvc.perform(post("/api/v1/drives/" + openDriveId + "/schedule")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/drives/" + openDriveId + "/open")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valid ECDSA signature check-in records attendance and marks verification status VERIFIED")
    void testValidCheckIn_Success() throws Exception {
        // Step 1: Request Attendance Session Challenge
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        MvcResult sessionRes = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeToken").isNotEmpty())
                .andReturn();

        AttendanceSessionResponse session = objectMapper.readValue(
                sessionRes.getResponse().getContentAsString(), AttendanceSessionResponse.class);

        // Step 2: Sign canonical payload with student's private key
        long timestamp = Instant.now().getEpochSecond();
        Student s1 = studentRepository.findById(1L).orElseThrow();

        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(),
                s1.getId(),
                openDriveId,
                student1DeviceId,
                stationId,
                timestamp
        );

        String signatureBase64 = CryptoTestHelper.signCanonicalMessage(student1KeyPair.getPrivate(), canonicalMessage);

        // Step 3: Submit signature to verify endpoint
        AttendanceVerifyRequest verifyReq = AttendanceVerifyRequest.builder()
                .challengeToken(session.getChallengeToken())
                .signatureBase64(signatureBase64)
                .timestampEpochSec(timestamp)
                .build();

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.studentId").value(s1.getId()))
                .andReturn();

        AttendanceVerifyResponse verifyResponse = objectMapper.readValue(
                verifyRes.getResponse().getContentAsString(), AttendanceVerifyResponse.class);
        assertThat(verifyResponse.getVerificationStatus()).isEqualTo("VERIFIED");

        // Step 4: Verify Database state
        assertThat(recordRepository.existsByDriveIdAndStudentId(openDriveId, s1.getId())).isTrue();
        assertThat(challengeRepository.findByChallengeToken(session.getChallengeToken()).orElseThrow().getUsed()).isTrue();

        // Step 5: Check Student History Endpoint
        mockMvc.perform(get("/api/v1/attendance/history")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Microsoft IDC"))
                .andExpect(jsonPath("$[0].verificationStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("Ineligible student session request is rejected")
    void testIneligibleStudent_Rejected() throws Exception {
        // Student 2 has not been added to eligibility for openDriveId
        // Register device for Student 2 first
        KeyPair s2KeyPair = CryptoTestHelper.generateP256KeyPair();
        RegisterDeviceRequest devReq = RegisterDeviceRequest.builder()
                .deviceFingerprint("pixel-fp-s2")
                .deviceModel("Samsung Galaxy S24")
                .osVersion("Android 14")
                .publicKeyPem(CryptoTestHelper.toPem(s2KeyPair.getPublic()))
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + student2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long s2DevId = objectMapper.readValue(regResult.getResponse().getContentAsString(), DeviceResponse.class).getId();

        // Officer approves Student 2 device
        mockMvc.perform(post("/api/v1/devices/" + s2DevId + "/approve")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());

        // Student 2 attempts session on drive they are ineligible for
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(s2DevId)
                .build();

        mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not on the eligible students list")));
    }

    @Test
    @DisplayName("Expired challenge submission is rejected")
    void testExpiredChallenge_Rejected() throws Exception {
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        MvcResult sessionRes = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AttendanceSessionResponse session = objectMapper.readValue(
                sessionRes.getResponse().getContentAsString(), AttendanceSessionResponse.class);

        // Manually expire the challenge in DB to simulate timeout
        var challenge = challengeRepository.findByChallengeToken(session.getChallengeToken()).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(10));
        challengeRepository.save(challenge);

        long timestamp = Instant.now().getEpochSecond();
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(), 1L, openDriveId, student1DeviceId, stationId, timestamp);
        String signature = CryptoTestHelper.signCanonicalMessage(student1KeyPair.getPrivate(), canonicalMessage);

        mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AttendanceVerifyRequest(session.getChallengeToken(), signature, timestamp))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("challenge has expired")));
    }

    @Test
    @DisplayName("Replay of consumed challenge is rejected with 409 Conflict")
    void testReplayedChallenge_Rejected() throws Exception {
        // Step 1: Successful check in
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        String sessionJson = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andReturn().getResponse().getContentAsString();
        AttendanceSessionResponse session = objectMapper.readValue(sessionJson, AttendanceSessionResponse.class);

        long timestamp = Instant.now().getEpochSecond();
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(), 1L, openDriveId, student1DeviceId, stationId, timestamp);
        String signature = CryptoTestHelper.signCanonicalMessage(student1KeyPair.getPrivate(), canonicalMessage);

        AttendanceVerifyRequest verifyReq = new AttendanceVerifyRequest(session.getChallengeToken(), signature, timestamp);

        // First submission succeeds
        mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated());

        // Replay submission fails
        mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been used")));
    }

    @Test
    @DisplayName("Invalid signature or signature from wrong student key is rejected")
    void testInvalidSignature_Rejected() throws Exception {
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        String sessionJson = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andReturn().getResponse().getContentAsString();
        AttendanceSessionResponse session = objectMapper.readValue(sessionJson, AttendanceSessionResponse.class);

        // Sign with a foreign keypair not matching student 1's device public key
        KeyPair foreignKeyPair = CryptoTestHelper.generateP256KeyPair();
        long timestamp = Instant.now().getEpochSecond();
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(), 1L, openDriveId, student1DeviceId, stationId, timestamp);
        String invalidSignature = CryptoTestHelper.signCanonicalMessage(foreignKeyPair.getPrivate(), canonicalMessage);

        mockMvc.perform(post("/api/v1/attendance/verify")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AttendanceVerifyRequest(session.getChallengeToken(), invalidSignature, timestamp))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("signature verification failed")));
    }

    @Test
    @DisplayName("Revoked device cannot request session or mark attendance")
    void testRevokedDevice_Rejected() throws Exception {
        // Officer revokes device
        mockMvc.perform(post("/api/v1/devices/" + student1DeviceId + "/revoke")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RevokeDeviceRequest("Reported lost phone"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        // Student tries to request session
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("device has been revoked")));
    }

    @Test
    @DisplayName("Check-in attempt on closed drive is rejected (409 Conflict)")
    void testClosedDrive_Rejected() throws Exception {
        // Close drive
        mockMvc.perform(post("/api/v1/drives/" + openDriveId + "/close")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());

        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not OPEN")));
    }

    @Test
    @DisplayName("Concurrent check-in requests produce at most one record without corrupting state")
    void testConcurrentCheckIns_ProducesSingleRecord() throws Exception {
        // Request challenge
        AttendanceSessionRequest sessionReq = AttendanceSessionRequest.builder()
                .driveId(openDriveId)
                .stationId(stationId)
                .deviceId(student1DeviceId)
                .build();

        String sessionJson = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andReturn().getResponse().getContentAsString();
        AttendanceSessionResponse session = objectMapper.readValue(sessionJson, AttendanceSessionResponse.class);

        long timestamp = Instant.now().getEpochSecond();
        String canonicalMessage = cryptoService.buildCanonicalMessage(
                session.getChallengeToken(), 1L, openDriveId, student1DeviceId, stationId, timestamp);
        String signature = CryptoTestHelper.signCanonicalMessage(student1KeyPair.getPrivate(), canonicalMessage);

        AttendanceVerifyRequest verifyReq = new AttendanceVerifyRequest(session.getChallengeToken(), signature, timestamp);
        String requestPayload = objectMapper.writeValueAsString(verifyReq);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/attendance/verify")
                                    .header("Authorization", "Bearer " + student1Token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestPayload))
                            .andReturn();
                    int status = res.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // exception caught
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one check-in must succeed
        assertThat(successCount.get()).isEqualTo(1);
        // All other concurrent calls must fail cleanly
        assertThat(conflictCount.get()).isEqualTo(threads - 1);
        // Database must have exactly 1 record for this student and drive
        assertThat(recordRepository.findByDriveIdAndStudentId(openDriveId, 1L)).isPresent();
    }
}
