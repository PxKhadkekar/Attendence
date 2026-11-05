#ifdef ARDUINO
#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEBeacon.h>
#include <Preferences.h>
#endif

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#if __has_include("BeaconConfig.h")
#include "BeaconConfig.h"
#elif __has_include("../include/BeaconConfig.h")
#include "../include/BeaconConfig.h"
#else
#include "BeaconConfig.h"
#endif

#if __has_include("BleBeaconEncoder.h")
#include "BleBeaconEncoder.h"
#elif __has_include("../include/BleBeaconEncoder.h")
#include "../include/BleBeaconEncoder.h"
#else
#include "BleBeaconEncoder.h"
#endif

#ifdef ARDUINO

static Preferences preferences;
static uint16_t currentMajor = BeaconConfig::DEFAULT_MAJOR;
static uint16_t currentMinor = BeaconConfig::DEFAULT_MINOR;
static int8_t currentTxPower1m = BeaconConfig::DEFAULT_TX_POWER_1M;
static uint8_t proximityUuid[16];

void updateBeaconAdvertising() {
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->stop();

    uint8_t payload[BeaconConfig::BEACON_FRAME_SIZE];
    bool ok = BleBeaconEncoder::encode(
        payload,
        sizeof(payload),
        proximityUuid,
        currentMajor,
        currentMinor,
        currentTxPower1m
    );

    if (!ok) {
        Serial.println("[ERROR] Failed to encode beacon payload!");
        return;
    }

    BLEAdvertisementData advertisementData;
    std::string strData((char*)payload, sizeof(payload));
    advertisementData.addData(strData);

    pAdvertising->setAdvertisementData(advertisementData);
    pAdvertising->setAdvertisementType(ADV_TYPE_NONCONN_IND);
    pAdvertising->setMinInterval(BeaconConfig::ADV_INTERVAL_MIN);
    pAdvertising->setMaxInterval(BeaconConfig::ADV_INTERVAL_MAX);

    pAdvertising->start();
    Serial.printf("[BLE] Broadcasting STATION-%03d in HALL-%02d (Major=%d, Minor=%d)\n",
                  currentMinor, currentMajor, currentMajor, currentMinor);
}

void processSerialCommands() {
    if (!Serial.available()) return;

    String line = Serial.readStringUntil('\n');
    line.trim();

    if (line.startsWith("SET_MAJOR ")) {
        uint16_t newMajor = line.substring(10).toInt();
        if (newMajor > 0) {
            currentMajor = newMajor;
            preferences.putUShort("major", currentMajor);
            Serial.printf("[CONFIG] Major updated to %d\n", currentMajor);
            updateBeaconAdvertising();
        }
    } else if (line.startsWith("SET_MINOR ")) {
        uint16_t newMinor = line.substring(10).toInt();
        if (newMinor > 0) {
            currentMinor = newMinor;
            preferences.putUShort("minor", currentMinor);
            Serial.printf("[CONFIG] Minor updated to %d\n", currentMinor);
            updateBeaconAdvertising();
        }
    } else if (line.equalsIgnoreCase("GET_CONFIG")) {
        char uuidStr[37];
        BleBeaconEncoder::formatUuidString(proximityUuid, uuidStr);
        Serial.println("--- Current Station Configuration ---");
        Serial.printf("Station Code: STATION-%03d\n", currentMinor);
        Serial.printf("Hall Code:    HALL-%02d\n", currentMajor);
        Serial.printf("UUID:         %s\n", uuidStr);
        Serial.printf("Major:        %d\n", currentMajor);
        Serial.printf("Minor:        %d\n", currentMinor);
        Serial.printf("TX Power 1m:  %d dBm\n", currentTxPower1m);
        Serial.printf("Adv Interval: 100 ms\n");
        Serial.printf("Free Heap:    %d bytes\n", ESP.getFreeHeap());
        Serial.println("------------------------------------");
    } else if (line.equalsIgnoreCase("REBOOT")) {
        Serial.println("[SYSTEM] Rebooting ESP32...");
        ESP.restart();
    }
}

