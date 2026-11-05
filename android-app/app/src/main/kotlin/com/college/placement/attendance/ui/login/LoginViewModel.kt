package com.college.placement.attendance.ui.login

import com.college.placement.attendance.api.dto.AuthResponse
import com.college.placement.attendance.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val auth: AuthResponse) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(usernameOrRoll: String, password: String) {
        if (usernameOrRoll.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter your email/roll number and password.")
            return
        }

        _uiState.value = LoginUiState.Loading
        scope.launch {
            try {
                val auth = authRepository.login(usernameOrRoll.trim(), password)
                _uiState.value = LoginUiState.Success(auth)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }
}
