package com.college.placement.attendance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "student_id", nullable = false, unique = true, length = 64)
    private String studentId; // Institutional Roll Number / USN

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(name = "contact_number", nullable = false, length = 32)
    private String contactNumber;

    @Column(nullable = false, length = 64)
    private String department;

    @Column(name = "batch_year", nullable = false)
    private Integer batchYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
