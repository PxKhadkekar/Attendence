package com.college.placement.attendance.repository

import com.college.placement.attendance.api.HttpApiClient
import com.college.placement.attendance.api.dto.ChallengeRequest
import com.college.placement.attendance.api.dto.ChallengeResponse
import com.college.placement.attendance.api.dto.VerifyAttendanceRequest
import com.college.placement.attendance.api.dto.VerifyAttendanceResponse
import com.college.placement.attendance.crypto.StandardJvmCryptoManager
import com.college.placement.attendance.model.StationBeacon
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AttendanceFlowTest {

    private lateinit var cryptoManager: StandardJvmCryptoManager

    @BeforeEach
    fun setUp() {
        cryptoManager = StandardJvmCryptoManager()
        cryptoManager.generateKeyPair()
    }

    @Test
    @DisplayName("Attendance submission throws if phone is outside station proximity threshold (RSSI < -75)")
    fun testRejectsOutsideProximity() {
        val outOfRangeBeacon = StationBeacon(
            serviceUuid = StationBeacon.EXPECTED_COLLEGE_UUID,
            major = 1,
            minor = 1,
            txPower = -59,
            rssi = -80 // Out of range!
        )

        val repo = AttendanceRepository(HttpApiClient(), cryptoManager)

        assertThatThrownBy {
            repo.submitBiometricAttendance(
                driveId = 1L,
                stationBeacon = outOfRangeBeacon,
                stationId = 1L
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("outside station proximity threshold")
    }

    @Test
    @DisplayName("Simulated end-to-end attendance cryptographic challenge signing and receipt generation")
    fun testSimulatedAttendanceVerification() {
        val inRangeBeacon = StationBeacon(
            serviceUuid = StationBeacon.EXPECTED_COLLEGE_UUID,
            major = 1,
            minor = 1,
            txPower = -59,
            rssi = -65 // Within range!
        )

        val mockChallengeId = UUID.randomUUID().toString()
        val mockNonce = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" // 32-byte hex

        val mockApiClient = object : HttpApiClient() {
            override fun requestChallenge(request: ChallengeRequest): ChallengeResponse {
                return ChallengeResponse(
                    challengeId = mockChallengeId,
                    nonce = mockNonce,
                    expiresAt = Instant.now().plusSeconds(60).toString(),
                    maxValiditySeconds = 60
                )
            }

            override fun verifyAttendance(request: VerifyAttendanceRequest): VerifyAttendanceResponse {
                assertThat(request.challengeId).isEqualTo(mockChallengeId)
                assertThat(request.signature).isNotBlank()
                return VerifyAttendanceResponse(
                    status = "VERIFIED",
                    recordId = 501L,
                    verifiedAt = Instant.now().toString(),
                    studentRollNumber = "CS2024001",
                    studentName = "Aarav Sharma",
                    companyName = "Acme Corp",
                    stationCode = "STATION-001",
                    hallName = "Auditorium Hall A",
                    message = "Attendance cryptographically verified"
                )
            }
        }

        val repo = AttendanceRepository(mockApiClient, cryptoManager)

        val receipt = repo.submitBiometricAttendance(
            driveId = 1L,
            stationBeacon = inRangeBeacon,
            stationId = 1L
        )

        assertThat(receipt).isNotNull()
        assertThat(receipt.recordId).isEqualTo(501L)
        assertThat(receipt.studentRollNumber).isEqualTo("CS2024001")
        assertThat(receipt.companyName).isEqualTo("Acme Corp")
        assertThat(receipt.stationCode).isEqualTo("STATION-001")
        assertThat(receipt.signatureSnippet).endsWith("...")
    }
}
