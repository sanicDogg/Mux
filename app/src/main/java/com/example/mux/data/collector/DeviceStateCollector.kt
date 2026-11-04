package com.example.mux.data.collector

import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.model.DeviceStateTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collector for device-state snapshots.
 *
 * It owns capture on demand (via [captureAndEmit]) and also reacts to triggers
 * published on [DeviceStateEvents]. Incoming triggers are coalesced with a
 * short debounce so a burst of related system broadcasts (e.g. connectivity +
 * airplane-mode + Wi-Fi all firing together) produces one snapshot instead of
 * waking the radio for each one.
 */
class DeviceStateCollector(
    private val provider: DeviceStateProvider,
    private val events: DeviceStateEvents,
    private val scope: CoroutineScope,
) : DataCollector<DeviceStatePayload> {

    override val collectorId: String = "device_state_collector"
    override val collectorType: String = "DEVICE_STATE"

    @Volatile
    private var active: Boolean = true
    override val isEnabled: Boolean
        get() = active

    private val _dataStream = MutableSharedFlow<DeviceStatePayload>(replay = 1, extraBufferCapacity = 16)
    override val dataStream = _dataStream.asSharedFlow()

    private var debounceJob: Job? = null

    /**
     * Starts collecting and subscribes to the event bus. Emits an immediate
     * startup snapshot, then event-driven snapshots after a quiet period so
     * bursts of related broadcasts are collapsed into one.
     */
    @OptIn(FlowPreview::class)
    override fun startCollecting() {
        active = true
        if (debounceJob != null) return

        debounceJob = scope.launch {
            emitData(provider.capture(DeviceStateTrigger.STARTUP))

            events.triggers
                .debounce(DEBOUNCE)
                .onEach { trigger -> emitData(provider.capture(trigger)) }
                .collect { }
        }
    }

    override fun stopCollecting() {
        active = false
        debounceJob?.cancel()
        debounceJob = null
    }

    override fun emitData(data: DeviceStatePayload) {
        if (active) {
            _dataStream.tryEmit(data)
        }
    }

    /** Reads the current device state without emitting it downstream. */
    suspend fun capture(trigger: String? = null): DeviceStatePayload = provider.capture(trigger)

    /** Reads the current device state and emits it to [dataStream]. */
    suspend fun captureAndEmit(trigger: String? = DeviceStateTrigger.MANUAL): DeviceStatePayload =
        capture(trigger).also { emitData(it) }

    private companion object {
        val DEBOUNCE = 750.milliseconds
    }
}
