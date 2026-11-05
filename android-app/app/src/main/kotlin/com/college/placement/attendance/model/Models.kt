package com.college.placement.attendance.model

data class User(
    val id: Long,
    val rollNumber: String,
    val email: String,
    val fullName: String,
    val department: String,
    val role: String
)

data class Device(
    val id: Long?,
    val rollNumber: String,
    val deviceModel: String,
    val osVersion: String,
    val keyFingerprint: String,
    val status: DeviceStatus,
    val enrolledAt: String? = null
)

enum class DeviceStatus {
    PENDING_APPROVAL,
    APPROVED,
    REVOKED,
    NOT_REGISTERED
}

data class Drive(
    val id: Long,
    val title: String,
    val companyName: String,
    val roleTitle: String,
    val targetDepartment: String,
    val status: String,
    val driveDate: String
)

data class AttendanceReceipt(
    val recordId: Long,
    val studentRollNumber: String,
    val studentName: String,
    val driveTitle: String,
    val companyName: String,
    val stationCode: String,
    val hallName: String,
    val verifiedAt: String,
    val signatureSnippet: String
)
