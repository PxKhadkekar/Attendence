# REST API Specification (/api/v1)

## 1. Global Standards & Conventions
- **Base URL**: `/api/v1`
- **Content Type**: `application/json` (except file uploads which use `multipart/form-data`, and reports which use `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).
- **Authentication**: Bearer JWT in `Authorization: Bearer <token>` header.
- **Correlation ID**: Every request returns `X-Correlation-ID: <uuid>` in response headers for tracing.
- **DTOs Only**: Entity classes are never serialized directly across the API boundary.

---

## 2. Standard Error Response Model
```json
{
  "timestamp": "2026-10-29T12:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/attendance/verify",
  "correlationId": "8f87b8d8-6548-4e5a-8b89-2187321e1a5f",
  "validationErrors": [
    {
      "field": "signatureBase64",
      "message": "Signature cannot be blank"
    }
  ]
}
```

---

## 3. Endpoints Breakdown by Domain

### 3.1 Authentication & Profile (`/api/v1/auth`)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | Authenticates user credentials (email/username + password), returns JWT + refresh token. |
| POST | `/api/v1/auth/refresh` | Public | Refreshes expired access token using valid refresh token. |
| GET | `/api/v1/auth/me` | Authenticated | Retrieves current authenticated profile (id, name, email, role, student details if student). |

### 3.2 Recruitment Drives (`/api/v1/drives`)
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/drives` | OFFICER | Lists drives with pagination, filtering by date and status. |
| POST | `/api/v1/drives` | OFFICER | Creates a new drive in `DRAFT` state. |
| GET | `/api/v1/drives/{id}` | OFFICER / STUDENT | Gets drive details. Students can only see drives they are eligible for. |
| PUT | `/api/v1/drives/{id}` | OFFICER | Updates drive metadata (company, date, venue) while in `DRAFT` or `SCHEDULED`. |
| POST | `/api/v1/drives/{id}/schedule` | OFFICER | Transitions drive from `DRAFT` to `SCHEDULED` (requires confirmed eligible list). |
| POST | `/api/v1/drives/{id}/open` | OFFICER | Opens attendance window (`SCHEDULED` -> `OPEN` or `CLOSED` -> `OPEN`). |
| POST | `/api/v1/drives/{id}/close` | OFFICER | Closes attendance window (`OPEN` -> `CLOSED`). |
| POST | `/api/v1/drives/{id}/finalize` | OFFICER | Finalizes drive (`CLOSED` -> `FINALIZED`). Locks records against normal edits. |
| GET | `/api/v1/drives/my-eligible` | STUDENT | Returns list of upcoming and active drives where student is on eligibility list. |

### 3.3 Excel Eligibility Data Flow (`/api/v1/drives/{id}/imports`)
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/drives/import-template` | OFFICER | Downloads a clean, blank `.xlsx` template with validated headers and instructions. |
| POST | `/api/v1/drives/{id}/imports/upload` | OFFICER | Uploads `.xlsx` file, verifies MIME/magic bytes/zip security, runs row validations, and returns preview statistics + errors. |
| POST | `/api/v1/drives/{id}/imports/{jobId}/confirm` | OFFICER | Confirms the valid rows from preview; inserts into `drive_eligibility` in a single transaction. |
| GET | `/api/v1/drives/{id}/imports/{jobId}/rejected-rows` | OFFICER | Downloads `.xlsx` containing only the rejected rows with the added `Error Reason` column. |

### 3.4 Student Device Registration (`/api/v1/devices`)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/v1/devices/register` | STUDENT | Submits device hardware fingerprint, model, OS, and NIST P-256 public key (X.509 PEM). |
| GET | `/api/v1/devices/my-device` | STUDENT | Returns currently registered device and status (`PENDING_APPROVAL`, `APPROVED`, `REVOKED`). |
| GET | `/api/v1/devices/pending` | OFFICER | Lists devices awaiting officer approval. |
| POST | `/api/v1/devices/{id}/approve` | OFFICER | Approves device registration. |
| POST | `/api/v1/devices/{id}/revoke` | OFFICER | Revokes device key (requires mandatory revocation reason). |

### 3.5 BLE Hardware Stations (`/api/v1/stations`)
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/stations` | OFFICER / STUDENT | Lists active BLE stations, UUIDs, Major/Minor IDs, and venue mappings. |
| POST | `/api/v1/stations` | OFFICER | Registers new ESP32 station. |
| PUT | `/api/v1/stations/{id}/status` | OFFICER | Activates or deactivates a station. |

### 3.6 Attendance Verification Protocol (`/api/v1/attendance`)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/v1/attendance/sessions` | STUDENT | Requests a single-use attendance challenge. Verifies student eligibility, drive status=`OPEN`, device=`APPROVED`, station=`ACTIVE`, and no duplicate record. Returns challenge token and canonical template. |
| POST | `/api/v1/attendance/verify` | STUDENT | Submits signed challenge. Verifies ECDSA signature with registered public key, consumes challenge, inserts attendance record in an atomic transaction. |
| GET | `/api/v1/attendance/history` | STUDENT | Returns authenticated student's own attendance history across drives. |

### 3.7 Live Attendance & Dashboard (`/api/v1/drives/{id}/attendance`)
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/drives/{id}/attendance/stream` | OFFICER | Server-Sent Events (SSE) streaming live committed check-ins to dashboard. |
| GET | `/api/v1/drives/{id}/attendance/summary` | OFFICER | Returns aggregated counts: Total Eligible, Present, Absent, Attendance Percentage. |
| GET | `/api/v1/drives/{id}/attendance/records` | OFFICER | Paginated & searchable list of present/absent students for the drive. |
| POST | `/api/v1/drives/{id}/attendance/corrections` | OFFICER | Audited manual override for a student (requires `studentId`, `newStatus`, `reason`). |

### 3.8 Reports & Audit Logs (`/api/v1/reports` & `/api/v1/audit`)
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/reports/drives/{id}/export` | OFFICER | Generates and streams `.xlsx` workbook using SXSSFWorkbook (Summary, Present, Absent, All Attendance, and optional Verification Audit). Escapes formula characters. |
| GET | `/api/v1/audit/logs` | OFFICER | Paginated search of system audit events with filter by action, date range, and actor. |
