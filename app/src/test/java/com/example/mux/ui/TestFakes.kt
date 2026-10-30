package com.example.mux.ui

import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared in-memory test doubles for [SettingsRepository] and [DataForwarder].
 */
class FakeSettingsRepository : SettingsRepository {
    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080/api/notifications")
    private val _enabled = MutableStateFlow(true)
    private val _apiKey = MutableStateFlow("token123")

    override val serverUrl: Flow<String> = _serverUrl
    override val isNotificationForwardingEnabled: Flow<Boolean> = _enabled
    override val apiKey: Flow<String> = _apiKey

    override suspend fun getServerUrl(): String = _serverUrl.value
    override suspend fun isNotificationForwardingEnabled(): Boolean = _enabled.value
    override suspend fun getApiKey(): String = _apiKey.value

    override suspend fun setServerUrl(url: String) { _serverUrl.value = url }
    override suspend fun setNotificationForwardingEnabled(enabled: Boolean) { _enabled.value = enabled }
    override suspend fun setApiKey(key: String) { _apiKey.value = key }
}

class FakeDataForwarder : DataForwarder {
    var shouldSucceed: Boolean = true
    var lastPayloadSent: String? = null
    var lastEndpointUrl: String? = null

    override suspend fun forwardData(payload: String): Result<Unit> {
        lastPayloadSent = payload
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Network error"))
    }

    override suspend fun forwardDataToEndpoint(payload: String, endpointUrl: String): Result<Unit> {
        lastPayloadSent = payload
        lastEndpointUrl = endpointUrl
        return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Endpoint error"))
    }
}
