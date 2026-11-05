# BLE Proximity Protocol & Station Hardware Design

## 1. Scope & Security Statement
> [!WARNING]
> **Fundamental Security Limitation**:
> Bluetooth Low Energy (BLE) advertisements are strictly a **radio proximity cue** indicating that an authorized ESP32 hardware beacon is detectable by the phone's radio.
> **BLE CANNOT AND MUST NOT BE USED AS PROOF OF STUDENT IDENTITY.**
> BLE advertisements can be sniffed, recorded, or relayed across distances using software-defined radios (SDR) or network proxies. Identity proof is provided exclusively by the hardware-backed Android Keystore ECDSA signature and single-use, server-validated cryptographic challenge.

---

## 2. Beacon Advertising Packet Format

The ESP32 station broadcasts standard non-connectable undirected BLE advertisements (`ADV_NONCONN_IND`) formatted according to the standard Proximity Beacon specification:

```
+-----------------------------------------------------------------------------------------+
| Byte Index | Field Length | Value / Description                                         |
+-----------------------------------------------------------------------------------------+
| 0x00       | 1 byte       | Length of Flags AD (0x02)                                   |
| 0x01       | 1 byte       | AD Type: Flags (0x01)                                       |
| 0x02       | 1 byte       | Flag value: 0x06 (LE General Discoverable + BR/EDR Not Supp)|
| 0x03       | 1 byte       | Length of Manufacturer Data (0x1A / 26 bytes)               |
| 0x04..0x05 | 2 bytes      | Company ID: 0x004C (Standard iBeacon Subtype Indicator)     |
| 0x06..0x07 | 2 bytes      | Beacon Type: 0x0215 (Proximity Beacon)                      |
| 0x08..0x17 | 16 bytes     | Proximity UUID (College Institutional Placement UUID)       |
|            |              | Default: 0000FD5A-0000-1000-8000-00805F9B34FB               |
| 0x18..0x19 | 2 bytes      | Major ID (Venue / Auditorium ID, 0 - 65535)                 |
| 0x1A..0x1B | 2 bytes      | Minor ID (Specific Station Unit / Desk ID, 0 - 65535)        |
| 0x1C       | 1 byte       | Measured Power (TX Power calibrated at 1 meter, e.g. -59)   |
+-----------------------------------------------------------------------------------------+
```

---

## 3. Radio Proximity Verification & Thresholding

1. **RSSI Filtering**:
   - The Android client scans for beacons matching the institutional `Proximity UUID`.
   - The client calculates filtered RSSI over a sliding window of 3 scan samples.
   - An RSSI cutoff threshold of **`>= -75 dBm`** (approx. 3–5 meters in an indoor classroom/auditorium environment) is enforced before enabling the "Check In" button.
2. **Station Binding**:
   - The student app extracts the `Major` and `Minor` IDs from the scanned packet.
   - The station ID is transmitted in the session challenge request (`POST /api/v1/attendance/sessions`).
   - The backend validates that the station is active and mapped to the drive's venue before issuing a challenge.

---

## 4. Android Version-Aware Permission Strategy

Android permissions for BLE have evolved across API levels. The student application requests minimal, privacy-preserving permissions:

### API Level 31+ (Android 12, 13, 14, 15)
- Requires `android.permission.BLUETOOTH_SCAN` with attribute:
  ```xml
  android:usesPermissionFlags="neverForLocation"
  ```
- Requires `android.permission.BLUETOOTH_CONNECT`.
- **Zero Location Permissions Required**: Because `neverForLocation` is asserted and the app only uses BLE for station detection, `ACCESS_FINE_LOCATION` is **NOT** requested or required on Android 12+.

### API Level 26–30 (Android 8.0 - 11)
- Requires `android.permission.BLUETOOTH` and `android.permission.BLUETOOTH_ADMIN`.
- Requires `android.permission.ACCESS_FINE_LOCATION` (mandatory on older Android versions for BLE discovery).
- Displays clear user disclosure: *"Location permission is required by Android OS solely to scan for the classroom attendance beacon."*

---

## 5. Development Mock Station Interface
To ensure testability and development when physical ESP32 boards are not connected, the Android application defines:

```kotlin
interface BleStationScanner {
    fun startScan(): Flow<BleStationDetection>
    fun stopScan()
}
```

- **Production Implementation**: `NativeAndroidBleScanner` interacting with `BluetoothLeScanner`.
- **Dev Mock Implementation**: `MockBleStationScanner` simulating the proximity detection of a test station.
- **Safety Enforcement**: The mock is instantiated via a Dagger/Hilt or Factory provider configured with:
  ```kotlin
  if (!BuildConfig.DEBUG) {
      throw IllegalStateException("MockBleStationScanner cannot be instantiated in a release build.")
  }
  ```
