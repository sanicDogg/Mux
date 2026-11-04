package com.example.mux.ui

import com.example.mux.process.manager.BinaryManager
import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.nginx.NginxConfigManager
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.repository.DefaultProcessLogRepository
import com.example.mux.process.tuna.TunaProcessController
import com.example.mux.service.DeviceStateTracker
import com.example.mux.ui.dashboard.DashboardViewModel
import com.example.mux.ui.dashboard.LogSourceFilter
import com.example.mux.ui.dashboard.LogTagFilter
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var processLogRepository: DefaultProcessLogRepository
    private lateinit var nativeProcessManager: NativeProcessManager
    private lateinit var nginxProcessController: NginxProcessController
    private lateinit var tunaProcessController: TunaProcessController
    private lateinit var deviceStateTracker: DeviceStateTracker
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        processLogRepository = DefaultProcessLogRepository()
        nativeProcessManager = NativeProcessManager(processLogRepository)

        val binaryManager = BinaryManager(tempFolder.root)
        val nginxConfigManager = NginxConfigManager(tempFolder.root)

        nginxProcessController = NginxProcessController(nativeProcessManager, binaryManager, nginxConfigManager)
        tunaProcessController = TunaProcessController(nativeProcessManager, binaryManager, processLogRepository)
        deviceStateTracker = FakeDeviceStateTracker()

        viewModel = DashboardViewModel(
            nativeProcessManager = nativeProcessManager,
            nginxProcessController = nginxProcessController,
            tunaProcessController = tunaProcessController,
            processLogRepository = processLogRepository,
            deviceStateTracker = deviceStateTracker
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val logsCollectJob = backgroundScope.launch { viewModel.filteredLogs.collect() }
        val state = viewModel.uiState.value
        val logs = viewModel.filteredLogs.value
        assertFalse(state.nginxStatus.isRunning)
        assertFalse(state.tunaStatus.isRunning)
        assertTrue(logs.isEmpty())
        assertTrue(state.isAutoScrollEnabled)
        assertEquals(LogSourceFilter.ALL, state.sourceFilter)
        assertEquals(LogTagFilter.ALL, state.tagFilter)
        collectJob.cancel()
        logsCollectJob.cancel()
    }

    @Test
    fun testLogFiltering() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val logsCollectJob = backgroundScope.launch { viewModel.filteredLogs.collect() }

        processLogRepository.addLog(ProcessTag.NGINX, LogSource.STDOUT, "Nginx stdout message")
        processLogRepository.addLog(ProcessTag.TUNA, LogSource.STDERR, "Tuna stderr message")

        viewModel.setSourceFilter(LogSourceFilter.STDOUT)
        viewModel.setTagFilter(LogTagFilter.NGINX)

        val filteredLogs = viewModel.filteredLogs.value
        assertEquals(1, filteredLogs.size)
        assertEquals("Nginx stdout message", filteredLogs[0].message)
        collectJob.cancel()
        logsCollectJob.cancel()
    }

    @Test
    fun testToggleAutoScrollAndClearLogs() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val logsCollectJob = backgroundScope.launch { viewModel.filteredLogs.collect() }

        processLogRepository.addLog(ProcessTag.NGINX, LogSource.STDOUT, "Message 1")

        viewModel.toggleAutoScroll()
        assertFalse(viewModel.uiState.value.isAutoScrollEnabled)

        viewModel.clearLogs()
        assertTrue(viewModel.filteredLogs.value.isEmpty())
        collectJob.cancel()
        logsCollectJob.cancel()
    }

    @Test
    fun testGetFormattedLogsText() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }
        val logsCollectJob = backgroundScope.launch { viewModel.filteredLogs.collect() }

        processLogRepository.addLog(ProcessTag.NGINX, LogSource.STDOUT, "Server running")

        val formattedText = viewModel.getFormattedLogsText()
        assertTrue(formattedText.contains("Server running"))
        assertTrue(formattedText.contains("NGINX"))
        collectJob.cancel()
        logsCollectJob.cancel()
    }

    @Test
    fun testSendDeviceStateUpdatesStatus() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        (deviceStateTracker as FakeDeviceStateTracker).shouldSucceed = true
        viewModel.sendDeviceState()

        assertEquals(1, (deviceStateTracker as FakeDeviceStateTracker).captureCallCount)
        assertEquals("Device state sent.", viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isSendingDeviceState)
        collectJob.cancel()
    }

    @Test
    fun testSendDeviceStateFailure() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect() }

        (deviceStateTracker as FakeDeviceStateTracker).shouldSucceed = false
        viewModel.sendDeviceState()

        assertEquals(1, (deviceStateTracker as FakeDeviceStateTracker).captureCallCount)
        assertTrue(viewModel.uiState.value.statusMessage?.startsWith("Device state send failed:") == true)
        assertFalse(viewModel.uiState.value.isSendingDeviceState)
        collectJob.cancel()
    }
}
