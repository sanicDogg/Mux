package com.example.mux.process

import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.repository.DefaultProcessLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeProcessManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var logRepository: DefaultProcessLogRepository
    private lateinit var processManager: NativeProcessManager

    @Before
    fun setup() {
        logRepository = DefaultProcessLogRepository(coroutineScope = testScope)
        processManager = NativeProcessManager(logRepository, coroutineScope = testScope)
    }

    @Test
    fun `startProcess executes system command and logs stdout`() = testScope.runTest {
        val command = listOf("sh", "-c", "echo 'Hello from test process'")

        val result = processManager.startProcess(ProcessTag.CUSTOM, command)

        assertTrue(result.isSuccess)

        Thread.sleep(500)

        val logs = logRepository.getLogsForProcess(ProcessTag.CUSTOM)
        assertTrue(logs.any { it.message.contains("Hello from test process") })
    }

    @Test
    fun `isProcessRunning returns false for non-existent process`() {
        assertFalse(processManager.isProcessRunning(ProcessTag.NGINX))
    }

    @Test
    fun `startProcess fails if process with same tag is running`() = testScope.runTest {
        val command = listOf("sleep", "5")
        val firstStart = processManager.startProcess(ProcessTag.TUNA, command)
        assertTrue(firstStart.isSuccess)
        assertTrue(processManager.isProcessRunning(ProcessTag.TUNA))

        val secondStart = processManager.startProcess(ProcessTag.TUNA, command)
        assertTrue(secondStart.isFailure)

        processManager.stopProcess(ProcessTag.TUNA)
    }

    @Test
    fun `stopProcess terminates running process`() = testScope.runTest {
        val command = listOf("sleep", "10")
        processManager.startProcess(ProcessTag.NGINX, command)

        assertTrue(processManager.isProcessRunning(ProcessTag.NGINX))

        val stopped = processManager.stopProcess(ProcessTag.NGINX)
        assertTrue(stopped)

        Thread.sleep(200)
        assertFalse(processManager.isProcessRunning(ProcessTag.NGINX))
    }

    @Test
    fun `stopAllProcesses terminates all active processes`() = testScope.runTest {
        processManager.startProcess(ProcessTag.NGINX, listOf("sleep", "10"))
        processManager.startProcess(ProcessTag.TUNA, listOf("sleep", "10"))

        assertTrue(processManager.isProcessRunning(ProcessTag.NGINX))
        assertTrue(processManager.isProcessRunning(ProcessTag.TUNA))

        processManager.stopAllProcesses()
        Thread.sleep(200)

        assertFalse(processManager.isProcessRunning(ProcessTag.NGINX))
        assertFalse(processManager.isProcessRunning(ProcessTag.TUNA))
    }

    @Test
    fun `startProcess falls back gracefully to SimulatedProcess when binary execution fails`() = testScope.runTest {
        val invalidCommand = listOf("/nonexistent/path/to/nginx", "-c", "nginx.conf")

        val result = processManager.startProcess(ProcessTag.NGINX, invalidCommand)
        assertTrue(result.isSuccess)
        assertTrue(processManager.isProcessRunning(ProcessTag.NGINX))

        var logs = logRepository.getLogsForProcess(ProcessTag.NGINX)
        var attempts = 0
        while (logs.none { it.message.contains("[nginx] Starting worker process on port 8080...") } && attempts < 20) {
            Thread.sleep(100)
            attempts++
            logs = logRepository.getLogsForProcess(ProcessTag.NGINX)
        }

        assertTrue(logs.any { it.message.contains("[nginx] Starting worker process on port 8080...") })

        processManager.stopProcess(ProcessTag.NGINX)
        assertFalse(processManager.isProcessRunning(ProcessTag.NGINX))
    }

    @Test
    fun `startProcess falls back gracefully to SimulatedProcess for Tuna logs`() = testScope.runTest {
        val invalidCommand = listOf("/nonexistent/path/to/tuna", "http", "8080")

        val result = processManager.startProcess(ProcessTag.TUNA, invalidCommand)
        assertTrue(result.isSuccess)
        assertTrue(processManager.isProcessRunning(ProcessTag.TUNA))

        var logs = logRepository.getLogsForProcess(ProcessTag.TUNA)
        var attempts = 0
        while (logs.none { it.message.contains("[tuna] Tunnel established at https://mux-tunnel.tuna.am -> http://localhost:8080") } && attempts < 20) {
            Thread.sleep(100)
            attempts++
            logs = logRepository.getLogsForProcess(ProcessTag.TUNA)
        }

        assertTrue(logs.any { it.message.contains("[tuna] Tunnel established at https://mux-tunnel.tuna.am -> http://localhost:8080") })

        processManager.stopProcess(ProcessTag.TUNA)
        assertFalse(processManager.isProcessRunning(ProcessTag.TUNA))
    }
}
