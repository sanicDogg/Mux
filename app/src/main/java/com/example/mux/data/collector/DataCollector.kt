package com.example.mux.data.collector

import com.example.mux.data.model.CallLogPayload
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.model.SmsPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Core extensible interface for data gathering components in the hub.
 * Implementations gather specific Android system events or data (e.g. Notifications, SMS, Call Logs)
 * and emit structured payloads to downstream processing and forwarding pipelines.
 */
interface DataCollector<T> {
    val collectorId: String
    val collectorType: String
    val isEnabled: Boolean
    val dataStream: Flow<T>
    
    fun startCollecting()
    fun stopCollecting()
    fun emitData(data: T)
}

/**
 * Notification data collector implementation.
 * Receives notification data intercepted by AppNotificationListenerService and streams them.
 */
class NotificationDataCollector : DataCollector<NotificationPayload> {
    override val collectorId: String = "notification_collector"
    override val collectorType: String = "NOTIFICATION"
    
    @Volatile
    private var active: Boolean = true
    override val isEnabled: Boolean
        get() = active

    private val _dataStream = MutableSharedFlow<NotificationPayload>(replay = 1, extraBufferCapacity = 128)
    override val dataStream: Flow<NotificationPayload> = _dataStream.asSharedFlow()

    override fun startCollecting() {
        active = true
    }

    override fun stopCollecting() {
        active = false
    }

    override fun emitData(data: NotificationPayload) {
        if (active) {
            _dataStream.tryEmit(data)
        }
    }
}

/**
 * Modular placeholder collector for future SMS gathering integration.
 */
class SmsDataCollector : DataCollector<SmsPayload> {
    override val collectorId: String = "sms_collector"
    override val collectorType: String = "SMS"

    @Volatile
    private var active: Boolean = false
    override val isEnabled: Boolean
        get() = active

    private val _dataStream = MutableSharedFlow<SmsPayload>(replay = 1, extraBufferCapacity = 128)
    override val dataStream: Flow<SmsPayload> = _dataStream.asSharedFlow()

    override fun startCollecting() {
        active = true
    }

    override fun stopCollecting() {
        active = false
    }

    override fun emitData(data: SmsPayload) {
        if (active) {
            _dataStream.tryEmit(data)
        }
    }
}

/**
 * Modular placeholder collector for future Call Log gathering integration.
 */
class CallLogDataCollector : DataCollector<CallLogPayload> {
    override val collectorId: String = "call_log_collector"
    override val collectorType: String = "CALL_LOG"

    @Volatile
    private var active: Boolean = false
    override val isEnabled: Boolean
        get() = active

    private val _dataStream = MutableSharedFlow<CallLogPayload>(replay = 1, extraBufferCapacity = 128)
    override val dataStream: Flow<CallLogPayload> = _dataStream.asSharedFlow()

    override fun startCollecting() {
        active = true
    }

    override fun stopCollecting() {
        active = false
    }

    override fun emitData(data: CallLogPayload) {
        if (active) {
            _dataStream.tryEmit(data)
        }
    }
}
