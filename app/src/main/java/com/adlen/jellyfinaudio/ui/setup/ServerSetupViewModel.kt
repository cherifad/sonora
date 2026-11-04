package com.adlen.jellyfinaudio.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adlen.jellyfinaudio.data.ServerConfig
import com.adlen.jellyfinaudio.data.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val connecting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state = _state.asStateFlow()

    fun connect(baseUrl: String, username: String, password: String) {
        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _state.value = SetupUiState(error = "Fill in the server URL, username and password.")
            return
        }

        viewModelScope.launch {
            _state.value = SetupUiState(connecting = true)
            val config = ServerConfig(
                baseUrl = ServerRepository.normalizeBaseUrl(baseUrl),
                username = username.trim(),
                password = password,
            )
            val result = repository.test(config)
            if (result.isSuccess) {
                repository.save(config)
                _state.value = SetupUiState()
            } else {
                _state.value = SetupUiState(
                    error = result.exceptionOrNull()?.message
                        ?: "Could not connect to the server.",
                )
            }
        }
    }
}
