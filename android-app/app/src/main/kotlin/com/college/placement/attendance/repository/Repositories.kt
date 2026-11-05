package com.college.placement.attendance.repository

import com.college.placement.attendance.api.HttpApiClient
import com.college.placement.attendance.api.dto.*
import com.college.placement.attendance.crypto.CryptoManager
import com.college.placement.attendance.model.AttendanceReceipt
import com.college.placement.attendance.model.Device
import com.college.placement.attendance.model.DeviceStatus
import com.college.placement.attendance.model.StationBeacon
import java.time.Instant
import java.util.Base64
import java.util.HexFormat

class AuthRepository(
    private val apiClient: HttpApiClient
) {
    private var currentUser: AuthResponse? = null

    fun login(username: String, password: String): AuthResponse {
        val response = apiClient.login(LoginRequest(username, password))
        this.currentUser = response
        return response
    }

    fun getCurrentUser(): AuthResponse? = currentUser
    fun isAuthenticated(): Boolean = currentUser != null
}

class DeviceRepository(
    private val apiClient: HttpApiClient,
    private val cryptoManager: CryptoManager
) {
    fun getOrEnrollDevice(rollNumber: String, deviceModel: String, osVersion: String): Device {
        val publicKeyPem = cryptoManager.getPublicKeyPem() ?: cryptoManager.generateKeyPair()
        val fingerprint = cryptoManager.getKeyFingerprint()!!

        val response = apiClient.registerDevice(
            DeviceRegisterRequest(
                rollNumber = rollNumber,
                deviceModel = deviceModel,
                osVersion = osVersion,
                publicKeyPem = publicKeyPem
            )
        )

        val status = try {
            DeviceStatus.valueOf(response.status)
        } catch (e: Exception) {
            DeviceStatus.PENDING_APPROVAL
        }

        return Device(
            id = response.deviceId,
            rollNumber = response.rollNumber,
            deviceModel = response.deviceModel,
            osVersion = osVersion,
            keyFingerprint = fingerprint,
            status = status
        )
    }

    fun checkDeviceStatus(): DeviceStatus {
        return try {
            val res = apiClient.getMyDevice()
            DeviceStatus.valueOf(res.status)
        } catch (e: Exception) {
            DeviceStatus.NOT_REGISTERED
        }
    }
}

open class AttendanceRepository(
    protected val apiClient: HttpApiClient,
    protected val cryptoManager: CryptoManager
) {
    /**
     * Executes the zero-trust attendance verification workflow:
     * 1. Validates that the station is within proximity threshold (RSSI >= -75 dBm).
     * 2. Requests a single-use 32-byte challenge nonce from the backend for the station and drive.
     * 3. Signs the challenge nonce using the hardware-backed NIST P-256 private key.
     * 4. Submits the signature and timestamp to the backend for verification.
     * 5. Returns the verified attendance receipt.
     */
    open fun submitBiometricAttendance(
        driveId: Long,
        stationBeacon: StationBeacon,
        stationId: Long
    ): AttendanceReceipt {
        if (!stationBeacon.isWithinProximity) {
            throw IllegalStateException(
                "Cannot mark attendance: Phone is outside station proximity threshold (RSSI ${stationBeacon.rssi} dBm < -75 dBm)"
            )
        }

        // 1. Request session challenge nonce
        val challengeRes = apiClient.requestChallenge(
            ChallengeRequest(driveId = driveId, stationId = stationId)
        )

        // 2. Decode nonce and sign with hardware-backed key
        // Nonce is 32-byte hex string (64 chars)
        val nonceBytes = HexFormat.of().parseHex(challengeRes.nonce)
        val signatureBytes = cryptoManager.signChallengeDirect(nonceBytes)
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

        // 3. Submit verification request
        val verifyRes = apiClient.verifyAttendance(
            VerifyAttendanceRequest(
                driveId = driveId,
                stationId = stationId,
                challengeId = challengeRes.challengeId,
                signature = signatureBase64,
                clientTimestamp = Instant.now().toString()
            )
        )

        return AttendanceReceipt(
            recordId = verifyRes.recordId,
            studentRollNumber = verifyRes.studentRollNumber,
            studentName = verifyRes.studentName,
            driveTitle = "Campus Placement Drive",
            companyName = verifyRes.companyName,
            stationCode = verifyRes.stationCode,
            hallName = verifyRes.hallName,
            verifiedAt = verifyRes.verifiedAt,
            signatureSnippet = signatureBase64.take(24) + "..."
        )
    }
}
