package com.example.mux.data.repository

import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

data class NotificationLogEntry(
    val payload: NotificationPayload,
    val status: ForwardingStatus,
    val loggedAt: Long = System.currentTimeMillis()
)

interface NotificationLogRepository {
    val logs: StateFlow<List<NotificationLogEntry>>
    fun addLog(entry: NotificationLogEntry)
    fun clearLogs()
}

class InMemoryNotificationLogRepository : NotificationLogRepository {
    private val logList = ConcurrentLinkedQueue<NotificationLogEntry>()
    private val _logs = MutableStateFlow<List<NotificationLogEntry>>(emptyList())
    override val logs: StateFlow<List<NotificationLogEntry>> = _logs.asStateFlow()

    override fun addLog(entry: NotificationLogEntry) {
        logList.add(entry)
        while (logList.size > 100) {
            logList.poll()
        }
        _logs.value = logList.toList().reversed()
    }

    override fun clearLogs() {
        logList.clear()
        _logs.value = emptyList()
    }
}
