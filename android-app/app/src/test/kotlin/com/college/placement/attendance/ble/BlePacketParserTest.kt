package com.college.placement.attendance.ble

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class BlePacketParserTest {

    @Test
    @DisplayName("Parse valid synthetic College Proximity BLE beacon advertisement")
    fun testParseValidCollegeBeacon() {
        val collegeUuid = BlePacketParser.EXPECTED_COLLEGE_UUID
        val major = 102
        val minor = 4
        val txPower = -59
        val rssi = -64 // Proximity threshold is >= -75 dBm

        val scanRecord = BlePacketParser.createSyntheticScanRecord(collegeUuid, major, minor, txPower)
        val beacon = BlePacketParser.parseScanRecord(scanRecord, rssi)

        assertThat(beacon).isNotNull()
        assertThat(beacon!!.major).isEqualTo(102)
        assertThat(beacon.minor).isEqualTo(4)
        assertThat(beacon.txPower).isEqualTo(-59)
        assertThat(beacon.rssi).isEqualTo(-64)
        assertThat(beacon.stationCode).isEqualTo("STATION-004")
        assertThat(beacon.hallCode).isEqualTo("HALL-102")
        assertThat(beacon.isWithinProximity).isTrue()
        assertThat(beacon.estimatedDistanceMeters).isGreaterThan(0.0)
    }

    @Test
    @DisplayName("Proximity threshold correctly identifies beacons out of range (< -75 dBm)")
    fun testProximityThresholdOutOfRange() {
        val collegeUuid = BlePacketParser.EXPECTED_COLLEGE_UUID
        val scanRecord = BlePacketParser.createSyntheticScanRecord(collegeUuid, 1, 1, -59)

        // RSSI of -82 dBm is outside threshold
        val outOfRangeBeacon = BlePacketParser.parseScanRecord(scanRecord, -82)
        assertThat(outOfRangeBeacon).isNotNull()
        assertThat(outOfRangeBeacon!!.isWithinProximity).isFalse()

        // RSSI of -70 dBm is inside threshold
        val inRangeBeacon = BlePacketParser.parseScanRecord(scanRecord, -70)
        assertThat(inRangeBeacon).isNotNull()
        assertThat(inRangeBeacon!!.isWithinProximity).isTrue()
    }

    @Test
    @DisplayName("Reject corrupted or non-beacon advertisement records")
    fun testRejectInvalidRecord() {
        val invalidRecord = ByteArray(25) { 0x00 }
        val beacon = BlePacketParser.parseScanRecord(invalidRecord, -60)
        assertThat(beacon).isNull()

        val nullBeacon = BlePacketParser.parseScanRecord(null, -60)
        assertThat(nullBeacon).isNull()
    }
}
