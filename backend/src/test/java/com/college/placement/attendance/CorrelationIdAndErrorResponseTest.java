package com.college.placement.attendance;

import com.college.placement.attendance.dto.auth.LoginRequest;
import com.college.placement.attendance.dto.drive.CreateDriveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdAndErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Incoming X-Correlation-ID should be propagated back in response header and body")
    void correlationId_PreservedInResponse() throws Exception {
        String testCorrelationId = "test-corr-id-998877";

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-ID", testCorrelationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "WrongPass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", testCorrelationId))
                .andExpect(jsonPath("$.correlationId").value(testCorrelationId));
    }

    @Test
    @DisplayName("Field validation errors return 400 with detailed field and message list")
    void validationErrors_ReturnStructuredList() throws Exception {
        // Missing companyName, title, date, venue
        CreateDriveRequest invalidRequest = new CreateDriveRequest();

        // Authenticate officer to bypass 403 and hit validation
        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("officer@college.edu", "Officer@123"))))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/drives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }
}
