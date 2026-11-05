package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.BleStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BleStationRepository extends JpaRepository<BleStation, Long> {
    Optional<BleStation> findByStationCode(String stationCode);
    Optional<BleStation> findByProximityUuidAndMajorIdAndMinorId(String proximityUuid, Integer majorId, Integer minorId);
    List<BleStation> findByIsActiveTrue();
}
