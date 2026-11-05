# Requirements Traceability Matrix (RTM)

This document maps all architectural, functional, cryptographic, and operational requirements of the **Zero-Trust Biometric & BLE Placement Drive Attendance System** to their respective implementation source files, database entities, and automated verification tests.

---

## 1. System Core Architecture & Boundaries

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-CORE-01** | Microservice/Modular Monolith architecture with Spring Boot 3 backend, React 19 portal, Android mobile client, and ESP32 BLE beacon firmware. | `backend/`, `frontend/`, `android-app/`, `ble-firmware/` | All 14 tables | `FullSystemLifecycleE2ETest.java` |
| **REQ-CORE-02** | Strict Zero-Trust Proximity Model: BLE radio proximity verifies venue presence only, never identity; Android Keystore ECDSA verifies authorized device participation. | `BlePacketParser.kt`, `CryptoManager.kt`, `AttendanceService.java` | `ble_stations`, `student_devices`, `attendance_challenges` | `MockBleStationScannerSecurityTest.kt`, `FullSystemLifecycleE2ETest.java` |
| **REQ-CORE-03** | Zero biometric data transmission over the wire (no raw biometric templates or boolean "biometric passed" flags transmitted). | `CryptoManager.kt`, `AttendanceVerifyRequest.java`, `CryptoVerificationService.java` | `attendance_records` (stores `signature_base64`) | `CryptoManagerTest.kt`, `AttendanceVerificationIntegrationTest.java` |

---

