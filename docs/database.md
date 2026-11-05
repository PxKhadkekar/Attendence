# Database Schema & Entity Relationship Specification

## 1. Relational Model & Schema Design

The database schema is strictly normalized and managed using **Flyway** migrations.
All foreign keys have explicit cascade rules (`RESTRICT` or `CASCADE`), primary keys use `BIGINT UNSIGNED AUTO_INCREMENT` or `VARCHAR(64)` for UUIDs/tokens, and unique constraints prevent race conditions and duplicate check-ins.

---

## 2. Table Specifications (14 Tables)

### 2.1 `roles`
Stores application roles (`ROLE_OFFICER`, `ROLE_STUDENT`).
```sql
CREATE TABLE roles (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.2 `users`
Authentication credentials for Officers and Students.
```sql
CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE, -- Institutional email or officer username
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL UNIQUE,
    phone VARCHAR(32) NULL,
    role_id INT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.3 `students`
Master student record linked to institutional ID (Roll Number / USN).
```sql
CREATE TABLE students (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    student_id VARCHAR(64) NOT NULL UNIQUE, -- Official institutional roll/USN
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL UNIQUE,
    contact_number VARCHAR(32) NOT NULL,
    department VARCHAR(64) NOT NULL,
    batch_year INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_students_student_id (student_id),
    INDEX idx_students_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.4 `company_drives`
Recruitment drives organized by T&P.
```sql
CREATE TABLE company_drives (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(128) NOT NULL,
    drive_title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    drive_date DATE NOT NULL,
    venue VARCHAR(128) NOT NULL,
    status ENUM('DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'FINALIZED') NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT UNSIGNED NOT NULL,
    opened_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    finalized_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_company_drives_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_drives_status_date (status, drive_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.5 `drive_eligibility`
Authorized student eligibility list imported from Excel per drive.
```sql
CREATE TABLE drive_eligibility (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    import_job_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eligibility_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_eligibility_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT uq_drive_student UNIQUE (drive_id, student_id),
    INDEX idx_eligibility_drive (drive_id),
    INDEX idx_eligibility_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.6 `ble_stations`
Authorized BLE hardware stations deployed at drive venues.
```sql
CREATE TABLE ble_stations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(64) NOT NULL UNIQUE,
    venue_name VARCHAR(128) NOT NULL,
    proximity_uuid VARCHAR(64) NOT NULL, -- BLE Proximity UUID
    major_id INT UNSIGNED NOT NULL,
    minor_id INT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_station_uuid_major_minor (proximity_uuid, major_id, minor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.7 `student_devices`
Hardware-bound cryptographic devices registered by students and approved by officers.
```sql
CREATE TABLE student_devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT UNSIGNED NOT NULL,
    device_fingerprint VARCHAR(128) NOT NULL,
    device_model VARCHAR(128) NOT NULL,
    os_version VARCHAR(32) NOT NULL,
    public_key_pem TEXT NOT NULL, -- NIST P-256 (secp256r1) Public Key in X.509 PEM
    public_key_fingerprint VARCHAR(64) NOT NULL, -- SHA-256 hash of DER public key
    status ENUM('PENDING_APPROVAL', 'APPROVED', 'REVOKED') NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by BIGINT UNSIGNED NULL,
    approved_at TIMESTAMP NULL,
    revoked_by BIGINT UNSIGNED NULL,
    revoked_at TIMESTAMP NULL,
    revocation_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_devices_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_devices_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_devices_revoker FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_devices_student_status (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.8 `attendance_challenges`
Single-use, short-lived cryptographic nonces issued for an attendance session.
```sql
CREATE TABLE attendance_challenges (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    challenge_token VARCHAR(64) NOT NULL UNIQUE, -- Cryptographically secure random UUID/token
    drive_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    station_id BIGINT UNSIGNED NOT NULL,
    canonical_payload TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_challenge_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_device FOREIGN KEY (device_id) REFERENCES student_devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_station FOREIGN KEY (station_id) REFERENCES ble_stations(id) ON DELETE RESTRICT,
    INDEX idx_challenge_token (challenge_token),
    INDEX idx_challenge_expiry (expires_at, used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.9 `attendance_records`
Committed attendance records.
```sql
CREATE TABLE attendance_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    station_id BIGINT UNSIGNED NOT NULL,
    challenge_id BIGINT UNSIGNED NOT NULL UNIQUE,
    verification_status ENUM('VERIFIED', 'MANUAL_OVERRIDE') NOT NULL DEFAULT 'VERIFIED',
    signature_base64 TEXT NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_device FOREIGN KEY (device_id) REFERENCES student_devices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_station FOREIGN KEY (station_id) REFERENCES ble_stations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_challenge FOREIGN KEY (challenge_id) REFERENCES attendance_challenges(id) ON DELETE RESTRICT,
    CONSTRAINT uq_record_drive_student UNIQUE (drive_id, student_id), -- Absolute duplicate check-in prevention
    INDEX idx_records_drive (drive_id),
    INDEX idx_records_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.10 `attendance_attempts`
Comprehensive audit trail of all attendance attempts (successes and failures).
```sql
CREATE TABLE attendance_attempts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NULL,
    station_id BIGINT UNSIGNED NULL,
    outcome ENUM('SUCCESS', 'CHALLENGE_EXPIRED', 'SIGNATURE_INVALID', 'DEVICE_REVOKED', 'INELIGIBLE', 'DRIVE_CLOSED', 'ALREADY_MARKED', 'DISTANCE_EXCEEDED') NOT NULL,
    failure_reason VARCHAR(255) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempts_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_attempts_drive_outcome (drive_id, outcome),
    INDEX idx_attempts_time (attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.11 `attendance_corrections`
Audited manual modifications made by T&P Officers (e.g. for FINALIZED drives or hardware exception handling).
```sql
CREATE TABLE attendance_corrections (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    officer_id BIGINT UNSIGNED NOT NULL,
    previous_status ENUM('PRESENT', 'ABSENT') NOT NULL,
    new_status ENUM('PRESENT', 'ABSENT') NOT NULL,
    reason VARCHAR(500) NOT NULL, -- Mandatory justification
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_corrections_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE RESTRICT,
    CONSTRAINT fk_corrections_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_corrections_officer FOREIGN KEY (officer_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_corrections_drive (drive_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.12 `import_jobs`
Tracks Excel file upload jobs, processing statistics, and status.
```sql
CREATE TABLE import_jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_file_path VARCHAR(512) NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    total_rows INT UNSIGNED NOT NULL DEFAULT 0,
    valid_rows INT UNSIGNED NOT NULL DEFAULT 0,
    invalid_rows INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('UPLOADED', 'PREVIEWED', 'COMMITTED', 'FAILED') NOT NULL DEFAULT 'UPLOADED',
    error_summary TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_imports_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_imports_user FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_import_drive (drive_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.13 `import_errors`
Detailed row-level errors for rejected rows during Excel import.
```sql
CREATE TABLE import_errors (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    import_job_id BIGINT UNSIGNED NOT NULL,
    row_number INT UNSIGNED NOT NULL,
    student_id VARCHAR(64) NULL,
    student_name VARCHAR(128) NULL,
    email VARCHAR(128) NULL,
    contact_number VARCHAR(32) NULL,
    error_reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_errors_job FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE CASCADE,
    INDEX idx_errors_job (import_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.14 `audit_logs`
Global audit trail capturing every privileged action.
```sql
CREATE TABLE audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NULL,
    action VARCHAR(64) NOT NULL, -- e.g. DRIVE_OPENED, EXCEL_EXPORTED, DEVICE_REVOKED
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    details JSON NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_action_time (action, created_at),
    INDEX idx_audit_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 3. Flyway Migration Strategy
- `V1__init_auth_schema.sql`: roles, users.
- `V2__init_master_data.sql`: students, ble_stations.
- `V3__init_drives_and_import.sql`: company_drives, drive_eligibility, import_jobs, import_errors.
- `V4__init_devices_and_attendance.sql`: student_devices, attendance_challenges, attendance_records, attendance_attempts, attendance_corrections, audit_logs.
- `V5__seed_initial_roles_and_stations.sql`: Default roles (`ROLE_OFFICER`, `ROLE_STUDENT`) and default venue stations.
