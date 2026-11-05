package com.college.placement.attendance.ui.device

import com.college.placement.attendance.model.Device
import com.college.placement.attendance.model.DeviceStatus
import com.college.placement.attendance.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DeviceUiState {
    object Idle : DeviceUiState()
    object Enrolling : DeviceUiState()
    data class Enrolled(val device: Device) : DeviceUiState()
    data class StatusChecked(val status: DeviceStatus) : DeviceUiState()
    data class Error(val message: String) : DeviceUiState()
}

class DeviceRegistrationViewModel(
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _uiState = MutableStateFlow<DeviceUiState>(DeviceUiState.Idle)
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    fun enrollDevice(rollNumber: String, deviceModel: String, osVersion: String) {
        _uiState.value = DeviceUiState.Enrolling
        scope.launch {
            try {
                val device = deviceRepository.getOrEnrollDevice(rollNumber, deviceModel, osVersion)
                _uiState.value = DeviceUiState.Enrolled(device)
            } catch (e: Exception) {
                _uiState.value = DeviceUiState.Error(e.message ?: "Device key enrollment failed")
            }
        }
    }

    fun refreshStatus() {
        scope.launch {
            try {
                val status = deviceRepository.checkDeviceStatus()
                _uiState.value = DeviceUiState.StatusChecked(status)
            } catch (e: Exception) {
                _uiState.value = DeviceUiState.Error(e.message ?: "Failed to check status")
            }
        }
    }
}
