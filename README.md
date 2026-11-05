# Zero-Trust Biometric & BLE Placement Drive Attendance System

[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java 21 LTS](https://img.shields.io/badge/Java-21_LTS-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![React 19](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript 5.6](https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Android Keystore](https://img.shields.io/badge/Android-Keystore_ECDSA-3DDC84?logo=android&logoColor=white)](https://developer.android.com/training/articles/keystore)
[![ESP32 BLE](https://img.shields.io/badge/ESP32-iBeacon_Firmware-E7352C?logo=espressif&logoColor=white)](https://www.espressif.com/)
[![Docker Compose](https://img.shields.io/badge/Docker-Compose_Ready-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

A production-grade, zero-trust attendance management system designed for University Training & Placement (T&P) departments to completely eliminate proxy attendance during on-campus recruitment drives.

---

## 1. System Architecture

The system coordinates four tightly-coupled components using asymmetric cryptography and physical radio constraints:

```mermaid
graph TB
    subgraph "Placement Hall (Physical Proximity Zone)"
        ESP[ESP32 BLE Beacon<br/>iBeacon Proximity Transmitter<br/>UUID: 0000FD5A-... Major: 101 Minor: 1]
        Phone[Student Android Device<br/>Android Keystore + BiometricPrompt<br/>Hardware-backed ECDSA P-256]
    end

    subgraph "Officer Station (Browser)"
        Officer[T&P Officer Portal<br/>React 19 + TypeScript + Vite<br/>Live SSE Real-Time Stream]
    end

    subgraph "University Infrastructure (Docker)"
        Nginx[Nginx Reverse Proxy / Gateway<br/>Gzip + Security Headers + SSE Buffering Off]
        Backend[Spring Boot 3.3.4 Backend<br/>Java 21 + Spring Security + Flyway]
        DB[(MySQL 8.0 Master Database<br/>Pessimistic Locking & Audit Trail)]
    end

    ESP -.->|Radio Proximity Only<br/>RSSI Threshold <= -75 dBm| Phone
    Phone -->|1. Scan BLE Beacon<br/>Capture UUID, Major, Minor| Phone
    Phone -->|2. Request Challenge Nonce<br/>POST /api/v1/attendance/sessions| Nginx
    Nginx --> Backend
    Backend --> DB
    Backend -->|Return 60s Single-Use Nonce| Phone
    Phone -->|3. Local Biometric Unlock<br/>CryptoObject + Keystore Private Key| Phone
    Phone -->|4. Submit ECDSA P-256 Signature<br/>POST /api/v1/attendance/verify| Nginx
    Backend -->|Verify PubKey PEM & Mark Attended| DB
    Backend -.->|5. Real-Time Push Event<br/>Server-Sent Events /api/v1/drives/{id}/live| Nginx
    Nginx -.-> Officer
```

---

## 2. Zero-Trust Identity & Proximity Model

Traditional campus attendance solutions fail because they treat phone proximity or biometric flags as human identity. This system enforces strict technological boundaries:

| Layer | Responsibility | What It Proves | What It CANNOT Prove |
| :--- | :--- | :--- | :--- |
| **ESP32 BLE Beacon** | Fixed-venue radio beacon broadcasting continuous 30-byte iBeacon frames at 100ms intervals (-59 dBm calibrated). | Physical proximity of an active radio receiver to the placement hall venue ($RSSI \ge -75 \text{ dBm}$). | **Never proves human identity or student presence.** Radio signals can be relayed. |
| **Android Keystore (NIST P-256)** | Generates asymmetric keypair `secp256r1` with `setUserAuthenticationRequired(true)`. Unlocked per transaction via `BiometricPrompt(CryptoObject)`. | The designated physical smartphone authorized during registration participated in signing the specific server challenge nonce. | **Does not prove the student did not share their smartphone passcode or PIN.** |
| **Backend Verification Engine** | Issues single-use nonces expiring in 60s, executes pessimistic write locking on challenge records, verifies ECDSA SHA256withECDSA signatures against enrolled Public Key PEMs. | Mathematical proof that the authorized private key signed `(nonce \| studentId \| driveId \| deviceId \| stationId \| timestamp)`. Single-use flag prevents replay. | N/A (Guarantees zero unauthorized proxy check-ins provided the enrolled device key is secure). |

> [!IMPORTANT]
> **No biometric templates, raw fingerprints, or boolean "biometric success" flags are EVER sent across the network.** Verification is exclusively mathematical via standard asymmetric cryptography.

---

## 3. Subsystem Overview

### Backend (`/backend`)
- **Technology**: Java 21, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Flyway 10, Apache POI 5.2.5.
- **Key Capabilities**:
  - Flyway migrations (V1–V5) establishing 14 normalized database tables.
  - Multi-sheet streaming SXSSFWorkbook Excel reports (`Summary`, `Present`, `Absent`, `All Attendance`, `Verification Audit`) with strict row-count parity.
  - Anti-Formula Injection sanitization (prefixing `=`, `+`, `-`, `@`, `\t`, `\r` with single quotes).
  - Pessimistic write locking on challenge sessions (`SELECT ... FOR UPDATE`) preventing concurrent double-submission race conditions.
  - Server-Sent Events (SSE) broadcasting live check-ins to officer monitoring dashboards.

### Frontend (`/frontend`)
- **Technology**: React 19, TypeScript 5.6, Vite 8, Tailwind CSS v4, TanStack Query v5, Lucide React, Vitest.
- **Key Capabilities**:
  - Full drive lifecycle controls: `DRAFT` $\rightarrow$ `SCHEDULED` $\rightarrow$ `OPEN` $\rightarrow$ `CLOSED`.
  - Excel drag-and-drop roster upload with rejected-row download capability.
  - Real-time attendance monitoring grid powered by SSE streaming with auto-reconnect.
  - Device approval workflow for student hardware binding.
  - 1-click SXSSFWorkbook 5-sheet placement audit export.

### Android Application (`/android-app`)
- **Technology**: Kotlin 1.9, Jetpack Compose / ViewBinding, AndroidX Biometric 1.2.0, Retrofit 2, Coroutines, StateFlow.
- **Key Capabilities**:
  - `CryptoManager`: Hardware-backed `AndroidKeyStore` NIST P-256 (`secp256r1`) key generation and signing.
  - `BlePacketParser`: Bit-accurate 30-byte Apple iBeacon frame decoder with Proximity UUID validation.
  - `MockBleStationScannerSecurityTest`: Hard security lock ensuring debug mock scanners are structurally stripped and rejected in release/production builds (`BuildConfig.DEBUG`).

### BLE Station Firmware (`/ble-firmware`)
- **Technology**: C++17, PlatformIO, Arduino ESP32 core (`ESP32 BLE Arduino`).
- **Key Capabilities**:
  - `BleBeaconEncoder`: Encodes 30-byte advertising packet (Flags + Manufacturer Specific Apple 0x004C iBeacon payload).
  - Serial CLI command parser for runtime UUID, Major, Minor, and TxPower reconfiguration stored in ESP32 Non-Volatile Storage (NVS).
  - Cross-platform native test harness compilable via `clang++` or `g++`.

---

## 4. Quickstart Guide

### Option A: Complete Docker Compose Deployment (Recommended)

To run the entire system (MySQL 8.0, Spring Boot backend, and Nginx frontend) in isolated production containers:

1. Copy the environment template:
   ```bash
   cp .env.example .env
   ```
2. Start all services:
   ```bash
   docker compose up -d
   ```
3. Check container health:
   ```bash
   docker compose ps
   ```
4. Access the portals:
   - **T&P Officer Portal**: [http://localhost](http://localhost) (or port 80)
   - **Backend API & Swagger UI**: [http://localhost/swagger-ui.html](http://localhost/swagger-ui.html)
   - **API Base URL**: [http://localhost/api/v1](http://localhost/api/v1)

---

### Option B: Local Development Setup (Without Docker)

#### 1. Database (MySQL 8.0)
Create a database named `placement_attendance`:
```sql
CREATE DATABASE placement_attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. Backend (Spring Boot 3.3.4)
```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home # Or your local JDK 21
mvn spring-boot:run
```
*Runs on `http://localhost:8080`.*

#### 3. Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
*Runs on `http://localhost:5173` (proxies `/api` and `/swagger-ui` to port 8080).*

#### 4. Android App (Development & Testing)
```bash
cd android-app
mvn test
```
*For Android Studio: Open `/android-app` and run on API 26+ device or emulator with biometric hardware.*

#### 5. ESP32 Firmware
```bash
cd ble-firmware
# Run native unit tests
clang++ -std=c++17 -Iinclude -Isrc test/test_beacon_encoder.cpp src/BleBeaconEncoder.cpp -o test_runner && ./test_runner

# Flash to physical ESP32 via PlatformIO
pio run -t upload
```

---

## 5. Automated Test Suites & Regression Verification

Every module includes automated test suites covering cryptographic correctness, concurrency, security defenses, and lifecycle flows:

| Module | Command | Coverage | Passing Tests |
| :--- | :--- | :--- | :---: |
| **Backend Integration & E2E** | `cd backend && mvn test` | Full 11-step E2E lifecycle, pessimistic lock concurrency, Excel formula injection, SXSSFWorkbook parity, JWT auth. | **33 / 33** |
| **Android Security & Crypto** | `cd android-app && mvn test` | NIST P-256 ECDSA signing, iBeacon frame parser, zero-trust mock lock. | **10 / 10** |
| **Frontend UI & Components** | `cd frontend && npm test` | Status badge variants, login form validation, navbar navigation. | **8 / 8** |
| **Frontend Build Validation** | `cd frontend && npm run build` | TypeScript compilation (`tsc -b`) and Vite production bundle. | **Zero Errors** |
| **Firmware Native Tests** | `cd ble-firmware && clang++ ...` | 30-byte frame encoding, big-endian UUID parsing, cross-platform alignment. | **3 / 3** |

---

## 6. Seed Accounts & Default Credentials

Pre-configured in Flyway database migration `V5__seed_initial_roles_and_stations.sql`:

| Role | Username / Institutional Email | Default Password | Enrolled Station / Data |
| :--- | :--- | :--- | :--- |
| **T&P Officer** | `officer@college.edu` | `Officer@123` | Full Administrative Privileges |
| **Student 1** | `student1@college.edu` (`STU2026001`) | `Student@123` | Aarav Sharma (CSE 2026) |
| **Student 2** | `student2@college.edu` (`STU2026002`) | `Student@123` | Diya Patel (IT 2026) |
| **Student 3** | `student3@college.edu` (`STU2026003`) | `Student@123` | Rohan Mehta (ECE 2026) |

**Default Active BLE Hardware Stations**:
1. `STATION-AUD-01` (Auditorium Main Stage) — Major: `101`, Minor: `1`, UUID: `0000FD5A-0000-1000-8000-00805F9B34FB`
2. `STATION-LAB-02` (T&P Seminar Hall 2) — Major: `102`, Minor: `1`, UUID: `0000FD5A-0000-1000-8000-00805F9B34FB`

---

## 7. STRIDE Threat Mitigation Matrix

| STRIDE Threat | Potential Attack Vector | System Defense | Implementation Location |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Student attempts check-in using an unauthorized friend's phone. | Single device enrollment policy with officer approval; NIST P-256 public key bound to hardware keystore. | `DeviceService.java`, `CryptoManager.kt` |
| **Tampering** | Intercepting HTTP request to alter student ID or timestamp. | Canonical payload signed with private key: `(challengeToken \| studentId \| driveId \| deviceId \| stationId \| timestamp)`. Any alteration invalidates ECDSA signature. | `CryptoVerificationService.java` |
| **Repudiation** | Student denies attending recruitment drive. | Cryptographic non-repudiation: signature stored in `attendance_records.signature_base64`; timestamp and audit logs in `audit_logs`. | `AttendanceService.java`, `AuditService.java` |
| **Information Disclosure** | Sniffing BLE broadcasts or logs leaking sensitive student biometrics. | BLE beacons emit zero student data (only venue beacon IDs); server never receives raw biometrics; audit exports strip keys/tokens. | `BleBeaconEncoder.cpp`, `ExcelReportService.java` |
| **Denial of Service** | Zip bomb uploaded via Excel roster, or flooding verification requests. | Apache POI ZipBomb limits (`setBiff8EncryptionKeyLimit`), pessimistic locking, rate limiting. | `ExcelSecurityConfig.java`, `RateLimitingFilter.java` |
| **Elevation of Privilege** | Student exporting reports or approving hardware devices. | Method-level Spring Security `@PreAuthorize("hasAuthority('ROLE_OFFICER')")` on all management routes. | `ReportController.java`, `DeviceController.java` |
