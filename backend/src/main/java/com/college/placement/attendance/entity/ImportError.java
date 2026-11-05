package com.college.placement.attendance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "import_errors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJob importJob;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "student_id", length = 64)
    private String studentId;

    @Column(name = "student_name", length = 128)
    private String studentName;

    @Column(length = 128)
    private String email;

    @Column(name = "contact_number", length = 32)
    private String contactNumber;

    @Column(name = "error_reason", nullable = false, length = 500)
    private String errorReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
