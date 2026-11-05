#ifndef BEACON_CONFIG_H
#define BEACON_CONFIG_H

#include <stdint.h>

/**
 * College Placement Drive BLE Station Beacon Configuration
 * Proximity UUID: 0000FD5A-0000-1000-8000-00805F9B34FB
 */
namespace BeaconConfig {

    // 16-byte Institutional Placement UUID
    constexpr const char* DEFAULT_UUID_STR = "0000FD5A-0000-1000-8000-00805F9B34FB";

    // Default Venue / Hall ID (Major: 101 -> Auditorium / Hall 1)
    constexpr uint16_t DEFAULT_MAJOR = 101;

    // Default Desk / Station ID (Minor: 1 -> Station 001)
    constexpr uint16_t DEFAULT_MINOR = 1;

    // Calibrated RSSI at 1 meter distance in dBm (standard iBeacon value)
    constexpr int8_t DEFAULT_TX_POWER_1M = -59;

    // BLE Advertising interval: 100 ms (160 * 0.625 ms = 100 ms)
    // 100ms interval enables rapid discovery by moving student phones
    constexpr uint16_t ADV_INTERVAL_MIN = 160;
    constexpr uint16_t ADV_INTERVAL_MAX = 160;

    // Beacon Frame Size: Exactly 30 bytes
    constexpr size_t BEACON_FRAME_SIZE = 30;
}

#endif // BEACON_CONFIG_H
