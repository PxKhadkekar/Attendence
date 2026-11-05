-- V2__init_master_data.sql: Master Students and BLE Stations

CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    student_id VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL UNIQUE,
    contact_number VARCHAR(32) NOT NULL,
    department VARCHAR(64) NOT NULL,
    batch_year INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_students_student_id ON students(student_id);
CREATE INDEX idx_students_email ON students(email);

CREATE TABLE IF NOT EXISTS ble_stations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(64) NOT NULL UNIQUE,
    venue_name VARCHAR(128) NOT NULL,
    proximity_uuid VARCHAR(64) NOT NULL,
    major_id INT NOT NULL,
    minor_id INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_station_uuid_major_minor ON ble_stations(proximity_uuid, major_id, minor_id);
