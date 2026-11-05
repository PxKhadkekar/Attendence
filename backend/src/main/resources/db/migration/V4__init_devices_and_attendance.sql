-- V4__init_devices_and_attendance.sql: Devices, Challenges, Records, Corrections, Audit

CREATE TABLE IF NOT EXISTS student_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    device_fingerprint VARCHAR(128) NOT NULL,
    device_model VARCHAR(128) NOT NULL,
    os_version VARCHAR(32) NOT NULL,
    public_key_pem TEXT NOT NULL,
    public_key_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by BIGINT NULL,
    approved_at TIMESTAMP NULL,
    revoked_by BIGINT NULL,
    revoked_at TIMESTAMP NULL,
    revocation_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_devices_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_devices_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_devices_revoker FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_devices_student_status ON student_devices(student_id, status);

CREATE TABLE IF NOT EXISTS attendance_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_token VARCHAR(64) NOT NULL UNIQUE,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    canonical_payload TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_challenge_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_device FOREIGN KEY (device_id) REFERENCES student_devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_station FOREIGN KEY (station_id) REFERENCES ble_stations(id) ON DELETE RESTRICT
);

CREATE INDEX idx_challenge_token ON attendance_challenges(challenge_token);
CREATE INDEX idx_challenge_expiry ON attendance_challenges(expires_at, used);

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    station_id BIGINT NULL,
    challenge_id BIGINT NULL UNIQUE,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED',
    signature_base64 TEXT NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_device FOREIGN KEY (device_id) REFERENCES student_devices(id) ON DELETE SET NULL,
    CONSTRAINT fk_records_station FOREIGN KEY (station_id) REFERENCES ble_stations(id) ON DELETE SET NULL,
    CONSTRAINT fk_records_challenge FOREIGN KEY (challenge_id) REFERENCES attendance_challenges(id) ON DELETE SET NULL,
    CONSTRAINT uq_record_drive_student UNIQUE (drive_id, student_id)
);

CREATE INDEX idx_records_drive ON attendance_records(drive_id);
CREATE INDEX idx_records_student ON attendance_records(student_id);

CREATE TABLE IF NOT EXISTS attendance_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    station_id BIGINT NULL,
    outcome VARCHAR(64) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempts_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);

CREATE INDEX idx_attempts_drive_outcome ON attendance_attempts(drive_id, outcome);
CREATE INDEX idx_attempts_time ON attendance_attempts(attempted_at);

CREATE TABLE IF NOT EXISTS attendance_corrections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    previous_status VARCHAR(16) NOT NULL,
    new_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_corrections_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE RESTRICT,
    CONSTRAINT fk_corrections_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_corrections_officer FOREIGN KEY (officer_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_corrections_drive ON attendance_corrections(drive_id);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    details TEXT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_action_time ON audit_logs(action, created_at);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
