package com.college.placement.attendance.ui.login

import com.college.placement.attendance.AttendanceApplication
import com.college.placement.attendance.api.dto.AuthResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Controller for Student Login Screen.
 * Demonstrates MVVM interaction, auth state handling, and flow transition.
 */
class LoginActivity {

    private val viewModel = LoginViewModel(AttendanceApplication.authRepository)
    private val scope = CoroutineScope(Dispatchers.Main)

    // Callback handlers for UI interactions
    var onLoginSuccess: ((AuthResponse) -> Unit)? = null
    var onLoginError: ((String) -> Unit)? = null
    var onLoadingStateChanged: ((Boolean) -> Unit)? = null

    init {
        scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is LoginUiState.Idle -> onLoadingStateChanged?.invoke(false)
                    is LoginUiState.Loading -> onLoadingStateChanged?.invoke(true)
                    is LoginUiState.Success -> {
                        onLoadingStateChanged?.invoke(false)
                        onLoginSuccess?.invoke(state.auth)
                    }
                    is LoginUiState.Error -> {
                        onLoadingStateChanged?.invoke(false)
                        onLoginError?.invoke(state.message)
                    }
                }
            }
        }
    }

    fun submitLogin(identifier: String, secret: String) {
        viewModel.login(identifier, secret)
    }
}
