package com.example.mux

import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.InMemoryNotificationLogRepository
import com.example.mux.data.repository.NotificationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the "show immediately, resolve status in place" data-layer operations:
 * [InMemoryNotificationLogRepository.upsertLog] and
 * [InMemoryNotificationLogRepository.updateStatus].
 */
class NotificationLogRepositoryTest {

    private lateinit var repository: InMemoryNotificationLogRepository

    @Before
    fun setUp() {
        repository = InMemoryNotificationLogRepository()
    }

    private fun payload(id: String, title: String? = null) = NotificationPayload(
        id = id,
        packageName = "com.app",
        title = title,
        postTime = 100,
        timestampIso = "2026-08-27T00:00:00Z"
    )

    @Test
    fun upsertLogReplacesExistingIdAndKeepsLatest() {
        repository.addLog(NotificationLogEntry(payload("1", "First"), ForwardingStatus(isSuccess = true)))
        repository.upsertLog(NotificationLogEntry(payload("1", "Updated"), ForwardingStatus.pending()))

        val logs = repository.logs.value
        assertEquals(1, logs.size)
        assertEquals("Updated", logs[0].payload.title)
        assertTrue(logs[0].status.isPending)
    }

    @Test
    fun upsertLogKeepsNewestFirstOrdering() {
        repository.addLog(NotificationLogEntry(payload("a"), ForwardingStatus(isSuccess = true)))
        repository.upsertLog(NotificationLogEntry(payload("b"), ForwardingStatus(isSuccess = true)))
        repository.upsertLog(NotificationLogEntry(payload("a", "Updated"), ForwardingStatus(isSuccess = true)))

        val logs = repository.logs.value
        assertEquals(2, logs.size)
        assertEquals("a", logs[0].payload.id) // re-upserted "a" moved to the top
        assertEquals("b", logs[1].payload.id)
    }

    @Test
    fun updateStatusResolvesPendingInPlace() {
        repository.addLog(NotificationLogEntry(payload("1"), ForwardingStatus.pending()))
        repository.updateStatus("1", ForwardingStatus(isSuccess = true))

        val logs = repository.logs.value
        assertEquals(1, logs.size)
        assertTrue(logs[0].status.isSuccess)
        assertFalse(logs[0].status.isPending)
        assertNull(logs[0].status.errorMessage)
    }

    @Test
    fun updateStatusWithUnknownIdIsNoOp() {
        repository.addLog(NotificationLogEntry(payload("1"), ForwardingStatus.pending()))
        repository.updateStatus("unknown", ForwardingStatus(isSuccess = true))

        val logs = repository.logs.value
        assertEquals(1, logs.size)
        assertTrue(logs[0].status.isPending)
    }
}