void setup() {
    Serial.begin(115200);
    delay(500);

    Serial.println("==================================================");
    Serial.println("College Placement Drive - BLE Station Beacon");
    Serial.println("Firmware Version: 1.0.0");
    Serial.println("Security Model: Zero-Trust Radio Proximity Node");
    Serial.println("==================================================");

    // Initialize Non-Volatile Storage (Preferences)
    preferences.begin("ble_station", false);
    currentMajor = preferences.getUShort("major", BeaconConfig::DEFAULT_MAJOR);
    currentMinor = preferences.getUShort("minor", BeaconConfig::DEFAULT_MINOR);

    // Parse College UUID
    if (!BleBeaconEncoder::parseUuidString(BeaconConfig::DEFAULT_UUID_STR, proximityUuid)) {
        Serial.println("[FATAL] Could not parse college proximity UUID string!");
    }

    // Initialize ESP32 BLE Stack
    BLEDevice::init("TP-STATION-BEACON");
    BLEDevice::setPower(ESP_PWR_LVL_P3); // Max broadcast range (+3 dBm)

    updateBeaconAdvertising();
    Serial.println("[STATUS] Beacon advertising active and running.");
}

void loop() {
    processSerialCommands();

    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat >= 30000) {
        lastHeartbeat = millis();
        Serial.printf("[HEARTBEAT] Station-%03d active | Free Heap: %d bytes | Uptime: %lu s\n",
                      currentMinor, ESP.getFreeHeap(), millis() / 1000);
    }

    delay(20);
}

#else
// Standalone Desktop / CLI Beacon Simulator (runs without physical ESP32)
int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    printf("==================================================\n");
    printf("College Placement Drive - BLE Station Beacon (Simulator)\n");
    printf("Hardware Target: ESP32 / ESP32-C3 / ESP32-S3\n");
    printf("Security Model: Zero-Trust Radio Proximity Node\n");
    printf("==================================================\n");

    uint8_t uuid[16];
    if (!BleBeaconEncoder::parseUuidString(BeaconConfig::DEFAULT_UUID_STR, uuid)) {
        fprintf(stderr, "[FATAL] Failed to parse college UUID string!\n");
        return 1;
    }

    uint8_t payload[BeaconConfig::BEACON_FRAME_SIZE];
    bool ok = BleBeaconEncoder::encode(
        payload,
        sizeof(payload),
        uuid,
        BeaconConfig::DEFAULT_MAJOR,
        BeaconConfig::DEFAULT_MINOR,
        BeaconConfig::DEFAULT_TX_POWER_1M
    );

    if (!ok) {
        fprintf(stderr, "[FATAL] Failed to encode beacon payload!\n");
        return 1;
    }

    char uuidFormatted[37];
    BleBeaconEncoder::formatUuidString(uuid, uuidFormatted);

    printf("[BEACON READY] Station: STATION-%03d | Hall: HALL-%02d\n",
           BeaconConfig::DEFAULT_MINOR, BeaconConfig::DEFAULT_MAJOR);
    printf("  UUID:        %s\n", uuidFormatted);
    printf("  Major ID:    %d (0x%04X)\n", BeaconConfig::DEFAULT_MAJOR, BeaconConfig::DEFAULT_MAJOR);
    printf("  Minor ID:    %d (0x%04X)\n", BeaconConfig::DEFAULT_MINOR, BeaconConfig::DEFAULT_MINOR);
    printf("  1m TX Power: %d dBm\n", BeaconConfig::DEFAULT_TX_POWER_1M);
    printf("  Packet Size: %zu bytes\n", sizeof(payload));
    printf("  Payload Hex: ");
    for (size_t i = 0; i < sizeof(payload); ++i) {
        printf("%02X ", payload[i]);
    }
    printf("\n");
    printf("[STATUS] Simulated beacon payload validated and ready for ESP32 flash.\n");
    return 0;
}
#endif // ARDUINO
