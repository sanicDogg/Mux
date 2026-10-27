package ru.mux.app.process.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ru.mux.app.process.model.LogSource
import ru.mux.app.process.model.ProcessLogEntry
import ru.mux.app.process.model.ProcessTag

interface ProcessLogRepository {
    val logs: StateFlow<List<ProcessLogEntry>>
    val logStream: SharedFlow<ProcessLogEntry>

    fun addLog(entry: ProcessLogEntry)
    fun addLog(processTag: ProcessTag, source: LogSource, message: String)
    fun clearLogs(processTag: ProcessTag? = null)
    fun getLogsForProcess(processTag: ProcessTag): List<ProcessLogEntry>
}
