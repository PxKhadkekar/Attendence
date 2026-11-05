package com.college.placement.attendance.controller;

import com.college.placement.attendance.dto.station.BleStationDto;
import com.college.placement.attendance.dto.station.CreateBleStationRequest;
import com.college.placement.attendance.security.UserPrincipal;
import com.college.placement.attendance.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
@Tag(name = "BLE Hardware Stations", description = "Management of venue BLE proximity beacon stations")
public class StationController {

    private final StationService stationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Register new BLE hardware beacon station")
    public ResponseEntity<BleStationDto> createStation(
            @Valid @RequestBody CreateBleStationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        BleStationDto response = stationService.createStation(request, currentUser, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all active BLE stations")
    public ResponseEntity<List<BleStationDto>> listActiveStations() {
        return ResponseEntity.ok(stationService.listActiveStations());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_OFFICER')")
    @Operation(summary = "Enable or disable a BLE hardware station")
    public ResponseEntity<BleStationDto> updateStationStatus(
            @PathVariable Long id,
            @RequestParam boolean active,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(stationService.updateStationStatus(id, active, currentUser, httpRequest));
    }
}
