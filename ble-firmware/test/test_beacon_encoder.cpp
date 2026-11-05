#include <cassert>
#include <cstdio>
#include <cstring>
#include <iostream>
#include "../include/BleBeaconEncoder.h"
#include "../include/BeaconConfig.h"

void testUuidParsing() {
    std::cout << "[TEST] Running testUuidParsing..." << std::endl;

    uint8_t uuid[16];
    bool ok = BleBeaconEncoder::parseUuidString("0000FD5A-0000-1000-8000-00805F9B34FB", uuid);
    assert(ok == true);

    // Verify individual bytes:
    // 00 00 FD 5A - 00 00 - 10 00 - 80 00 - 00 80 5F 9B 34 FB
    assert(uuid[0] == 0x00);
    assert(uuid[1] == 0x00);
    assert(uuid[2] == 0xFD);
    assert(uuid[3] == 0x5A);
    assert(uuid[4] == 0x00);
    assert(uuid[5] == 0x00);
    assert(uuid[6] == 0x10);
    assert(uuid[7] == 0x00);
    assert(uuid[8] == 0x80);
    assert(uuid[9] == 0x00);
    assert(uuid[10] == 0x00);
    assert(uuid[11] == 0x80);
    assert(uuid[12] == 0x5F);
    assert(uuid[13] == 0x9B);
    assert(uuid[14] == 0x34);
    assert(uuid[15] == 0xFB);

    // Round-trip formatting
    char formatted[37];
    BleBeaconEncoder::formatUuidString(uuid, formatted);
    assert(strcmp(formatted, "0000FD5A-0000-1000-8000-00805F9B34FB") == 0);

    // Test rejection of invalid UUIDs
    uint8_t invalidUuid[16];
    assert(!BleBeaconEncoder::parseUuidString("TOO-SHORT", invalidUuid));
    assert(!BleBeaconEncoder::parseUuidString("0000FD5A-0000-1000-8000-ZZZZZZZZZZZZ", invalidUuid)); // non-hex
    assert(!BleBeaconEncoder::parseUuidString(nullptr, invalidUuid));

    std::cout << "  ✓ testUuidParsing passed!" << std::endl;
}

void testPacketEncoding() {
    std::cout << "[TEST] Running testPacketEncoding..." << std::endl;

    uint8_t uuid[16];
    BleBeaconEncoder::parseUuidString(BeaconConfig::DEFAULT_UUID_STR, uuid);

    uint8_t buffer[30];
    memset(buffer, 0, sizeof(buffer));

    uint16_t testMajor = 101; // 0x0065
    uint16_t testMinor = 1;   // 0x0001
    int8_t testTxPower = -59; // 0xC5

    bool success = BleBeaconEncoder::encode(buffer, sizeof(buffer), uuid, testMajor, testMinor, testTxPower);
    assert(success == true);

    // Byte 0..2: Flags
    assert(buffer[0] == 0x02);
    assert(buffer[1] == 0x01);
    assert(buffer[2] == 0x06);

    // Byte 3..4: Manufacturer Specific Data Header
    assert(buffer[3] == 0x1A); // Length 26
    assert(buffer[4] == 0xFF); // Type 0xFF

    // Byte 5..6: Apple Company ID 0x004C (Little-Endian: 4C 00)
    assert(buffer[5] == 0x4C);
    assert(buffer[6] == 0x00);

    // Byte 7..8: Beacon Type 0x02, Sub-length 0x15 (21 bytes)
    assert(buffer[7] == 0x02);
    assert(buffer[8] == 0x15);

    // Byte 9..24: 16-byte UUID
    assert(memcmp(&buffer[9], uuid, 16) == 0);

    // Byte 25..26: Major ID (Big-Endian)
    assert(buffer[25] == 0x00);
    assert(buffer[26] == 0x65);

    // Byte 27..28: Minor ID (Big-Endian)
    assert(buffer[27] == 0x00);
    assert(buffer[28] == 0x01);

    // Byte 29: TX Power at 1m (signed -59 = 0xC5)
    assert(buffer[29] == static_cast<uint8_t>(-59));

    // Buffer overflow check: passing buffer < 30 bytes must fail
    uint8_t smallBuffer[29];
    assert(!BleBeaconEncoder::encode(smallBuffer, sizeof(smallBuffer), uuid, testMajor, testMinor, testTxPower));

    std::cout << "  ✓ testPacketEncoding passed!" << std::endl;
}

void testCrossPlatformConsistency() {
    std::cout << "[TEST] Running testCrossPlatformConsistency..." << std::endl;

    // Verify endianness for multi-byte IDs
    uint8_t uuid[16];
    BleBeaconEncoder::parseUuidString("0000FD5A-0000-1000-8000-00805F9B34FB", uuid);

    uint8_t buffer[30];
    // Test with Major = 0x1234, Minor = 0xABCD
    BleBeaconEncoder::encode(buffer, sizeof(buffer), uuid, 0x1234, 0xABCD, -60);

    assert(buffer[25] == 0x12);
    assert(buffer[26] == 0x34);
    assert(buffer[27] == 0xAB);
    assert(buffer[28] == 0xCD);
    assert(buffer[29] == static_cast<uint8_t>(-60));

    std::cout << "  ✓ testCrossPlatformConsistency passed!" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "ESP32 BLE Beacon Firmware Test Runner" << std::endl;
    std::cout << "========================================" << std::endl;

    testUuidParsing();
    testPacketEncoding();
    testCrossPlatformConsistency();

    std::cout << "========================================" << std::endl;
    std::cout << "ALL 3 TEST SUITES PASSED (0 FAILURES)" << std::endl;
    std::cout << "========================================" << std::endl;

    return 0;
}
