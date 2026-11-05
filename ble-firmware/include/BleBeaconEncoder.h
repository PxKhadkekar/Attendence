#ifndef BLE_BEACON_ENCODER_H
#define BLE_BEACON_ENCODER_H

#include <stdint.h>
#include <stddef.h>
#if __has_include("BeaconConfig.h")
#include "BeaconConfig.h"
#elif __has_include("../include/BeaconConfig.h")
#include "../include/BeaconConfig.h"
#else
#include "BeaconConfig.h"
#endif

/**
 * Encodes standard BLE Proximity Beacon advertisement packets according to
 * the College Attendance BLE Protocol specification.
 */
class BleBeaconEncoder {
public:
    /**
     * Encodes a 30-byte raw advertising packet.
     * @param buffer Destination buffer (must be at least 30 bytes).
     * @param bufferSize Size of destination buffer.
     * @param uuid 16-byte proximity UUID.
     * @param major 2-byte Venue/Hall ID.
     * @param minor 2-byte Desk/Station ID.
     * @param txPower1m Calibrated RSSI at 1 meter (-128 to 127 dBm).
     * @return true if successfully encoded, false on buffer overflow.
     */
    static bool encode(
        uint8_t* buffer,
        size_t bufferSize,
        const uint8_t uuid[16],
        uint16_t major,
        uint16_t minor,
        int8_t txPower1m
    );

    /**
     * Parses a 36-character UUID string (with or without hyphens) into a 16-byte array.
     * Example: "0000FD5A-0000-1000-8000-00805F9B34FB"
     * @return true if valid UUID string, false otherwise.
     */
    static bool parseUuidString(const char* uuidStr, uint8_t outUuid[16]);

    /**
     * Formats a 16-byte UUID array into a standard 36-character formatted string.
     */
    static void formatUuidString(const uint8_t uuid[16], char outStr[37]);
};

#endif // BLE_BEACON_ENCODER_H
