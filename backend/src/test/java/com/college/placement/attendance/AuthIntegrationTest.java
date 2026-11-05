package com.college.placement.attendance;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.auth.LoginResponse;
import com.college.placement.attendance.dto.auth.RefreshTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Officer should successfully login and receive JWT token")
    void officerLogin_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("officer@college.edu")
                .password("Officer@123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ROLE_OFFICER"))
                .andExpect(jsonPath("$.user.email").value("officer@college.edu"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        LoginResponse response = objectMapper.readValue(responseContent, LoginResponse.class);
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Student should login and receive student profile details")
    void studentLogin_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("student1@college.edu")
                .password("Student@123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.user.studentId").value("STU2026001"))
                .andExpect(jsonPath("$.user.department").value("Computer Science and Engineering"));
    }

    @Test
    @DisplayName("Login with invalid password should fail with 401 Unauthorized")
    void login_InvalidCredentials_Fails() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("officer@college.edu")
                .password("WrongPassword999")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("Valid refresh token should generate new access token")
    void refreshToken_Success() throws Exception {
        // Step 1: Login
        LoginRequest loginRequest = LoginRequest.builder()
                .username("officer@college.edu")
                .password("Officer@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);

        // Step 2: Refresh
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(loginResponse.getRefreshToken())
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("officer@college.edu"));
    }

    @Test
    @DisplayName("Accessing /api/v1/auth/me with Bearer token should return profile")
    void getCurrentUser_Authenticated_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("student2@college.edu")
                .password("Student@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("diya.patel@college.edu"))
                .andExpect(jsonPath("$.studentId").value("STU2026002"));
    }
}
