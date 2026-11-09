package com.example.mux.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard data model representing a notification captured from the Android system.
 */
@Immutable
@Serializable
data class NotificationPayload(
    @SerialName("id")
    val id: String,
    
    @SerialName("packageName")
    val packageName: String,
    
    @SerialName("appName")
    val appName: String? = null,
    
    @SerialName("title")
    val title: String? = null,
    
    @SerialName("text")
    val text: String? = null,
    
    @SerialName("subText")
    val subText: String? = null,
    
    @SerialName("bigText")
    val bigText: String? = null,
    
    @SerialName("postTime")
    val postTime: Long,
    
    @SerialName("timestampIso")
    val timestampIso: String,
    
    @SerialName("channelId")
    val channelId: String? = null,
    
    @SerialName("notificationKey")
    val notificationKey: String? = null,
    
    @SerialName("type")
    val type: String = "NOTIFICATION",
    
    @SerialName("isOngoing")
    val isOngoing: Boolean = false,

    /**
     * Device hardware/connectivity state captured at the moment this
     * notification arrived. Null when device-state capture is disabled or
     * unavailable, so the notification payload stays lean by default.
     */
    @SerialName("deviceState")
    val deviceState: DeviceStatePayload? = null,
)

/**
 * Placeholder data model representing an SMS message for future SMS Data Collectors.
 */
@Serializable
data class SmsPayload(
    @SerialName("id")
    val id: String,
    
    @SerialName("sender")
    val sender: String,
    
    @SerialName("messageBody")
    val messageBody: String,
    
    @SerialName("timestamp")
    val timestamp: Long,
    
    @SerialName("timestampIso")
    val timestampIso: String,
    
    @SerialName("subscriptionId")
    val subscriptionId: Int? = null,
    
    @SerialName("type")
    val type: String = "SMS"
)

/**
 * Placeholder data model representing a Call Log entry for future Call Log Data Collectors.
 */
@Serializable
data class CallLogPayload(
    @SerialName("id")
    val id: String,
    
    @SerialName("phoneNumber")
    val phoneNumber: String,
    
    @SerialName("callType")
    val callType: String, // e.g. INCOMING, OUTGOING, MISSED, REJECTED
    
    @SerialName("durationSeconds")
    val durationSeconds: Long,
    
    @SerialName("timestamp")
    val timestamp: Long,
    
    @SerialName("timestampIso")
    val timestampIso: String,
    
    @SerialName("cachedName")
    val cachedName: String? = null,
    
    @SerialName("type")
    val type: String = "CALL_LOG"
)

/**
 * Encapsulates status of data forwarding operations.
 */
@Immutable
@Serializable
data class ForwardingStatus(
    val isSuccess: Boolean,
    val isPending: Boolean = false,
    val statusCode: Int? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /** A status representing a notification that is shown but not yet forwarded. */
        fun pending(): ForwardingStatus = ForwardingStatus(isSuccess = false, isPending = true)
    }
}
