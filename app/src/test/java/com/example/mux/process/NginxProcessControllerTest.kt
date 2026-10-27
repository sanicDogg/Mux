package com.example.mux.process

import com.example.mux.process.manager.BinaryManager
import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.nginx.NginxConfigManager
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.repository.DefaultProcessLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class NginxProcessControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var logRepository: DefaultProcessLogRepository
    private lateinit var processManager: NativeProcessManager
    private lateinit var binaryManager: BinaryManager
    private lateinit var configManager: NginxConfigManager
    private lateinit var nginxController: NginxProcessController

    @Before
    fun setup() {
        tempFolder.create()
        logRepository = DefaultProcessLogRepository(coroutineScope = testScope)
        processManager = NativeProcessManager(logRepository, coroutineScope = testScope)
        binaryManager = BinaryManager(tempFolder.root)
        configManager = NginxConfigManager(tempFolder.root)
        nginxController = NginxProcessController(processManager, binaryManager, configManager)
    }

    @Test
    fun `buildCommand creates correct command array pointing to config file`() {
        val command = nginxController.buildCommand()
        assertTrue(command.contains("-c"))
        assertTrue(command.contains(configManager.configFile.absolutePath))
        assertTrue(command.contains("daemon off;"))
    }

    @Test
    fun `isServerRunning returns false initially`() {
        assertFalse(nginxController.isServerRunning())
    }
}
