package com.example.mux.ui

import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.service.DeviceStateTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared in-memory test doubles for [SettingsRepository] and [DataForwarder].
 */
class FakeSettingsRepository : SettingsRepository {
    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080/api/notifications")
    private val _enabled = MutableStateFlow(true)
    private val _apiKey = MutableStateFlow("token123")
    private val _deviceStateUrl = MutableStateFlow("http://127.0.0.1:8080/api/device-state")
    private val _deviceStateEnabled = MutableStateFlow(true)
    private val _clipboardEnabled = MutableStateFlow(false)

    override val serverUrl: Flow<String> = _serverUrl
    override val isNotificationForwardingEnabled: Flow<Boolean> = _enabled
    override val apiKey: Flow<String> = _apiKey
    override val deviceStateUrl: Flow<String> = _deviceStateUrl
    override val isDeviceStateForwardingEnabled: Flow<Boolean> = _deviceStateEnabled
    override val isClipboardCaptureEnabled: Flow<Boolean> = _clipboardEnabled

    override suspend fun getServerUrl(): String = _serverUrl.value
    override suspend fun isNotificationForwardingEnabled(): Boolean = _enabled.value
    override suspend fun getApiKey(): String = _apiKey.value
    override suspend fun getDeviceStateUrl(): String = _deviceStateUrl.value
    override suspend fun isDeviceStateForwardingEnabled(): Boolean = _deviceStateEnabled.value
    override suspend fun isClipboardCaptureEnabled(): Boolean = _clipboardEnabled.value

    override suspend fun setServerUrl(url: String) { _serverUrl.value = url }
    override suspend fun setNotificationForwardingEnabled(enabled: Boolean) { _enabled.value = enabled }
    override suspend fun setApiKey(key: String) { _apiKey.value = key }
    override suspend fun setDeviceStateUrl(url: String) { _deviceStateUrl.value = url }
    override suspend fun setDeviceStateForwardingEnabled(enabled: Boolean) { _deviceStateEnabled.value = enabled }
    override suspend fun setClipboardCaptureEnabled(enabled: Boolean) { _clipboardEnabled.value = enabled }
}

class FakeDeviceStateTracker : DeviceStateTracker {
    var shouldSucceed: Boolean = true
    var captureCallCount: Int = 0
    private val _lastCaptured = MutableStateFlow<DeviceStatePayload?>(null)

    override val lastCaptured: StateFlow<DeviceStatePayload?> = _lastCaptured

    fun emit(state: DeviceStatePayload) {
        _lastCaptured.value = state
    }

    override suspend fun capture(trigger: String?): DeviceStatePayload {
        captureCallCount++
        return _lastCaptured.value ?: DeviceStatePayload(
            batteryLevel = 0,
            isWifiConnected = false,
            timestamp = 0L,
            timestampIso = "",
        )
    }

    override suspend fun captureAndSend(): Result<Unit> {
        captureCallCount++
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Capture failed"))
    }
}

class FakeDataForwarder : DataForwarder {
    var shouldSucceed: Boolean = true
    var lastPayloadSent: String? = null
    var lastEndpointUrl: String? = null

    override suspend fun forwardNotification(payload: String): Result<Unit> {
        lastPayloadSent = payload
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Network error"))
    }

    override suspend fun forwardDeviceState(payload: String): Result<Unit> {
        lastPayloadSent = payload
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Network error"))
    }

    override suspend fun forwardDataToEndpoint(payload: String, endpointUrl: String): Result<Unit> {
        lastPayloadSent = payload
        lastEndpointUrl = endpointUrl
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Endpoint error"))
    }
}