## 2. Authentication & Authorization (RBAC)

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-AUTH-01** | Role-Based Access Control distinguishing T&P Officers (`ROLE_OFFICER`) from Students (`ROLE_STUDENT`). | `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `UserPrincipal.java` | `users`, `roles` | `AuthIntegrationTest.java` |
| **REQ-AUTH-02** | Secure JWT token issuance with short-lived access tokens and refresh token rotation. | `JwtService.java`, `AuthController.java` | `users` | `AuthIntegrationTest.java` |
| **REQ-AUTH-03** | End-to-end request correlation IDs (`X-Correlation-ID`) across HTTP logs and structured JSON errors. | `CorrelationIdFilter.java`, `GlobalExceptionHandler.java` | `audit_logs` | `FullSystemLifecycleE2ETest.java` |

---

## 3. Recruitment Drive Lifecycle State Machine

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-DRV-01** | Placement drive lifecycle transitions strictly through: `DRAFT` $\rightarrow$ `SCHEDULED` $\rightarrow$ `OPEN` $\rightarrow$ `CLOSED`. | `DriveService.java`, `DriveController.java`, `CompanyDrive.java` | `company_drives` | `DriveLifecycleTest.java` |
| **REQ-DRV-02** | Illegal state transitions (e.g. `DRAFT` $\rightarrow$ `OPEN` without scheduling, or modifying a `CLOSED` drive) must fail with HTTP 409 Conflict. | `DriveService.java`, `InvalidStateTransitionException.java` | `company_drives` | `DriveLifecycleTest.java` |
| **REQ-DRV-03** | Real-time live attendance streaming to Officer Portal via Server-Sent Events (SSE). | `AttendanceSseService.java`, `LiveAttendanceController.java` | N/A (In-memory `SseEmitter`) | `AttendanceVerificationIntegrationTest.java` |

---

## 4. Excel Import & Export Pipeline

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-XLS-01** | Strict OOXML validation (reject `.xls`, fake mimes, or corrupt zip files) and Apache POI ZipBomb denial-of-service defense. | `ExcelSecurityConfig.java`, `ExcelImportService.java` | N/A | `ExcelImportSecurityTest.java` |
| **REQ-XLS-02** | Two-phase eligibility roster import: preview valid/invalid rows followed by atomic batch commit. | `ExcelImportService.java`, `DriveImportController.java` | `import_jobs`, `import_errors`, `drive_eligibility` | `ExcelImportSecurityTest.java` |
| **REQ-XLS-03** | Rejected row download producing `.xlsx` file containing solely invalid rows annotated with detailed error reasons. | `RejectedRowsExportService.java`, `DriveImportController.java` | `import_errors` | `ExcelImportSecurityTest.java` |
| **REQ-XLS-04** | Anti-Formula Injection protection prefixing dangerous characters (`=`, `+`, `-`, `@`, `\t`, `\r`) with single quotes during export and parsing. | `FormulaSanitizer.java`, `ExcelReportService.java` | N/A | `ExcelExportTest.java`, `FullSystemLifecycleE2ETest.java` |
| **REQ-XLS-05** | High-performance streaming SXSSFWorkbook multi-sheet report (`Summary`, `Present`, `Absent`, `All Attendance`, `Verification Audit`) maintaining mathematical count parity. | `ExcelReportService.java`, `ReportController.java` | `attendance_records`, `drive_eligibility`, `verification_attempts` | `ExcelExportTest.java`, `FullSystemLifecycleE2ETest.java` |

---

## 5. Device Hardware Binding & Keystore Cryptography

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-DEV-01** | Hardware-backed NIST P-256 (`secp256r1`) keypair generation inside Android Keystore (`AndroidKeyStore`). | `CryptoManager.kt` | N/A (Secure Element) | `CryptoManagerTest.kt` |
| **REQ-DEV-02** | Device registration requiring T&P Officer approval before the device can be utilized for verification. | `DeviceService.java`, `DeviceController.java` | `student_devices` | `AttendanceVerificationIntegrationTest.java`, `FullSystemLifecycleE2ETest.java` |
| **REQ-DEV-03** | Single active device policy: registering a new device marks previous devices as `PENDING_APPROVAL` or revoked. | `DeviceService.java` | `student_devices` | `AttendanceVerificationIntegrationTest.java` |

---

## 6. Verification Engine & Replay Defense

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-VER-01** | Single-use 60-second challenge nonces generated with cryptographically secure random bytes. | `AttendanceService.java`, `AttendanceChallenge.java` | `attendance_challenges` | `AttendanceVerificationIntegrationTest.java` |
| **REQ-VER-02** | Canonical message formatting: `challengeToken \| studentId \| driveId \| deviceId \| stationId \| timestamp`. | `CryptoVerificationService.java` | N/A | `AttendanceVerificationIntegrationTest.java` |
| **REQ-VER-03** | Asymmetric signature verification using device enrolled Public Key PEM (`SHA256withECDSA`). | `CryptoVerificationService.java` | `student_devices` | `AttendanceVerificationIntegrationTest.java`, `FullSystemLifecycleE2ETest.java` |
| **REQ-VER-04** | Concurrency control via database pessimistic write locking (`SELECT ... FOR UPDATE`) preventing race conditions during verification. | `AttendanceChallengeRepository.java`, `AttendanceService.java` | `attendance_challenges` | `AttendanceVerificationIntegrationTest.java` |
| **REQ-VER-05** | Strict replay attack prevention: consumed challenges reject re-submissions immediately with HTTP 409 Conflict. | `AttendanceService.java` | `attendance_challenges` | `FullSystemLifecycleE2ETest.java` |

---

## 7. BLE Hardware Station & Firmware

| Requirement ID | Requirement Description | Implementation Components | Database Tables | Automated Verification Tests |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-BLE-01** | ESP32 BLE Beacon firmware transmitting 30-byte Apple iBeacon advertisement frames at 100ms intervals. | `BleBeaconEncoder.cpp`, `main.cpp` | `ble_stations` | `test_beacon_encoder.cpp` |
| **REQ-BLE-02** | Serial CLI configuration for runtime beacon parameters stored persistently in Non-Volatile Storage (NVS). | `main.cpp`, `BeaconConfig.h` | N/A (ESP32 Flash) | Firmware manual console tests |
| **REQ-BLE-03** | Calibrated RSSI threshold filtering ($RSSI \ge -75 \text{ dBm}$) ensuring student presence within the venue perimeter. | `BlePacketParser.kt`, `AndroidBleStationScanner.kt` | `ble_stations` | `BlePacketParserTest.kt` |
| **REQ-BLE-04** | Hard security lock prohibiting mock BLE scanners in release/production builds (`BuildConfig.DEBUG`). | `MockBleStationScanner.kt` | N/A | `MockBleStationScannerSecurityTest.kt` |

---

## 8. Frontend Officer Portal

| Requirement ID | Requirement Description | Implementation Components | Verification Tests |
| :--- | :--- | :--- | :--- |
| **REQ-FE-01** | Modern, responsive Officer Portal with drive creation, scheduling, and closing workflows. | `DriveListPage.tsx`, `DriveDetailsPage.tsx`, `CreateDriveModal.tsx` | `vitest run` (clean suite) |
| **REQ-FE-02** | Real-time live attendance feed updating dynamically via SSE. | `LiveAttendancePage.tsx`, `useAttendanceStream.ts` | Manual browser test & Vitest |
| **REQ-FE-03** | Student hardware device approval grid with 1-click authorization. | `DeviceApprovalsPage.tsx` | Vitest suite |
| **REQ-FE-04** | Status badge visual indicators conforming to college design tokens. | `StatusBadge.tsx` | `StatusBadge.test.tsx` (5/5 passed) |

---

## 9. Deployment, Containerization & Operational Hardening

| Requirement ID | Requirement Description | Implementation Components | Verification Status |
| :--- | :--- | :--- | :--- |
| **REQ-OPS-01** | Multi-stage Dockerfile for Spring Boot backend using Eclipse Temurin 21 Alpine and running as non-root user `spring:spring`. | `backend/Dockerfile` | Validated multi-stage spec |
| **REQ-OPS-02** | Multi-stage Dockerfile for React frontend using Node 20 builder and Alpine Nginx server with SPA routing and SSE proxying. | `frontend/Dockerfile`, `frontend/nginx.conf` | Validated Nginx config |
| **REQ-OPS-03** | Single-command Docker Compose orchestration binding MySQL 8.0, Backend, and Frontend with explicit healthchecks and dependencies. | `docker-compose.yml`, `.env.example` | Validated Compose file |
| **REQ-OPS-04** | Automated 11-step end-to-end integration test exercising the full system lifecycle without external mocks. | `FullSystemLifecycleE2ETest.java` | **100% Passed (BUILD SUCCESS)** |
