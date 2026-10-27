package com.example.mux.ui

import com.example.mux.process.manager.BinaryManager
import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.nginx.NginxConfigManager
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.repository.DefaultProcessLogRepository
import com.example.mux.process.tuna.TunnelProtocol
import com.example.mux.process.tuna.TunaProcessController
import com.example.mux.ui.webserver.NginxConfigViewModel
import com.example.mux.ui.webserver.WebServerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NginxConfigViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var nginxConfigManager: NginxConfigManager
    private lateinit var nginxProcessController: NginxProcessController
    private lateinit var tunaProcessController: TunaProcessController
    private lateinit var viewModel: NginxConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val logRepository = DefaultProcessLogRepository()
        val nativeProcessManager = NativeProcessManager(logRepository)
        val binaryManager = BinaryManager(tempFolder.root)

        nginxConfigManager = NginxConfigManager(tempFolder.root)
        nginxProcessController = NginxProcessController(nativeProcessManager, binaryManager, nginxConfigManager)
        tunaProcessController = TunaProcessController(nativeProcessManager, binaryManager, logRepository)

        viewModel = NginxConfigViewModel(
            nginxConfigManager = nginxConfigManager,
            nginxProcessController = nginxProcessController,
            tunaProcessController = tunaProcessController,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialConfigAndFilesLoaded() = runTest(testDispatcher) {
        val state = viewModel.uiState.first()

        assertTrue(state.isConfigValid)
        assertNotNull(state.parsedConfig)
        assertEquals(8080, state.nginxPort)
        assertTrue(state.staticFiles.any { it.name == "index.html" })
    }

    @Test
    fun testUpdateAndSaveRawConfig() = runTest(testDispatcher) {
        val validConfig = """
            worker_processes 1;
            events { worker_connections 512; }
            http {
                server {
                    listen 9090;
                    server_name myhost;
                    location / { root html; }
                }
            }
        """.trimIndent()

        viewModel.updateRawConfigText(validConfig)

        var state = viewModel.uiState.first()
        assertTrue(state.isConfigValid)

        viewModel.saveRawConfig()

        state = viewModel.uiState.first()
        assertEquals("nginx.conf saved successfully.", state.statusMessage)
        assertEquals(9090, state.nginxPort)
    }

    @Test
    fun testUpdateNginxSettings() = runTest(testDispatcher) {
        viewModel.updateNginxSettings(7070, "custom.local")

        val state = viewModel.uiState.first()
        assertEquals(7070, state.nginxPort)
        assertEquals("custom.local", state.nginxServerName)
    }

    @Test
    fun testStaticFileCreateEditDelete() = runTest(testDispatcher) {
        viewModel.createStaticFile("test.html", "<h1>Test Content</h1>")

        var state = viewModel.uiState.first()
        assertTrue(state.staticFiles.any { it.name == "test.html" })
        assertEquals("test.html", state.selectedStaticFile?.name)

        viewModel.updateSelectedFileContent("<h1>Updated Content</h1>")
        viewModel.saveSelectedStaticFile()

        val testFile = File(nginxConfigManager.htmlDir, "test.html")
        assertEquals("<h1>Updated Content</h1>", testFile.readText())

        viewModel.deleteStaticFile(testFile)

        state = viewModel.uiState.first()
        assertFalse(state.staticFiles.any { it.name == "test.html" })
    }

    @Test
    fun testUpdateTunaConfigAndTab() = runTest(testDispatcher) {
        viewModel.setActiveTab(WebServerTab.TUNA_SETTINGS)
        viewModel.updateTunaConfig(
            localPort = 9090,
            protocol = TunnelProtocol.HTTPS,
            remoteHost = "tunnel.example.com",
            subdomain = "my-subdomain",
            customFlagsText = "--flag1 --flag2"
        )

        val state = viewModel.uiState.first()
        assertEquals(WebServerTab.TUNA_SETTINGS, state.activeTab)
        assertEquals(9090, state.tunaConfig.localPort)
        assertEquals(TunnelProtocol.HTTPS, state.tunaConfig.protocol)
        assertEquals("tunnel.example.com", state.tunaConfig.remoteHost)
        assertEquals("my-subdomain", state.tunaConfig.subdomain)
        assertEquals(listOf("--flag1", "--flag2"), state.tunaConfig.customFlags)
    }
}
