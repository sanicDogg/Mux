package ru.mux.app.process

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mux.app.process.manager.BinaryManager
import ru.mux.app.process.manager.NativeProcessManager
import ru.mux.app.process.repository.DefaultProcessLogRepository
import ru.mux.app.tuna.TunaConfig
import ru.mux.app.tuna.TunaProcessController
import ru.mux.app.tuna.TunnelProtocol

@OptIn(ExperimentalCoroutinesApi::class)
class TunaProcessControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var logRepository: DefaultProcessLogRepository
    private lateinit var processManager: NativeProcessManager
    private lateinit var binaryManager: BinaryManager
    private lateinit var tunaController: TunaProcessController

    @Before
    fun setup() {
        tempFolder.create()
        logRepository = DefaultProcessLogRepository(coroutineScope = testScope)
        processManager = NativeProcessManager(logRepository, coroutineScope = testScope)
        binaryManager = BinaryManager(tempFolder.root)
        tunaController = TunaProcessController(processManager, binaryManager, logRepository)
    }

    @Test
    fun `buildCommand creates correct command list for HTTP default config`() {
        val config = TunaConfig(localPort = 8080, protocol = TunnelProtocol.HTTP)
        val command = tunaController.buildCommand(config)

        assertTrue(command.contains("http"))
        assertTrue(command.contains("8080"))
    }

    @Test
    fun `buildCommand creates correct command list with custom subdomain and flags`() {
        val config = TunaConfig(
            localPort = 3000,
            protocol = TunnelProtocol.HTTPS,
            subdomain = "my-test-subdomain",
            remoteHost = "tuna.example.com",
            customFlags = listOf("--verbose", "--debug")
        )

        val command = tunaController.buildCommand(config)

        assertEquals("https", command[1])
        assertEquals("3000", command[2])
        assertTrue(command.contains("--subdomain"))
        assertTrue(command.contains("my-test-subdomain"))
        assertTrue(command.contains("--remote-host"))
        assertTrue(command.contains("tuna.example.com"))
        assertTrue(command.contains("--verbose"))
        assertTrue(command.contains("--debug"))
    }

    @Test
    fun `isTunnelRunning returns status from process manager`() {
        assertFalse(tunaController.isTunnelRunning())
    }
}
