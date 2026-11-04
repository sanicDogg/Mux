package com.example.mux.service

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.example.mux.data.collector.DeviceStateCollector
import com.example.mux.data.collector.DeviceStateEvents
import com.example.mux.data.collector.DeviceStateProvider
import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.model.DeviceStateTrigger
import com.example.mux.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Owns the end-to-end device-state pipeline and isolates it from the
 * notification-listener service lifecycle.
 *
 * Responsibilities:
 * - wire the event bus, broadcast receiver, clipboard listener, and collector;
 * - debounce/dedupe captured snapshots;
 * - serialize and forward them to the device-state channel.
 *
 * The service only calls [start]/[stop] and [captureAndSend], keeping
 * [AppNotificationListenerService] focused on notification interception.
 */
class DeviceStateEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dataForwarder: DataForwarder,
    private val scope: CoroutineScope,
) {

    private val events = DeviceStateEvents()
    private var receiver: DeviceStateBroadcastReceiver? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private val provider = DeviceStateProvider(context, settingsRepository)
    val collector = DeviceStateCollector(provider, events, scope)

    @Volatile
    private var lastSignature: String? = null

    private val _lastCaptured = MutableStateFlow<DeviceStatePayload?>(null)
    val lastCaptured: StateFlow<DeviceStatePayload?> = _lastCaptured.asStateFlow()

    fun start() {
        collector.startCollecting()

        receiver = DeviceStateBroadcastReceiver(events).also { r ->
            // System-broadcast-only receiver: no export flag is required (and
            // adding one would reject several protected system broadcasts).
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, DeviceStateBroadcastReceiver.intentFilter())
        }

        scope.launch {
            collector.dataStream.collect { payload -> forward(payload) }
        }

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            events.publish(DeviceStateTrigger.CLIPBOARD)
        }.also { listener -> clipboard?.addPrimaryClipChangedListener(listener) }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null

        clipboardListener?.let { listener ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            runCatching { clipboard?.removePrimaryClipChangedListener(listener) }
        }
        clipboardListener = null

        collector.stopCollecting()
    }

    /**
     * Captures a snapshot on demand and forwards it to the device-state channel.
     */
    suspend fun captureAndSend(): Result<Unit> {
        if (!settingsRepository.isDeviceStateForwardingEnabled()) {
            Log.d(TAG, "Device state forwarding is disabled. Skipping.")
            return Result.success(Unit)
        }
        return forward(collector.captureAndEmit())
    }

    /** Serializes, dedupes, and forwards a single device-state snapshot. */
    private suspend fun forward(payload: DeviceStatePayload): Result<Unit> {
        _lastCaptured.value = payload

        val signature = payload.contentSignature()
        if (signature == lastSignature) {
            Log.d(TAG, "Device state unchanged. Skipping forward.")
            return Result.success(Unit)
        }
        lastSignature = signature

        return try {
            val jsonPayload = Json.encodeToString(DeviceStatePayload.serializer(), payload)
            Log.d(TAG, "Captured device state. Forwarding payload: $jsonPayload")

            val result = dataForwarder.forwardDeviceState(jsonPayload)
            if (result.isSuccess) {
                Log.i(TAG, "Successfully forwarded device state payload")
            } else {
                Log.e(TAG, "Failed to forward device state payload: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding device state", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "DeviceStateEngine"
    }
}
