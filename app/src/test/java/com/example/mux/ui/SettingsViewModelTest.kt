package com.example.mux.ui

import com.example.mux.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = FakeSettingsRepository()
        viewModel = SettingsViewModel(settingsRepository = settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertEquals("http://127.0.0.1:8080/api/notifications", state.serverUrl)
        assertEquals("token123", state.apiKey)
        assertTrue(state.isNotificationForwardingEnabled)
        assertTrue(state.isBlacklistMode)
        assertFalse(state.isSmsForwardingEnabled)
        assertFalse(state.isCallLogForwardingEnabled)
        collectJob.cancel()
    }

    @Test
    fun testSetServerUrlAndApiKey() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setServerUrl("http://10.0.0.1:9090/webhook")
        viewModel.setApiKey("new_api_key_secret")

        val state = viewModel.uiState.value
        assertEquals("http://10.0.0.1:9090/webhook", state.serverUrl)
        assertEquals("new_api_key_secret", state.apiKey)
        assertEquals("http://10.0.0.1:9090/webhook", settingsRepository.getServerUrl())
        assertEquals("new_api_key_secret", settingsRepository.getApiKey())
        collectJob.cancel()
    }

    @Test
    fun testToggleNotificationForwarding() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setNotificationForwardingEnabled(false)

        val state = viewModel.uiState.value
        assertFalse(state.isNotificationForwardingEnabled)
        assertFalse(settingsRepository.isNotificationForwardingEnabled())
        collectJob.cancel()
    }

    @Test
    fun testPackageRulesManagement() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.addPackageRule("com.test.app")
        assertTrue(viewModel.uiState.value.packageRules.contains("com.test.app"))

        viewModel.setPackageRuleMode(false) // Whitelist mode
        assertFalse(viewModel.uiState.value.isBlacklistMode)

        viewModel.removePackageRule("com.test.app")
        assertFalse(viewModel.uiState.value.packageRules.contains("com.test.app"))
        collectJob.cancel()
    }

    @Test
    fun testSmsAndCallLogPlaceholders() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.toggleSmsForwarding(true)
        viewModel.toggleCallLogForwarding(true)

        val state = viewModel.uiState.value
        assertTrue(state.isSmsForwardingEnabled)
        assertTrue(state.isCallLogForwardingEnabled)
        collectJob.cancel()
    }
}
