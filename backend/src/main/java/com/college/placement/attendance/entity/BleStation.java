package com.college.placement.attendance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ble_stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BleStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", nullable = false, unique = true, length = 64)
    private String stationCode;

    @Column(name = "venue_name", nullable = false, length = 128)
    private String venueName;

    @Column(name = "proximity_uuid", nullable = false, length = 64)
    private String proximityUuid;

    @Column(name = "major_id", nullable = false)
    private Integer majorId;

    @Column(name = "minor_id", nullable = false)
    private Integer minorId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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
