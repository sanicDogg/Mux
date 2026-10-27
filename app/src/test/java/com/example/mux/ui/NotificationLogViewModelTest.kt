package com.example.mux.ui

import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.InMemoryNotificationLogRepository
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.ui.notifications.NotificationLogViewModel
import com.example.mux.ui.notifications.NotificationStatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var logRepository: InMemoryNotificationLogRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var dataForwarder: FakeDataForwarder
    private lateinit var serviceConnectedFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: NotificationLogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logRepository = InMemoryNotificationLogRepository()
        settingsRepository = FakeSettingsRepository()
        dataForwarder = FakeDataForwarder()
        serviceConnectedFlow = MutableStateFlow(true)

        viewModel = NotificationLogViewModel(
            notificationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            dataForwarder = dataForwarder,
            isServiceConnectedFlow = serviceConnectedFlow
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertTrue(state.logs.isEmpty())
        assertTrue(state.isServiceConnected)
        assertEquals("http://127.0.0.1:8080/api/notifications", state.serverEndpointUrl)
        collectJob.cancel()
    }

    @Test
    fun testSearchQueryAndStatusFiltering() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        val payload1 = NotificationPayload(id = "1", packageName = "com.whatsapp", appName = "WhatsApp", title = "Hello", text = "World", postTime = 100, timestampIso = "2026-08-27T00:00:00Z")
        val payload2 = NotificationPayload(id = "2", packageName = "com.telegram", appName = "Telegram", title = "Alert", text = "Warning", postTime = 200, timestampIso = "2026-08-27T00:01:00Z")

        logRepository.addLog(NotificationLogEntry(payload1, ForwardingStatus(isSuccess = true)))
        logRepository.addLog(NotificationLogEntry(payload2, ForwardingStatus(isSuccess = false, errorMessage = "Timeout")))

        viewModel.setSearchQuery("WhatsApp")

        var state = viewModel.uiState.value
        assertEquals(1, state.filteredLogs.size)
        assertEquals("com.whatsapp", state.filteredLogs[0].payload.packageName)

        viewModel.setSearchQuery("")
        viewModel.setStatusFilter(NotificationStatusFilter.FAILED)

        state = viewModel.uiState.value
        assertEquals(1, state.filteredLogs.size)
        assertEquals("com.telegram", state.filteredLogs[0].payload.packageName)
        collectJob.cancel()
    }

    @Test
    fun testSelectEntryAndResendNotification() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        val payload = NotificationPayload(id = "resend_test", packageName = "com.example.app", title = "Test", text = "Body", postTime = 100, timestampIso = "2026-08-27T00:00:00Z")
        val entry = NotificationLogEntry(payload, ForwardingStatus(isSuccess = false, errorMessage = "Failed"))
        logRepository.addLog(entry)

        viewModel.selectEntry(entry)

        dataForwarder.shouldSucceed = true
        viewModel.resendNotification(entry)

        assertNotNull(dataForwarder.lastPayloadSent)
        assertEquals("http://127.0.0.1:8080/api/notifications", dataForwarder.lastEndpointUrl)

        val state = viewModel.uiState.value
        assertTrue(state.resendResult?.contains("forwarded successfully") == true)
        collectJob.cancel()
    }

    @Test
    fun testClearLogs() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        val payload = NotificationPayload(id = "1", packageName = "com.app", postTime = 100, timestampIso = "2026-08-27T00:00:00Z")
        logRepository.addLog(NotificationLogEntry(payload, ForwardingStatus(isSuccess = true)))

        viewModel.clearLogs()

        val state = viewModel.uiState.value
        assertTrue(state.logs.isEmpty())
        assertNull(state.selectedEntry)
        collectJob.cancel()
    }
}
