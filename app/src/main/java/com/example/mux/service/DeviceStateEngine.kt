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
import kotlinx.coroutines.Job
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
 * [start] and [stop] are idempotent and should be driven by the device-state
 * forwarding setting so the event machinery only runs while the feature is on.
 */
class DeviceStateEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dataForwarder: DataForwarder,
    private val scope: CoroutineScope,
) : DeviceStateTracker {

    private val events = DeviceStateEvents()
    private var receiver: DeviceStateBroadcastReceiver? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var forwardingJob: Job? = null

    private val provider = DeviceStateProvider(context, settingsRepository)
    val collector = DeviceStateCollector(provider, events, scope)

    @Volatile
    private var lastSignature: String? = null
    @Volatile
    private var isStarted: Boolean = false

    private val _lastCaptured = MutableStateFlow<DeviceStatePayload?>(null)
    override val lastCaptured: StateFlow<DeviceStatePayload?> = _lastCaptured.asStateFlow()

    fun start() {
        if (isStarted) return
        isStarted = true

        collector.startCollecting()

        receiver = DeviceStateBroadcastReceiver(events).also { r ->
            // System-broadcast-only receiver: no export flag is required (and
            // adding one would reject several protected system broadcasts).
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, DeviceStateBroadcastReceiver.intentFilter())
        }

        forwardingJob = scope.launch {
            collector.dataStream.collect { payload -> forward(payload) }
        }

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            events.publish(DeviceStateTrigger.CLIPBOARD)
        }.also { listener -> clipboard?.addPrimaryClipChangedListener(listener) }

        Log.d(TAG, "Device state engine started")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null

        clipboardListener?.let { listener ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            runCatching { clipboard?.removePrimaryClipChangedListener(listener) }
        }
        clipboardListener = null

        forwardingJob?.cancel()
        forwardingJob = null
        collector.stopCollecting()

        Log.d(TAG, "Device state engine stopped")
    }

    /**
     * Captures a snapshot on demand and forwards it to the device-state channel.
     *
     * @return [Result.failure] when device-state forwarding is disabled, so
     * callers can distinguish "disabled" from a real network error.
     */
    override suspend fun captureAndSend(): Result<Unit> {
        if (!settingsRepository.isDeviceStateForwardingEnabled()) {
            Log.d(TAG, "Device state forwarding is disabled. Skipping.")
            return Result.failure(IllegalStateException("Device-state forwarding is disabled"))
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
