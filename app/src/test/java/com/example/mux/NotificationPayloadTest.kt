package com.example.mux

import com.example.mux.data.model.CallLogPayload
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.model.SmsPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun testNotificationPayloadSerialization() {
        val payload = NotificationPayload(
            id = "key_123",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "John Doe",
            text = "Hello, are we meeting today?",
            subText = "Chat",
            bigText = "Hello, are we meeting today? Let me know!",
            postTime = 1700000000000L,
            timestampIso = "2023-11-14T22:13:20.000Z",
            channelId = "msg_channel",
            notificationKey = "key_123",
            type = "NOTIFICATION",
            isOngoing = false
        )

        val jsonString = json.encodeToString(NotificationPayload.serializer(), payload)
        assertTrue(jsonString.contains("\"packageName\":\"com.whatsapp\""))
        assertTrue(jsonString.contains("\"title\":\"John Doe\""))

        val deserialized = json.decodeFromString(NotificationPayload.serializer(), jsonString)
        assertEquals(payload.id, deserialized.id)
        assertEquals(payload.packageName, deserialized.packageName)
        assertEquals(payload.appName, deserialized.appName)
        assertEquals(payload.title, deserialized.title)
        assertEquals(payload.text, deserialized.text)
        assertEquals(payload.postTime, deserialized.postTime)
    }

    @Test
    fun testSmsPayloadSerialization() {
        val payload = SmsPayload(
            id = "sms_456",
            sender = "+1234567890",
            messageBody = "Your verification code is 9876",
            timestamp = 1700000000000L,
            timestampIso = "2023-11-14T22:13:20.000Z"
        )

        val jsonString = json.encodeToString(SmsPayload.serializer(), payload)
        assertTrue(jsonString.contains("\"sender\":\"+1234567890\""))

        val deserialized = json.decodeFromString(SmsPayload.serializer(), jsonString)
        assertEquals(payload.sender, deserialized.sender)
        assertEquals(payload.messageBody, deserialized.messageBody)
        assertEquals("SMS", deserialized.type)
    }

    @Test
    fun testCallLogPayloadSerialization() {
        val payload = CallLogPayload(
            id = "call_789",
            phoneNumber = "+1987654321",
            callType = "MISSED",
            durationSeconds = 0L,
            timestamp = 1700000000000L,
            timestampIso = "2023-11-14T22:13:20.000Z",
            cachedName = "Alice"
        )

        val jsonString = json.encodeToString(CallLogPayload.serializer(), payload)
        assertTrue(jsonString.contains("\"callType\":\"MISSED\""))

        val deserialized = json.decodeFromString(CallLogPayload.serializer(), jsonString)
        assertEquals(payload.phoneNumber, deserialized.phoneNumber)
        assertEquals(payload.callType, deserialized.callType)
        assertEquals("CALL_LOG", deserialized.type)
    }
}
