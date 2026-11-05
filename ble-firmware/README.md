# ESP32 BLE Station Beacon Firmware

## Overview
This firmware turns any standard ESP32, ESP32-C3, or ESP32-S3 microcontroller board into a dedicated zero-trust radio proximity beacon for placement drive attendance desks.

It continuously broadcasts non-connectable undirected BLE advertisements (`ADV_NONCONN_IND`) matching the College Attendance BLE Proximity Beacon Specification:
- **Proximity UUID**: `0000FD5A-0000-1000-8000-00805F9B34FB` (College Institutional Placement UUID)
- **Major ID**: Venue / Auditorium Hall identifier (e.g., 101)
- **Minor ID**: Desk / Station Unit identifier (e.g., 1)
- **TX Power**: Calibrated at 1 meter (-59 dBm)
- **Advertising Interval**: 100 ms (fast discovery by student devices)

---

## Hardware Requirements
- **Microcontroller**: Any standard ESP32 development board (e.g., ESP32 DevKit V1, ESP32-WROOM-32, ESP32-C3 SuperMini, or ESP32-S3).
- **Power**: Standard 5V USB-C or micro-USB power brick, or 3.7V LiPo battery.
- **Power Consumption**: ~15–20 mA average at 100ms interval. A 2000 mAh battery can power a station for over 100 hours.

---

## Building and Flashing

### Option A: Using PlatformIO (Recommended)
1. Install PlatformIO CLI or PlatformIO IDE extension in VS Code.
2. Connect your ESP32 board to your computer via USB.
3. Build and upload firmware:
   ```bash
   pio run -t upload
   ```
4. Open the serial monitor (115200 baud):
   ```bash
   pio device monitor
   ```

### Option B: Using Arduino IDE
1. Install the ESP32 board definitions in Arduino IDE via Board Manager (`esp32` by Espressif Systems).
2. Open `src/main.cpp`.
3. Select board: **ESP32 Dev Module**.
4. Set Upload Speed: `921600` and Flash Frequency: `80MHz`.
5. Click **Upload**.

---

## Live Serial Console Configuration Commands
The firmware supports on-the-fly configuration through the USB Serial console at 115200 baud:
- `SET_MAJOR <id>`: Sets the Hall/Venue ID (e.g., `SET_MAJOR 102`) and persists to NVS flash.
- `SET_MINOR <id>`: Sets the Station/Desk ID (e.g., `SET_MINOR 5`) and persists to NVS flash.
- `GET_CONFIG`: Displays active UUID, Hall ID, Station ID, TX power, and free heap memory.
- `REBOOT`: Reboots the ESP32 microcontroller.

---

## Security Model Notice
> [!WARNING]
> **BLE Radio Proximity Boundary**:
> BLE advertisements are strictly a radio proximity cue indicating that an authorized physical beacon is nearby. BLE advertisements **NEVER** serve as proof of human identity. Proof of student attendance is provided exclusively by the Android Keystore NIST P-256 private key and biometric prompt unlock.
