#if __has_include("BleBeaconEncoder.h")
#include "BleBeaconEncoder.h"
#elif __has_include("../include/BleBeaconEncoder.h")
#include "../include/BleBeaconEncoder.h"
#else
#include "BleBeaconEncoder.h"
#endif
#include <string.h>
#include <stdio.h>

static int hexCharToInt(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

bool BleBeaconEncoder::encode(
    uint8_t* buffer,
    size_t bufferSize,
    const uint8_t uuid[16],
    uint16_t major,
    uint16_t minor,
    int8_t txPower1m
) {
    if (buffer == nullptr || bufferSize < BeaconConfig::BEACON_FRAME_SIZE || uuid == nullptr) {
        return false;
    }

    // Byte 0..2: Flags AD structure
    buffer[0] = 0x02; // Length: 2 bytes
    buffer[1] = 0x01; // AD Type: Flags (0x01)
    buffer[2] = 0x06; // Flags: LE General Discoverable (0x02) | BR/EDR Not Supported (0x04)

    // Byte 3..4: Manufacturer Specific Data Header
    buffer[3] = 0x1A; // Length: 26 bytes (0x1A)
    buffer[4] = 0xFF; // AD Type: Manufacturer Specific Data (0xFF)

    // Byte 5..6: Company Identifier (Apple ID 0x004C, Little-Endian: 0x4C, 0x00)
    buffer[5] = 0x4C;
    buffer[6] = 0x00;

    // Byte 7..8: Proximity Beacon Sub-Type (0x02) and Sub-Length (0x15 = 21 bytes)
    buffer[7] = 0x02;
    buffer[8] = 0x15;

    // Byte 9..24: 16-byte Proximity UUID (Big-Endian)
    memcpy(&buffer[9], uuid, 16);

    // Byte 25..26: Major ID (Venue / Auditorium ID, Big-Endian)
    buffer[25] = static_cast<uint8_t>((major >> 8) & 0xFF);
    buffer[26] = static_cast<uint8_t>(major & 0xFF);

    // Byte 27..28: Minor ID (Station Unit / Desk ID, Big-Endian)
    buffer[27] = static_cast<uint8_t>((minor >> 8) & 0xFF);
    buffer[28] = static_cast<uint8_t>(minor & 0xFF);

    // Byte 29: Measured TX Power at 1 meter (signed 8-bit integer, e.g. -59 = 0xC5)
    buffer[29] = static_cast<uint8_t>(txPower1m);

    return true;
}

bool BleBeaconEncoder::parseUuidString(const char* uuidStr, uint8_t outUuid[16]) {
    if (uuidStr == nullptr || outUuid == nullptr) {
        return false;
    }

    size_t byteIdx = 0;
    int highNibble = -1;

    for (size_t i = 0; uuidStr[i] != '\0' && byteIdx < 16; ++i) {
        char c = uuidStr[i];
        if (c == '-' || c == ' ' || c == ':') {
            continue; // Skip separators
        }

        int val = hexCharToInt(c);
        if (val < 0) {
            return false; // Invalid hex character
        }

        if (highNibble < 0) {
            highNibble = val;
        } else {
            outUuid[byteIdx++] = static_cast<uint8_t>((highNibble << 4) | val);
            highNibble = -1;
        }
    }

    return (byteIdx == 16 && highNibble < 0);
}

void BleBeaconEncoder::formatUuidString(const uint8_t uuid[16], char outStr[37]) {
    if (uuid == nullptr || outStr == nullptr) return;

    snprintf(outStr, 37,
        "%02X%02X%02X%02X-%02X%02X-%02X%02X-%02X%02X-%02X%02X%02X%02X%02X%02X",
        uuid[0], uuid[1], uuid[2], uuid[3],
        uuid[4], uuid[5],
        uuid[6], uuid[7],
        uuid[8], uuid[9],
        uuid[10], uuid[11], uuid[12], uuid[13], uuid[14], uuid[15]
    );
}
