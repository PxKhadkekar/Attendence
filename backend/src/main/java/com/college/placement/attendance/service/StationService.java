package com.college.placement.attendance.service;

import com.college.placement.attendance.dto.station.BleStationDto;
import com.college.placement.attendance.dto.station.CreateBleStationRequest;
import com.college.placement.attendance.entity.BleStation;
import com.college.placement.attendance.exception.ConflictException;
import com.college.placement.attendance.exception.ResourceNotFoundException;
import com.college.placement.attendance.repository.BleStationRepository;
import com.college.placement.attendance.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationService {

    private final BleStationRepository stationRepository;
    private final AuditService auditService;

    @Transactional
    public BleStationDto createStation(CreateBleStationRequest request, UserPrincipal officer, HttpServletRequest httpRequest) {
        if (stationRepository.findByStationCode(request.getStationCode()).isPresent()) {
            throw new ConflictException("Station code '" + request.getStationCode() + "' is already registered.");
        }

        BleStation station = BleStation.builder()
                .stationCode(request.getStationCode().trim())
                .venueName(request.getVenueName().trim())
                .proximityUuid(request.getProximityUuid().trim())
                .majorId(request.getMajorId())
                .minorId(request.getMinorId())
                .isActive(true)
                .build();

        BleStation saved = stationRepository.save(station);

        auditService.recordAction(officer.getId(), "STATION_CREATED", "STATION", saved.getId().toString(),
                "Registered BLE station " + saved.getStationCode() + " at " + saved.getVenueName(), httpRequest);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BleStationDto> listActiveStations() {
        return stationRepository.findByIsActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BleStationDto> listAllStations() {
        return stationRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public BleStationDto updateStationStatus(Long id, boolean active, UserPrincipal officer, HttpServletRequest httpRequest) {
        BleStation station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + id));

        station.setIsActive(active);
        BleStation updated = stationRepository.save(station);

        auditService.recordAction(officer.getId(), "STATION_STATUS_UPDATED", "STATION", id.toString(),
                "Set station active status to " + active, httpRequest);

        return toDto(updated);
    }

    private BleStationDto toDto(BleStation s) {
        return BleStationDto.builder()
                .id(s.getId())
                .stationCode(s.getStationCode())
                .venueName(s.getVenueName())
                .proximityUuid(s.getProximityUuid())
                .majorId(s.getMajorId())
                .minorId(s.getMinorId())
                .isActive(s.getIsActive())
                .build();
    }
}
