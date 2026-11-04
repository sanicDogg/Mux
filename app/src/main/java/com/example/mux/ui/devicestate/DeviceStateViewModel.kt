package com.example.mux.ui.devicestate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.service.DeviceStateTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Outcome of a manual "Send now" action. */
sealed interface DeviceStateSendStatus {
    data object Idle : DeviceStateSendStatus
    data object Success : DeviceStateSendStatus
    data class Failure(val message: String?) : DeviceStateSendStatus
}

data class DeviceStateUiState(
    val deviceState: DeviceStatePayload? = null,
    val isSending: Boolean = false,
    val sendStatus: DeviceStateSendStatus = DeviceStateSendStatus.Idle,
)

/**
 * ViewModel for the standalone Device State screen.
 *
 * Observes the latest snapshot from [DeviceStateTracker] and offers a manual
 * "Send now" action. The ViewModel exposes a structured [DeviceStateSendStatus]
 * so the UI layer can render localized text, keeping this class free of
 * Android `Context`/resource access.
 */
class DeviceStateViewModel(
    private val deviceStateTracker: DeviceStateTracker,
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    private val _sendStatus = MutableStateFlow<DeviceStateSendStatus>(DeviceStateSendStatus.Idle)

    val uiState: StateFlow<DeviceStateUiState> = combine(
        deviceStateTracker.lastCaptured,
        _isSending,
        _sendStatus,
    ) { deviceState, isSending, sendStatus ->
        DeviceStateUiState(
            deviceState = deviceState,
            isSending = isSending,
            sendStatus = sendStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceStateUiState(),
    )

    fun sendNow() {
        viewModelScope.launch {
            _isSending.value = true
            _sendStatus.value = DeviceStateSendStatus.Idle
            val result = try {
                deviceStateTracker.captureAndSend()
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _isSending.value = false
            }
            _sendStatus.value = if (result.isSuccess) {
                DeviceStateSendStatus.Success
            } else {
                DeviceStateSendStatus.Failure(result.exceptionOrNull()?.message)
            }
        }
    }

    fun clearStatus() {
        _sendStatus.value = DeviceStateSendStatus.Idle
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceStateViewModel(app.deviceStateTracker) as T
        }
    }
}
