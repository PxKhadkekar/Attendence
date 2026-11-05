-- V3__init_drives_and_import.sql: Drives, Eligibility, and Import Tracking

CREATE TABLE IF NOT EXISTS company_drives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(128) NOT NULL,
    drive_title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    drive_date DATE NOT NULL,
    venue VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    opened_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    finalized_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_company_drives_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_drives_status_date ON company_drives(status, drive_date);

CREATE TABLE IF NOT EXISTS import_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_file_path VARCHAR(512) NULL,
    file_size_bytes BIGINT NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    error_summary TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_imports_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_imports_user FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_import_drive ON import_jobs(drive_id);

CREATE TABLE IF NOT EXISTS import_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    import_job_id BIGINT NOT NULL,
    row_number INT NOT NULL,
    student_id VARCHAR(64) NULL,
    student_name VARCHAR(128) NULL,
    email VARCHAR(128) NULL,
    contact_number VARCHAR(32) NULL,
    error_reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_errors_job FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_errors_job ON import_errors(import_job_id);

CREATE TABLE IF NOT EXISTS drive_eligibility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    import_job_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eligibility_drive FOREIGN KEY (drive_id) REFERENCES company_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_eligibility_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT uq_drive_student UNIQUE (drive_id, student_id),
    CONSTRAINT fk_eligibility_job FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE SET NULL
);

CREATE INDEX idx_eligibility_drive ON drive_eligibility(drive_id);
CREATE INDEX idx_eligibility_student ON drive_eligibility(student_id);
