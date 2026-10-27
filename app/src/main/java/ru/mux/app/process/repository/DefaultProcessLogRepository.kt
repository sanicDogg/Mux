package ru.mux.app.process.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mux.app.process.model.LogSource
import ru.mux.app.process.model.ProcessLogEntry
import ru.mux.app.process.model.ProcessTag

class DefaultProcessLogRepository(
    private val maxCapacity: Int = 1000,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : ProcessLogRepository {

    private val _logs = MutableStateFlow<List<ProcessLogEntry>>(emptyList())
    override val logs: StateFlow<List<ProcessLogEntry>> = _logs.asStateFlow()

    private val _logStream = MutableSharedFlow<ProcessLogEntry>(replay = 100, extraBufferCapacity = 500)
    override val logStream: SharedFlow<ProcessLogEntry> = _logStream.asSharedFlow()

    private val lock = Any()

    override fun addLog(entry: ProcessLogEntry) {
        synchronized(lock) {
            _logs.update { current ->
                val newList = current + entry
                if (newList.size > maxCapacity) {
                    newList.takeLast(maxCapacity)
                } else {
                    newList
                }
            }
        }
        coroutineScope.launch {
            _logStream.emit(entry)
        }
    }

    override fun addLog(processTag: ProcessTag, source: LogSource, message: String) {
        addLog(
            ProcessLogEntry(
                processTag = processTag,
                source = source,
                message = message
            )
        )
    }

    override fun clearLogs(processTag: ProcessTag?) {
        synchronized(lock) {
            if (processTag == null) {
                _logs.value = emptyList()
            } else {
                _logs.update { current ->
                    current.filterNot { it.processTag == processTag }
                }
            }
        }
    }

    override fun getLogsForProcess(processTag: ProcessTag): List<ProcessLogEntry> {
        return logs.value.filter { it.processTag == processTag }
    }
}
