-- V5__seed_initial_roles_and_stations.sql: Seed Roles, Officer User, Students, and Stations

INSERT INTO roles (id, name, description) VALUES
(1, 'ROLE_OFFICER', 'Training and Placement Officer'),
(2, 'ROLE_STUDENT', 'Eligible Student');

-- Default Officer User (Password: Officer@123)
INSERT INTO users (id, username, password_hash, full_name, email, phone, role_id, is_active) VALUES
(1, 'officer@college.edu', '$2b$10$d3xnnF/k6GSFhmm/FvbZuO4n7zPow9p7BHqe1JYXfPLC7G50BFTbC', 'T&P Officer Admin', 'officer@college.edu', '+91-9876543210', 1, TRUE);

-- Master Student Users (Password: Student@123)
INSERT INTO users (id, username, password_hash, full_name, email, phone, role_id, is_active) VALUES
(2, 'student1@college.edu', '$2b$10$YMeT39rQ5P2zZTQC5WL1COsH3FA6gKy036TC51hxI6MeUcg2Fu5B6', 'Aarav Sharma', 'aarav.sharma@college.edu', '+91-9876500001', 2, TRUE),
(3, 'student2@college.edu', '$2b$10$YMeT39rQ5P2zZTQC5WL1COsH3FA6gKy036TC51hxI6MeUcg2Fu5B6', 'Diya Patel', 'diya.patel@college.edu', '+91-9876500002', 2, TRUE),
(4, 'student3@college.edu', '$2b$10$YMeT39rQ5P2zZTQC5WL1COsH3FA6gKy036TC51hxI6MeUcg2Fu5B6', 'Rohan Mehta', 'rohan.mehta@college.edu', '+91-9876500003', 2, TRUE);

-- Master Student Records
INSERT INTO students (id, user_id, student_id, full_name, email, contact_number, department, batch_year) VALUES
(1, 2, 'STU2026001', 'Aarav Sharma', 'aarav.sharma@college.edu', '+91-9876500001', 'Computer Science and Engineering', 2026),
(2, 3, 'STU2026002', 'Diya Patel', 'diya.patel@college.edu', '+91-9876500002', 'Information Technology', 2026),
(3, 4, 'STU2026003', 'Rohan Mehta', 'rohan.mehta@college.edu', '+91-9876500003', 'Electronics and Communication', 2026);

-- Authorized BLE Hardware Stations
INSERT INTO ble_stations (id, station_code, venue_name, proximity_uuid, major_id, minor_id, is_active) VALUES
(1, 'STATION-AUD-01', 'Auditorium Main Stage', '0000FD5A-0000-1000-8000-00805F9B34FB', 101, 1, TRUE),
(2, 'STATION-LAB-02', 'T&P Seminar Hall 2', '0000FD5A-0000-1000-8000-00805F9B34FB', 102, 1, TRUE);
