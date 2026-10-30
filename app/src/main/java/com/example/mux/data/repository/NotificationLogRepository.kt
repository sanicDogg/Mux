package com.example.mux.data.repository

import androidx.compose.runtime.Immutable
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.ConcurrentLinkedQueue

@Immutable
data class NotificationLogEntry(
    val payload: NotificationPayload,
    val status: ForwardingStatus,
    val loggedAt: Long = System.currentTimeMillis()
)

interface NotificationLogRepository {
    val logs: StateFlow<ImmutableList<NotificationLogEntry>>
    fun addLog(entry: NotificationLogEntry)
    fun addLogs(entries: List<NotificationLogEntry>)

    /**
     * Inserts [entry], replacing any existing entry with the same [NotificationPayload.id].
     * This is the "show immediately, latest wins" path: a re-posted notification updates
     * its row in place instead of producing a duplicate.
     */
    fun upsertLog(entry: NotificationLogEntry)

    /**
     * Updates the [ForwardingStatus] of the entry with the given payload [id] in place,
     * keeping its position in the list.
     */
    fun updateStatus(id: String, status: ForwardingStatus)

    fun clearLogs()
}

class InMemoryNotificationLogRepository : NotificationLogRepository {
    private val logList = ConcurrentLinkedQueue<NotificationLogEntry>()
    private val _logs = MutableStateFlow<ImmutableList<NotificationLogEntry>>(persistentListOf())
    override val logs: StateFlow<ImmutableList<NotificationLogEntry>> = _logs.asStateFlow()

    override fun addLog(entry: NotificationLogEntry) {
        logList.add(entry)
        trimToCapacity()
        publish()
    }

    override fun addLogs(entries: List<NotificationLogEntry>) {
        logList.addAll(entries)
        trimToCapacity()
        publish()
    }

    override fun upsertLog(entry: NotificationLogEntry) {
        logList.removeIf { it.payload.id == entry.payload.id }
        logList.add(entry)
        trimToCapacity()
        publish()
    }

    override fun updateStatus(id: String, status: ForwardingStatus) {
        var changed = false
        val updated = logList.map { entry ->
            if (entry.payload.id == id) {
                changed = true
                entry.copy(status = status)
            } else {
                entry
            }
        }
        if (!changed) return

        logList.clear()
        logList.addAll(updated)
        publish()
    }

    override fun clearLogs() {
        logList.clear()
        _logs.value = persistentListOf()
    }

    private fun trimToCapacity() {
        while (logList.size > 100) {
            logList.poll()
        }
    }

    private fun publish() {
        _logs.value = logList.toList().asReversed().toImmutableList()
    }
}
