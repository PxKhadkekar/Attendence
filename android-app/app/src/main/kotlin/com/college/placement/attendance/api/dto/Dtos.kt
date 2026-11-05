package com.college.placement.attendance.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginRequest(
    @JsonProperty("username") val username: String,
    @JsonProperty("password") val password: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuthResponse(
    @JsonProperty("token") val token: String,
    @JsonProperty("type") val type: String = "Bearer",
    @JsonProperty("id") val id: Long,
    @JsonProperty("username") val username: String,
    @JsonProperty("email") val email: String,
    @JsonProperty("fullName") val fullName: String,
    @JsonProperty("role") val role: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceRegisterRequest(
    @JsonProperty("rollNumber") val rollNumber: String,
    @JsonProperty("deviceModel") val deviceModel: String,
    @JsonProperty("osVersion") val osVersion: String,
    @JsonProperty("publicKeyPem") val publicKeyPem: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceRegisterResponse(
    @JsonProperty("deviceId") val deviceId: Long?,
    @JsonProperty("rollNumber") val rollNumber: String,
    @JsonProperty("deviceModel") val deviceModel: String,
    @JsonProperty("keyFingerprint") val keyFingerprint: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("message") val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MyDeviceResponse(
    @JsonProperty("deviceId") val deviceId: Long?,
    @JsonProperty("rollNumber") val rollNumber: String,
    @JsonProperty("deviceModel") val deviceModel: String,
    @JsonProperty("osVersion") val osVersion: String,
    @JsonProperty("keyFingerprint") val keyFingerprint: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("approvedAt") val approvedAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChallengeRequest(
    @JsonProperty("driveId") val driveId: Long,
    @JsonProperty("stationId") val stationId: Long
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChallengeResponse(
    @JsonProperty("challengeId") val challengeId: String,
    @JsonProperty("nonce") val nonce: String,
    @JsonProperty("expiresAt") val expiresAt: String,
    @JsonProperty("maxValiditySeconds") val maxValiditySeconds: Long = 60
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VerifyAttendanceRequest(
    @JsonProperty("driveId") val driveId: Long,
    @JsonProperty("stationId") val stationId: Long,
    @JsonProperty("challengeId") val challengeId: String,
    @JsonProperty("signature") val signature: String,
    @JsonProperty("clientTimestamp") val clientTimestamp: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VerifyAttendanceResponse(
    @JsonProperty("status") val status: String,
    @JsonProperty("recordId") val recordId: Long,
    @JsonProperty("verifiedAt") val verifiedAt: String,
    @JsonProperty("studentRollNumber") val studentRollNumber: String,
    @JsonProperty("studentName") val studentName: String,
    @JsonProperty("companyName") val companyName: String,
    @JsonProperty("stationCode") val stationCode: String,
    @JsonProperty("hallName") val hallName: String,
    @JsonProperty("message") val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DriveDto(
    @JsonProperty("id") val id: Long,
    @JsonProperty("title") val title: String,
    @JsonProperty("companyName") val companyName: String,
    @JsonProperty("roleTitle") val roleTitle: String,
    @JsonProperty("targetDepartment") val targetDepartment: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("driveDate") val driveDate: String
)
