package com.college.placement.attendance.ui.attendance

import com.college.placement.attendance.AttendanceApplication
import com.college.placement.attendance.model.AttendanceReceipt
import com.college.placement.attendance.model.StationBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Controller for Attendance Verification Screen.
 * Demonstrates BLE station detection, proximity thresholding, biometric prompt triggers,
 * and attendance confirmation.
 */
class AttendanceActivity(
    useDevMock: Boolean = false
) {
    private val scanner = AttendanceApplication.createBleScanner(useDevMock)
    private val viewModel = AttendanceViewModel(AttendanceApplication.attendanceRepository, scanner)
    private val scope = CoroutineScope(Dispatchers.Main)

    var onScanningStatusChanged: ((String) -> Unit)? = null
    var onStationDiscovered: ((StationBeacon) -> Unit)? = null
    var onAttendanceVerified: ((AttendanceReceipt) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLoadingStateChanged: ((Boolean) -> Unit)? = null

    init {
        scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is AttendanceUiState.Idle -> onLoadingStateChanged?.invoke(false)
                    is AttendanceUiState.Scanning -> {
                        onLoadingStateChanged?.invoke(false)
                        onScanningStatusChanged?.invoke(state.message)
                    }
                    is AttendanceUiState.StationFound -> {
                        onLoadingStateChanged?.invoke(false)
                        onStationDiscovered?.invoke(state.beacon)
                    }
                    is AttendanceUiState.Submitting -> onLoadingStateChanged?.invoke(true)
                    is AttendanceUiState.Verified -> {
                        onLoadingStateChanged?.invoke(false)
                        onAttendanceVerified?.invoke(state.receipt)
                    }
                    is AttendanceUiState.Error -> {
                        onLoadingStateChanged?.invoke(false)
                        onError?.invoke(state.message)
                    }
                }
            }
        }
    }

    fun startScanning() {
        viewModel.startBeaconScan()
    }

    fun stopScanning() {
        viewModel.stopBeaconScan()
    }

    fun markAttendance(driveId: Long, stationId: Long) {
        viewModel.submitAttendance(driveId, stationId)
    }
}
