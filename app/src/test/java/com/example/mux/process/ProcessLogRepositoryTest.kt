package com.example.mux.process

import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessLogEntry
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.repository.DefaultProcessLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessLogRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: DefaultProcessLogRepository

    @Before
    fun setup() {
        repository = DefaultProcessLogRepository(
            maxCapacity = 5,
            coroutineScope = testScope
        )
    }

    @Test
    fun `addLog updates logs StateFlow and streams log`() = testScope.runTest {
        val entry = ProcessLogEntry(
            processTag = ProcessTag.NGINX,
            source = LogSource.STDOUT,
            message = "Server started"
        )

        repository.addLog(entry)

        val logs = repository.logs.value
        assertEquals(1, logs.size)
        assertEquals("Server started", logs.first().message)
        assertEquals(ProcessTag.NGINX, logs.first().processTag)

        val streamed = repository.logStream.first()
        assertEquals("Server started", streamed.message)
    }

    @Test
    fun `addLog enforces max capacity`() {
        repeat(10) { i ->
            repository.addLog(
                processTag = ProcessTag.TUNA,
                source = LogSource.STDOUT,
                message = "Log line $i"
            )
        }

        val logs = repository.logs.value
        assertEquals(5, logs.size)
        assertEquals("Log line 5", logs.first().message)
        assertEquals("Log line 9", logs.last().message)
    }

    @Test
    fun `getLogsForProcess filters logs correctly`() {
        repository.addLog(ProcessTag.NGINX, LogSource.STDOUT, "Nginx log")
        repository.addLog(ProcessTag.TUNA, LogSource.STDERR, "Tuna error")
        repository.addLog(ProcessTag.NGINX, LogSource.STDERR, "Nginx error")

        val nginxLogs = repository.getLogsForProcess(ProcessTag.NGINX)
        val tunaLogs = repository.getLogsForProcess(ProcessTag.TUNA)

        assertEquals(2, nginxLogs.size)
        assertEquals(1, tunaLogs.size)
        assertEquals("Tuna error", tunaLogs.first().message)
    }

    @Test
    fun `clearLogs removes targeted process logs or all logs`() {
        repository.addLog(ProcessTag.NGINX, LogSource.STDOUT, "Nginx log")
        repository.addLog(ProcessTag.TUNA, LogSource.STDOUT, "Tuna log")

        repository.clearLogs(ProcessTag.NGINX)
        assertEquals(1, repository.logs.value.size)
        assertEquals(ProcessTag.TUNA, repository.logs.value.first().processTag)

        repository.clearLogs()
        assertTrue(repository.logs.value.isEmpty())
    }
}
