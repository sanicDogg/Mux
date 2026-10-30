package com.example.mux.ui

import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.InMemoryNotificationLogRepository
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.ui.notifications.NotificationStatusFilter
import com.example.mux.ui.notifications.fast.FastNotificationAction
import com.example.mux.ui.notifications.fast.FastNotificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [FastNotificationViewModel]. Reuses the [FakeSettingsRepository] and
 * [FakeDataForwarder] declared in this package (see [NotificationLogViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FastNotificationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var logRepository: InMemoryNotificationLogRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var dataForwarder: FakeDataForwarder
    private lateinit var serviceConnectedFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: FastNotificationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logRepository = InMemoryNotificationLogRepository()
        settingsRepository = FakeSettingsRepository()
        dataForwarder = FakeDataForwarder()
        serviceConnectedFlow = MutableStateFlow(true)

        viewModel = FastNotificationViewModel(
            notificationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            dataForwarder = dataForwarder,
            isServiceConnectedFlow = serviceConnectedFlow,
            processingDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun payloadOf(
        id: String,
        packageName: String = "com.app",
        appName: String? = null,
        title: String? = null,
        text: String? = null,
        postTime: Long = 100
    ) = NotificationPayload(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        postTime = postTime,
        timestampIso = "2026-08-27T00:00:00Z"
    )

    /**
     * Subscribes to the three shared flows so their WhileSubscribed pipelines start and
     * (with the unconfined test dispatcher) emit their initial values eagerly.
     */
    private fun TestScope.subscribeAll() {
        backgroundScope.launch { viewModel.logList.collect() }
        backgroundScope.launch { viewModel.header.collect() }
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        subscribeAll()

        val state = viewModel.uiState.value
        assertTrue(state.searchQuery.isEmpty())
        assertEquals(NotificationStatusFilter.ALL, state.statusFilter)
        assertNull(state.selectedEntry)
        assertNull(state.selectedJson)
        assertFalse(state.isResending)
        assertNull(state.statusMessage)

        assertTrue(viewModel.logList.value.isEmpty())

        val header = viewModel.header.value
        assertTrue(header.isServiceConnected)
        assertEquals("http://127.0.0.1:8080/api/notifications", header.serverEndpointUrl)
    }

    @Test
    fun testSearchQueryFiltering() = runTest(testDispatcher) {
        subscribeAll()

        logRepository.addLog(NotificationLogEntry(payloadOf("1", "com.whatsapp", "WhatsApp", "Hello"), ForwardingStatus(isSuccess = true)))
        logRepository.addLog(NotificationLogEntry(payloadOf("2", "com.telegram", "Telegram", "Alert"), ForwardingStatus(isSuccess = true)))

        assertEquals(2, viewModel.logList.value.size)

        viewModel.onAction(FastNotificationAction.Search("whatsapp"))
        var list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("com.whatsapp", list[0].payload.packageName)

        // Match by title (case-insensitive)
        viewModel.onAction(FastNotificationAction.Search("ALERT"))
        list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("com.telegram", list[0].payload.packageName)

        // Match by ID
        viewModel.onAction(FastNotificationAction.Search("2"))
        list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("2", list[0].payload.id)

        // Empty query clears the filter
        viewModel.onAction(FastNotificationAction.Search(""))
        assertEquals(2, viewModel.logList.value.size)
    }

    @Test
    fun testStatusFiltering() = runTest(testDispatcher) {
        subscribeAll()

        logRepository.addLog(NotificationLogEntry(payloadOf("1"), ForwardingStatus(isSuccess = true)))
        logRepository.addLog(NotificationLogEntry(payloadOf("2"), ForwardingStatus(isSuccess = false, errorMessage = "Timeout")))

        viewModel.onAction(FastNotificationAction.FilterStatus(NotificationStatusFilter.SUCCESS))
        var list = viewModel.logList.value
        assertEquals(1, list.size)
        assertTrue(list[0].status.isSuccess)

        viewModel.onAction(FastNotificationAction.FilterStatus(NotificationStatusFilter.FAILED))
        list = viewModel.logList.value
        assertEquals(1, list.size)
        assertFalse(list[0].status.isSuccess)

        viewModel.onAction(FastNotificationAction.FilterStatus(NotificationStatusFilter.ALL))
        assertEquals(2, viewModel.logList.value.size)
    }

    @Test
    fun testPendingEntriesAreFilteredSeparately() = runTest(testDispatcher) {
        subscribeAll()

        logRepository.addLog(NotificationLogEntry(payloadOf("pending"), ForwardingStatus.pending()))
        logRepository.addLog(NotificationLogEntry(payloadOf("ok"), ForwardingStatus(isSuccess = true)))
        logRepository.addLog(NotificationLogEntry(payloadOf("fail"), ForwardingStatus(isSuccess = false, errorMessage = "Timeout")))

        // ALL shows everything including the pending row
        assertEquals(3, viewModel.logList.value.size)

        // SUCCESS excludes pending and failed
        viewModel.onAction(FastNotificationAction.FilterStatus(NotificationStatusFilter.SUCCESS))
        var list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("ok", list[0].payload.id)

        // FAILED excludes pending and success
        viewModel.onAction(FastNotificationAction.FilterStatus(NotificationStatusFilter.FAILED))
        list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("fail", list[0].payload.id)
    }

    @Test
    fun testDeduplicationLatestWins() = runTest(testDispatcher) {
        subscribeAll()

        logRepository.addLog(NotificationLogEntry(payloadOf("dup", title = "First"), ForwardingStatus(isSuccess = false, errorMessage = "Old failure")))
        logRepository.addLog(NotificationLogEntry(payloadOf("dup", title = "Updated"), ForwardingStatus(isSuccess = true)))

        val list = viewModel.logList.value
        assertEquals(1, list.size)
        assertEquals("Updated", list[0].payload.title)
        assertTrue(list[0].status.isSuccess)
    }

    @Test
    fun testSelectionByIdAndSurvivesFiltering() = runTest(testDispatcher) {
        subscribeAll()

        val entry = NotificationLogEntry(payloadOf("sel", "com.app", "App", "My Title", "My Text"), ForwardingStatus(isSuccess = true))
        logRepository.addLog(entry)
        logRepository.addLog(NotificationLogEntry(payloadOf("other"), ForwardingStatus(isSuccess = true)))

        viewModel.onAction(FastNotificationAction.SelectEntry("sel"))
        var state = viewModel.uiState.value
        assertNotNull(state.selectedEntry)
        assertEquals("sel", state.selectedEntry?.payload?.id)
        assertNotNull(state.selectedJson)
        assertTrue(state.selectedJson!!.contains("\"packageName\""))

        // Filter so the selected entry is no longer in the visible list — selection survives.
        viewModel.onAction(FastNotificationAction.Search("zzz-no-match"))
        state = viewModel.uiState.value
        assertTrue(viewModel.logList.value.isEmpty())
        assertNotNull(state.selectedEntry)
        assertEquals("sel", state.selectedEntry?.payload?.id)

        // Unknown ID resolves to nothing
        viewModel.onAction(FastNotificationAction.SelectEntry("nope"))
        state = viewModel.uiState.value
        assertNull(state.selectedEntry)
        assertNull(state.selectedJson)

        // Clearing the selection
        viewModel.onAction(FastNotificationAction.SelectEntry(null))
        assertNull(viewModel.uiState.value.selectedEntry)
    }

    @Test
    fun testResendSuccess() = runTest(testDispatcher) {
        subscribeAll()

        dataForwarder.shouldSucceed = true
        val entry = NotificationLogEntry(payloadOf("resend_ok"), ForwardingStatus(isSuccess = false, errorMessage = "Failed"))
        logRepository.addLog(entry)

        viewModel.onAction(FastNotificationAction.SelectEntry("resend_ok"))
        viewModel.onAction(FastNotificationAction.ResendNotification("resend_ok"))

        assertNotNull(dataForwarder.lastPayloadSent)
        assertEquals("http://127.0.0.1:8080/api/notifications", dataForwarder.lastEndpointUrl)

        val state = viewModel.uiState.value
        assertFalse(state.isResending)
        assertNotNull(state.statusMessage)
        assertTrue(state.statusMessage!!.contains("forwarded successfully"))

        // A new `_resend_` entry is added and the selection moves to it.
        val list = viewModel.logList.value
        assertEquals(2, list.size)
        val resendId = state.selectedEntry?.payload?.id
        assertNotNull(resendId)
        assertTrue(resendId!!.startsWith("resend_ok_resend_"))
        assertTrue(list.any { it.payload.id == resendId && it.status.isSuccess })
    }

    @Test
    fun testResendFailure() = runTest(testDispatcher) {
        subscribeAll()

        dataForwarder.shouldSucceed = false
        logRepository.addLog(NotificationLogEntry(payloadOf("resend_err"), ForwardingStatus(isSuccess = true)))

        viewModel.onAction(FastNotificationAction.SelectEntry("resend_err"))
        viewModel.onAction(FastNotificationAction.ResendNotification("resend_err"))

        val state = viewModel.uiState.value
        assertFalse(state.isResending)
        assertNotNull(state.statusMessage)
        assertTrue(state.statusMessage!!.contains("failed"))

        // The failed resend row is added with the error message.
        val resendId = state.selectedEntry?.payload?.id
        assertNotNull(resendId)
        val resendEntry = viewModel.logList.value.first { it.payload.id == resendId }
        assertFalse(resendEntry.status.isSuccess)
        assertNotNull(resendEntry.status.errorMessage)
    }

    @Test
    fun testClearLogsClearsSelectionAndList() = runTest(testDispatcher) {
        subscribeAll()

        logRepository.addLog(NotificationLogEntry(payloadOf("1"), ForwardingStatus(isSuccess = true)))
        viewModel.onAction(FastNotificationAction.SelectEntry("1"))
        assertNotNull(viewModel.uiState.value.selectedEntry)

        viewModel.onAction(FastNotificationAction.ClearLogs)

        assertTrue(viewModel.logList.value.isEmpty())
        assertNull(viewModel.uiState.value.selectedEntry)
        assertNull(viewModel.uiState.value.selectedJson)
    }

    @Test
    fun testHeaderReflectsServiceAndUrlChanges() = runTest(testDispatcher) {
        subscribeAll()

        serviceConnectedFlow.value = false
        var header = viewModel.header.value
        assertFalse(header.isServiceConnected)
        assertEquals("http://127.0.0.1:8080/api/notifications", header.serverEndpointUrl)

        settingsRepository.setServerUrl("http://10.0.0.5:9000/notify")
        header = viewModel.header.value
        assertEquals("http://10.0.0.5:9000/notify", header.serverEndpointUrl)

        serviceConnectedFlow.value = true
        assertTrue(viewModel.header.value.isServiceConnected)
    }
}
