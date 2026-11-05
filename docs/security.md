# Threat Model & Security Architecture

## 1. Explicit Security Disclaimers & Limits of Technology

> [!CAUTION]
> ### Critical Architectural Assumptions & Boundaries
> 1. **BLE Is Not Identity Proof**:
>    Bluetooth Low Energy (BLE) transmissions simply indicate that an authorized hardware station's radio signal is within reception range. BLE signals can be intercepted, captured, or relayed across remote networks (e.g., via SDR or internet tunnels). BLE proximity confirms venue co-presence only under non-adversarial radio conditions and must never be treated as identity verification.
> 2. **Local Biometrics Do Not Prove Real-World Identity**:
>    On-device biometric verification (Fingerprint / Face Unlock via `BiometricPrompt`) proves only that a user authorized on that specific phone unlocked the Android Keystore private key. It does not prove *which* human presented their biometric if multiple fingerprints are enrolled on the device. True student binding depends on the initial registration ceremony and device trust.
> 3. **Hardware Root of Trust**:
>    The system relies on Android Keystore hardware-backed keys (TEE or StrongBox). On compromised/rooted devices with patched kernels, hardware guarantees may be broken; therefore, SafetyNet/Play Integrity checks and rooted device detection form part of defense-in-depth.

---

## 2. Threat Model (STRIDE Matrix)

| Threat Category | Specific Threat Vector | Impact | Mitigation Strategy |
|---|---|---|---|
| **Spoofing** | Student impersonates another student via stolen credentials. | Unauthorized attendance recorded. | Attendance requires cryptographic signature from the specific private key bound to the approved student device. Knowing login credentials alone cannot produce a valid check-in. |
| **Spoofing** | Rogue BLE beacon broadcasting college UUID. | Trick phone into thinking it is at venue. | Backend strictly verifies that the drive is `OPEN`, checks student eligibility, and verifies station status. Even if BLE is spoofed, attendance cannot be submitted without an active open drive. |
| **Tampering** | Student modifies challenge payload or timestamp. | Attendance recorded for wrong drive or invalid time. | Android Keystore signs the canonical string containing `(challengeToken, studentId, driveId, deviceId, stationId, timestamp)`. Any alteration invalidates the ECDSA signature verification on the server. |
| **Tampering** | Attacker injects spreadsheet formulas (`=cmd|' /C ...'!A0`) in student lists. | Remote code execution or data exfiltration on officer workstation when opening `.xlsx`. | 1) Input sanitization: cell values starting with `=`, `+`, `-`, `@` are treated strictly as plain text on import. 2) Output escaping: every exported string cell starting with dangerous characters is escaped with a leading single quote (`'`). |
| **Repudiation** | Student claims they never checked in, or Officer falsely claims student was absent. | Disputed attendance record. | Cryptographic signature of check-in stored permanently in `attendance_records.signature_base64`. Every manual officer correction creates an immutable record in `attendance_corrections` with mandatory reason. |
| **Information Disclosure** | Student accesses drive attendance list or another student's history (IDOR). | Student privacy violation. | Spring Security enforces RBAC. Student endpoints only accept `Authentication.getPrincipal()` to access own data; IDOR attempts with arbitrary `studentId` are rejected with 403 Forbidden. |
| **Denial of Service** | Malicious officer or attacker uploads 50MB zip bomb Excel file. | Backend JVM OutOfMemoryError / CPU starvation. | 1) File size hard limit (5 MB). 2) Apache POI `ZipSecureFile.setMinInflateRatio(0.01)` and `setMaxEntrySize(50000000)`. 3) Row limit capped at 5,000 rows. Streaming SXSSF parser avoids full DOM in-memory inflation. |
| **Elevation of Privilege** | Student registers device without authorization or bypasses officer approval. | Unapproved device marks attendance. | Newly registered devices enter `PENDING_APPROVAL` status. Challenges are only issued to devices with `APPROVED` status. Officer review is logged in `audit_logs`. |

---

## 3. Cryptographic Specification & Key Binding

1. **Key Generation Parameters**:
   - Algorithm: `KeyProperties.KEY_ALGORITHM_EC`
   - Curve: NIST P-256 (`secp256r1`)
   - Digest: `DIGEST_SHA256`
   - Purpose: `KeyProperties.PURPOSE_SIGN`
   - Hardware Binding: `setUserAuthenticationRequired(true)`
   - Authentication Type: `AUTH_BIOMETRIC_STRONG`
   - Timeout: Single-use (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`) requiring fresh biometric presentation per signature.
2. **Canonical Payload Construction**:
   ```
   CANONICAL_MESSAGE = challengeToken + "|" + studentId + "|" + driveId + "|" + deviceId + "|" + stationId + "|" + timestampEpochSec
   ```
3. **Backend Signature Verification**:
   - Backend loads student's registered public key from `student_devices.public_key_pem`.
   - Algorithm: `SHA256withECDSA` (DER format).
   - Validation ensures mathematical proof of possession of the private key protected by the Android Keystore.

---

## 4. Race Condition & Concurrency Defense
- **Single Transaction Guarantee**:
  Challenge validation, challenge consumption (`used = true`), and attendance record creation (`INSERT INTO attendance_records`) occur within a single database transaction with pessimistic locking (`SELECT ... FOR UPDATE` on `attendance_challenges`).
- **Unique Constraint Safety Net**:
  `UNIQUE(drive_id, student_id)` on `attendance_records` guarantees that even in concurrent multi-threaded requests, only one insert will succeed; the concurrent duplicate triggers a `DataIntegrityViolationException` and returns `409 CONFLICT` without corrupting state.

---

## 5. Device Accessibility & Hardware Exception Workflow
If a student uses an unsupported device, has disabled biometric hardware, or experiences hardware failure:
1. **No Silent Fallback**: The student app *never* silently falls back to password-only check-in.
2. **Physical Desk Verification**: The student presents their physical college photo ID card at the designated T&P Help Desk.
3. **Audited Officer Override**: The T&P Officer uses the web portal's audited correction flow (`POST /api/v1/drives/{id}/attendance/corrections`) to record attendance with `newStatus: PRESENT`, `verificationStatus: MANUAL_OVERRIDE`, and mandatory reason: `"Physical ID verified at Help Desk due to hardware disability / failure"`.
