package com.example.mux

import com.example.mux.data.collector.CallLogDataCollector
import com.example.mux.data.collector.NotificationDataCollector
import com.example.mux.data.collector.SmsDataCollector
import com.example.mux.data.model.CallLogPayload
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.model.SmsPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataCollectorTest {

    @Test
    fun testNotificationDataCollector() = runTest {
        val collector = NotificationDataCollector()
        collector.startCollecting()
        assertTrue(collector.isEnabled)
        assertEquals("NOTIFICATION", collector.collectorType)

        val payload = NotificationPayload(
            id = "test_1",
            packageName = "com.test.app",
            postTime = 1000L,
            timestampIso = "1970-01-01T00:00:01.000Z"
        )

        collector.emitData(payload)
        val collected = collector.dataStream.first()
        assertEquals("test_1", collected.id)
        assertEquals("com.test.app", collected.packageName)

        collector.stopCollecting()
        assertFalse(collector.isEnabled)
    }

    @Test
    fun testSmsDataCollector() = runTest {
        val collector = SmsDataCollector()
        assertFalse(collector.isEnabled)
        collector.startCollecting()
        assertTrue(collector.isEnabled)
        assertEquals("SMS", collector.collectorType)

        val payload = SmsPayload(
            id = "sms_1",
            sender = "12345",
            messageBody = "Test SMS",
            timestamp = 2000L,
            timestampIso = "1970-01-01T00:00:02.000Z"
        )

        collector.emitData(payload)
        val collected = collector.dataStream.first()
        assertEquals("sms_1", collected.id)
        assertEquals("12345", collected.sender)
    }

    @Test
    fun testCallLogDataCollector() = runTest {
        val collector = CallLogDataCollector()
        assertFalse(collector.isEnabled)
        collector.startCollecting()
        assertTrue(collector.isEnabled)
        assertEquals("CALL_LOG", collector.collectorType)

        val payload = CallLogPayload(
            id = "call_1",
            phoneNumber = "99999",
            callType = "INCOMING",
            durationSeconds = 120L,
            timestamp = 3000L,
            timestampIso = "1970-01-01T00:00:03.000Z"
        )

        collector.emitData(payload)
        val collected = collector.dataStream.first()
        assertEquals("call_1", collected.id)
        assertEquals("INCOMING", collected.callType)
    }
}
