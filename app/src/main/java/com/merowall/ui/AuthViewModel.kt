package com.merowall.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merowall.data.model.Result
import com.merowall.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isLoggedIn = authRepository.isLoggedIn))
    val uiState = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            authRepository.signInWithEmail(email, password).collect { result ->
                when (result) {
                    is Result.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Result.Success -> _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        viewModelScope.launch {
            authRepository.signUpWithEmail(email, password, username).collect { result ->
                when (result) {
                    is Result.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Result.Success -> _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.update { it.copy(isLoggedIn = false) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
