package com.example.mux.ui.devicestate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.service.DeviceStateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Outcome of a manual forward action. */
sealed interface DeviceStateSendStatus {
    data object Idle : DeviceStateSendStatus
    data object Success : DeviceStateSendStatus
    data class Failure(val message: String?) : DeviceStateSendStatus
}

private val prettyJson = Json { prettyPrint = true }

/**
 * Rarely changing engine status: whether device-state forwarding is enabled and
 * the endpoint payloads are sent to. Isolated from the hot payload flow.
 */
data class DeviceStateHeader(
    val isForwardingEnabled: Boolean = false,
    val deviceStateEndpointUrl: String = ""
)

data class DeviceStateUiState(
    val deviceState: DeviceStatePayload? = null,
    val deviceStateJson: String? = null,
    val header: DeviceStateHeader = DeviceStateHeader(),
    val isSending: Boolean = false,
    val sendStatus: DeviceStateSendStatus = DeviceStateSendStatus.Idle,
)

/**
 * ViewModel for the standalone Device State screen.
 *
 * Observes the latest snapshot from [DeviceStateTracker], the forwarding
 * enablement + endpoint from [SettingsRepository], and offers a manual
 * "Resend / Forward Payload" action. The ViewModel exposes structured state so
 * the UI layer can render localized text, keeping this class free of Android
 * `Context`/resource access.
 */
class DeviceStateViewModel(
    private val deviceStateTracker: DeviceStateTracker,
    private val settingsRepository: SettingsRepository,
    private val dataForwarder: DataForwarder,
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    private val _sendStatus = MutableStateFlow<DeviceStateSendStatus>(DeviceStateSendStatus.Idle)

    /**
     * Rare header state. Uses the DataStore flows directly (no suspend read
     * inside the transform).
     */
    val header: StateFlow<DeviceStateHeader> = combine(
        settingsRepository.isDeviceStateForwardingEnabled,
        settingsRepository.deviceStateUrl
    ) { enabled, url ->
        DeviceStateHeader(isForwardingEnabled = enabled, deviceStateEndpointUrl = url)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceStateHeader())

    val uiState: StateFlow<DeviceStateUiState> = combine(
        deviceStateTracker.lastCaptured,
        header,
        _isSending,
        _sendStatus,
    ) { deviceState, header, isSending, sendStatus ->
        val deviceStateJson = deviceState?.let { payload ->
            withContext(Dispatchers.Default) {
                runCatching {
                    prettyJson.encodeToString(DeviceStatePayload.serializer(), payload)
                }.getOrNull() ?: payload.toString()
            }
        }
        DeviceStateUiState(
            deviceState = deviceState,
            deviceStateJson = deviceStateJson,
            header = header,
            isSending = isSending,
            sendStatus = sendStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceStateUiState(),
    )

    /** Captures the current state and forwards it to the device-state channel. */
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

    /** Forwards the latest already-captured payload without re-capturing. */
    fun resend() {
        viewModelScope.launch {
            val payload = deviceStateTracker.lastCaptured.value ?: return@launch

            _isSending.value = true
            _sendStatus.value = DeviceStateSendStatus.Idle
            val result = try {
                val jsonPayload = Json.encodeToString(DeviceStatePayload.serializer(), payload)
                val targetUrl = settingsRepository.getDeviceStateUrl()
                dataForwarder.forwardDataToEndpoint(jsonPayload, targetUrl)
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
            return DeviceStateViewModel(
                deviceStateTracker = app.deviceStateTracker,
                settingsRepository = app.settingsRepository,
                dataForwarder = app.dataForwarder
            ) as T
        }
    }
}
