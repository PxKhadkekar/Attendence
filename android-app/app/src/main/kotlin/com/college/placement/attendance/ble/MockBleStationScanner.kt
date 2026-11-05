package com.college.placement.attendance.ble

import com.college.placement.attendance.BuildConfig
import com.college.placement.attendance.model.StationBeacon
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Development & Testing Mock for BLE Station Scanning.
 * Used when physical ESP32 beacon stations are unavailable.
 *
 * ZERO-TRUST ENFORCEMENT:
 * This class is HARD-LOCKED to BuildConfig.DEBUG.
 * Any attempt to instantiate or invoke this class in a production/release build
 * immediately throws a fatal SecurityException, guaranteeing it cannot be shipped
 * or exploited in production.
 */
class MockBleStationScanner(
    private val mockMajor: Int = 1,
    private val mockMinor: Int = 1,
    private val mockRssi: Int = -62, // Well within proximity threshold (>= -75 dBm)
    private val mockTxPower: Int = -59
) : BleStationScanner {

    private var scanning = false
    private var scheduler: ScheduledExecutorService? = null

    init {
        // Strict production guard: Impossible to enable in release builds
        if (!BuildConfig.DEBUG) {
            throw SecurityException(
                "CRITICAL SECURITY VIOLATION: MockBleStationScanner is strictly forbidden in release builds! " +
                        "Placement drive attendance requires genuine physical BLE radio proximity."
            )
        }
    }

    override fun startScanning(
        onStationDiscovered: (StationBeacon) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!BuildConfig.DEBUG) {
            throw SecurityException("CRITICAL: MockBleStationScanner forbidden in release profile")
        }

        scanning = true
        scheduler = Executors.newSingleThreadScheduledExecutor()

        // Periodically emit the simulated station beacon every 1.5 seconds
        scheduler?.scheduleWithFixedDelay({
            if (scanning) {
                val beacon = StationBeacon(
                    serviceUuid = StationBeacon.EXPECTED_COLLEGE_UUID,
                    major = mockMajor,
                    minor = mockMinor,
                    txPower = mockTxPower,
                    rssi = mockRssi
                )
                onStationDiscovered(beacon)
            }
        }, 500, 1500, TimeUnit.MILLISECONDS)
    }

    override fun stopScanning() {
        scanning = false
        scheduler?.shutdownNow()
        scheduler = null
    }

    override fun isScanning(): Boolean = scanning
}
