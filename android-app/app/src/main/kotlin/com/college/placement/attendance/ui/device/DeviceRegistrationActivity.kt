package com.college.placement.attendance.ui.device

import com.college.placement.attendance.AttendanceApplication
import com.college.placement.attendance.model.Device
import com.college.placement.attendance.model.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Controller for Device Key Enrollment Screen.
 * Demonstrates hardware Keystore key enrollment and approval polling.
 */
class DeviceRegistrationActivity {

    private val viewModel = DeviceRegistrationViewModel(AttendanceApplication.deviceRepository)
    private val scope = CoroutineScope(Dispatchers.Main)

    var onDeviceEnrolled: ((Device) -> Unit)? = null
    var onStatusUpdated: ((DeviceStatus) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLoadingStateChanged: ((Boolean) -> Unit)? = null

    init {
        scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is DeviceUiState.Idle -> onLoadingStateChanged?.invoke(false)
                    is DeviceUiState.Enrolling -> onLoadingStateChanged?.invoke(true)
                    is DeviceUiState.Enrolled -> {
                        onLoadingStateChanged?.invoke(false)
                        onDeviceEnrolled?.invoke(state.device)
                    }
                    is DeviceUiState.StatusChecked -> {
                        onLoadingStateChanged?.invoke(false)
                        onStatusUpdated?.invoke(state.status)
                    }
                    is DeviceUiState.Error -> {
                        onLoadingStateChanged?.invoke(false)
                        onError?.invoke(state.message)
                    }
                }
            }
        }
    }

    fun enrollKey(rollNumber: String, deviceModel: String, osVersion: String) {
        viewModel.enrollDevice(rollNumber, deviceModel, osVersion)
    }

    fun checkStatus() {
        viewModel.refreshStatus()
    }
}
