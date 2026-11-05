package com.college.placement.attendance.ui.attendance

import com.college.placement.attendance.ble.BleStationScanner
import com.college.placement.attendance.model.AttendanceReceipt
import com.college.placement.attendance.model.StationBeacon
import com.college.placement.attendance.repository.AttendanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AttendanceUiState {
    object Idle : AttendanceUiState()
    data class Scanning(val message: String = "Scanning for station beacons...") : AttendanceUiState()
    data class StationFound(val beacon: StationBeacon) : AttendanceUiState()
    object Submitting : AttendanceUiState()
    data class Verified(val receipt: AttendanceReceipt) : AttendanceUiState()
    data class Error(val message: String) : AttendanceUiState()
}

class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val bleScanner: BleStationScanner,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _uiState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Idle)
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private var currentStation: StationBeacon? = null

    fun startBeaconScan() {
        _uiState.value = AttendanceUiState.Scanning()
        bleScanner.startScanning(
            onStationDiscovered = { beacon ->
                currentStation = beacon
                _uiState.value = AttendanceUiState.StationFound(beacon)
            },
            onError = { error ->
                _uiState.value = AttendanceUiState.Error(error)
            }
        )
    }

    fun stopBeaconScan() {
        bleScanner.stopScanning()
    }

    fun submitAttendance(driveId: Long, stationId: Long) {
        val beacon = currentStation
        if (beacon == null) {
            _uiState.value = AttendanceUiState.Error("No station beacon discovered yet.")
            return
        }

        if (!beacon.isWithinProximity) {
            _uiState.value = AttendanceUiState.Error(
                "Phone is outside required station proximity (Signal: ${beacon.rssi} dBm, required >= -75 dBm)."
            )
            return
        }

        _uiState.value = AttendanceUiState.Submitting
        scope.launch {
            try {
                val receipt = attendanceRepository.submitBiometricAttendance(
                    driveId = driveId,
                    stationBeacon = beacon,
                    stationId = stationId
                )
                _uiState.value = AttendanceUiState.Verified(receipt)
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState.Error(e.message ?: "Attendance verification failed")
            }
        }
    }
}
