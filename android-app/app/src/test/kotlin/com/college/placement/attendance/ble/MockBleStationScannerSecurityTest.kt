package com.college.placement.attendance.ble

import com.college.placement.attendance.BuildConfig
import com.college.placement.attendance.model.StationBeacon
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockBleStationScannerSecurityTest {

    @BeforeEach
    fun setUp() {
        BuildConfig.DEBUG = true
    }

    @AfterEach
    fun tearDown() {
        BuildConfig.DEBUG = true
    }

    @Test
    @DisplayName("In DEBUG profile, MockBleStationScanner operates and discovers simulated beacon")
    fun testMockScannerInDebugMode() {
        BuildConfig.DEBUG = true

        val scanner = MockBleStationScanner(mockMajor = 1, mockMinor = 2, mockRssi = -65)
        val latch = CountDownLatch(1)
        var discoveredBeacon: StationBeacon? = null

        scanner.startScanning(
            onStationDiscovered = { beacon ->
                discoveredBeacon = beacon
                latch.countDown()
            },
            onError = {}
        )

        val completed = latch.await(3, TimeUnit.SECONDS)
        scanner.stopScanning()

        assertThat(completed).isTrue()
        assertThat(discoveredBeacon).isNotNull()
        assertThat(discoveredBeacon!!.minor).isEqualTo(2)
        assertThat(discoveredBeacon!!.stationCode).isEqualTo("STATION-002")
        assertThat(discoveredBeacon!!.isWithinProximity).isTrue()
    }

    @Test
    @DisplayName("In RELEASE profile (DEBUG=false), MockBleStationScanner strictly throws SecurityException on instantiation")
    fun testMockScannerForbiddenInReleaseProfile() {
        BuildConfig.DEBUG = false

        assertThatThrownBy {
            MockBleStationScanner()
        }
            .isInstanceOf(SecurityException::class.java)
            .hasMessageContaining("strictly forbidden in release builds")
    }

    @Test
    @DisplayName("In RELEASE profile, calling startScanning throws SecurityException even if constructed prior")
    fun testMockScannerStartScanningForbiddenInRelease() {
        BuildConfig.DEBUG = true
        val scanner = MockBleStationScanner()

        // Switch to release profile
        BuildConfig.DEBUG = false

        assertThatThrownBy {
            scanner.startScanning(
                onStationDiscovered = {},
                onError = {}
            )
        }
            .isInstanceOf(SecurityException::class.java)
            .hasMessageContaining("forbidden in release profile")
    }
}
