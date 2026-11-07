package com.example.mux.ui

import com.example.mux.ui.devicestate.DeviceStateSendStatus
import com.example.mux.ui.devicestate.DeviceStateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceStateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tracker: FakeDeviceStateTracker
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var dataForwarder: FakeDataForwarder
    private lateinit var viewModel: DeviceStateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = FakeDeviceStateTracker()
        settingsRepository = FakeSettingsRepository()
        dataForwarder = FakeDataForwarder()
        viewModel = DeviceStateViewModel(tracker, settingsRepository, dataForwarder)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        assertEquals(null, viewModel.uiState.value.deviceState)
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(DeviceStateSendStatus.Idle, viewModel.uiState.value.sendStatus)
        collectJob.cancel()
    }

    @Test
    fun testSendNowSuccess() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        tracker.shouldSucceed = true
        viewModel.sendNow()

        assertEquals(1, tracker.captureCallCount)
        assertEquals(DeviceStateSendStatus.Success, viewModel.uiState.value.sendStatus)
        assertFalse(viewModel.uiState.value.isSending)
        collectJob.cancel()
    }

    @Test
    fun testSendNowFailure() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        tracker.shouldSucceed = false
        viewModel.sendNow()

        assertEquals(1, tracker.captureCallCount)
        val status = viewModel.uiState.value.sendStatus
        assertTrue(status is DeviceStateSendStatus.Failure)
        assertFalse(viewModel.uiState.value.isSending)
        collectJob.cancel()
    }

    @Test
    fun testResendForwardsLatestPayload() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        tracker.emit(com.example.mux.data.model.DeviceStatePayload(
            batteryLevel = 100,
            isWifiConnected = true,
            signalStrength = "high",
            timestamp = 0L,
            timestampIso = "2026-01-01T00:00:00.000Z"
        ))
        dataForwarder.shouldSucceed = true
        viewModel.resend()
        advanceUntilIdle()

        assertEquals(DeviceStateSendStatus.Success, viewModel.uiState.value.sendStatus)
        assertEquals("http://127.0.0.1:8080/api/device-state", dataForwarder.lastEndpointUrl)
        assertFalse(dataForwarder.lastPayloadSent.isNullOrBlank())
        collectJob.cancel()
    }
}
