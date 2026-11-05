package com.college.placement.attendance.ble

import com.college.placement.attendance.model.StationBeacon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Parser for College Placement Proximity Beacons (iBeacon frame specification).
 * Extracts College UUID, Hall Major ID, Station Minor ID, and Calibrated TxPower.
 */
object BlePacketParser {

    // 0000FD5A-0000-1000-8000-00805F9B34FB in standard UUID format
    val EXPECTED_COLLEGE_UUID: UUID = UUID.fromString("0000fd5a-0000-1000-8000-00805f9b34fb")

    /**
     * Parses raw BLE advertisement scan record bytes.
     * Looks for 0xFF (Manufacturer Specific Data) with Apple Company ID 0x004C and sub-type 0x02 0x15.
     */
    fun parseScanRecord(scanRecord: ByteArray?, rssi: Int): StationBeacon? {
        if (scanRecord == null || scanRecord.size < 30) return null

        var index = 0
        while (index < scanRecord.size) {
            val length = scanRecord[index].toInt() and 0xFF
            if (length == 0) break // End of AD structures

            val type = scanRecord[index + 1].toInt() and 0xFF

            // Manufacturer Specific Data (0xFF)
            if (type == 0xFF && length >= 26) {
                val companyId = (scanRecord[index + 2].toInt() and 0xFF) or
                        ((scanRecord[index + 3].toInt() and 0xFF) shl 8)

                val beaconType = scanRecord[index + 4].toInt() and 0xFF
                val beaconSubLength = scanRecord[index + 5].toInt() and 0xFF

                // Check for Apple Company ID (0x004C) and Proximity Beacon Type (0x02, length 0x15 = 21 bytes)
                if (companyId == 0x004C && beaconType == 0x02 && beaconSubLength == 0x15) {
                    val uuidBytes = scanRecord.copyOfRange(index + 6, index + 22)
                    val parsedUuid = bytesToUuid(uuidBytes)

                    val buffer = ByteBuffer.wrap(scanRecord, index + 22, 5).order(ByteOrder.BIG_ENDIAN)
                    val major = buffer.short.toInt() and 0xFFFF
                    val minor = buffer.short.toInt() and 0xFFFF
                    val txPower = buffer.get().toInt() // signed 1-byte value

                    return StationBeacon(
                        serviceUuid = parsedUuid.toString().uppercase(),
                        major = major,
                        minor = minor,
                        txPower = txPower,
                        rssi = rssi
                    )
                }
            }

            index += length + 1
        }

        return null
    }

    /**
     * Converts a 16-byte array into a java.util.UUID (Big Endian).
     */
    fun bytesToUuid(bytes: ByteArray): UUID {
        require(bytes.size == 16) { "UUID bytes array must be exactly 16 bytes" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val mostSig = buffer.long
        val leastSig = buffer.long
        return UUID(mostSig, leastSig)
    }

    /**
     * Creates a synthetic 30-byte advertisement record for testing or mock broadcasting.
     */
    fun createSyntheticScanRecord(uuid: UUID, major: Int, minor: Int, txPower: Int): ByteArray {
        val record = ByteArray(30)
        // Flags structure (length 2, type 0x01, flags 0x06)
        record[0] = 0x02
        record[1] = 0x01
        record[2] = 0x06

        // Manufacturer Specific Data (length 26, type 0xFF)
        record[3] = 0x1A
        record[4] = 0xFF.toByte()

        // Company ID 0x004C (Little Endian: 0x4C, 0x00)
        record[5] = 0x4C
        record[6] = 0x00

        // Beacon Type 0x02, Sub-length 0x15 (21 bytes)
        record[7] = 0x02
        record[8] = 0x15

        // UUID (16 bytes)
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        System.arraycopy(buffer.array(), 0, record, 9, 16)

        // Major (2 bytes, Big Endian)
        record[25] = ((major shr 8) and 0xFF).toByte()
        record[26] = (major and 0xFF).toByte()

        // Minor (2 bytes, Big Endian)
        record[27] = ((minor shr 8) and 0xFF).toByte()
        record[28] = (minor and 0xFF).toByte()

        // TX Power (1 byte, signed)
        record[29] = txPower.toByte()

        return record
    }
}
