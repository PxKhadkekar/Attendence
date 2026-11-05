package com.college.placement.attendance.model

import kotlin.math.pow

/**
 * Represents a placement drive physical station detected via BLE beacon broadcast.
 * Proximity threshold: RSSI >= -75 dBm (calibrated for station radius ~2-3 meters).
 */
data class StationBeacon(
    val serviceUuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Approximate distance in meters based on log-distance path loss model:
     * d = 10 ^ ((TxPower - RSSI) / (10 * n)) where n = 2.0 (free space / indoor line-of-sight).
     */
    val estimatedDistanceMeters: Double
        get() {
            if (rssi == 0) return -1.0
            val ratio = (txPower - rssi) / (10.0 * 2.0)
            return 10.0.pow(ratio)
        }

    /**
     * Proximity gate: Student must be in close proximity to the physical station beacon
     * to prevent out-of-room spoofing.
     */
    val isWithinProximity: Boolean
        get() = rssi >= PROXIMITY_RSSI_THRESHOLD

    val stationCode: String
        get() = "STATION-%03d".format(minor)

    val hallCode: String
        get() = "HALL-%02d".format(major)

    companion object {
        const val PROXIMITY_RSSI_THRESHOLD = -75 // dBm
        const val EXPECTED_COLLEGE_UUID = "0000FD5A-0000-1000-8000-00805F9B34FB"
    }
}
