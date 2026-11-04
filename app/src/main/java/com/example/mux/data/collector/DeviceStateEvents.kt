package com.example.mux.data.collector

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight, in-process event bus for device-state triggers.
 *
 * Battery-friendly by design: producers (broadcast receivers, system callbacks)
 * only publish a *trigger string* and never read hardware state themselves. A
 * single collector subscribes, coalesces bursts with a debounce, and captures
 * one snapshot per settled event.
 */
class DeviceStateEvents {

    private val _triggers = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val triggers: SharedFlow<String> = _triggers.asSharedFlow()

    /** Publish a trigger. Dropped silently if no subscriber is currently active. */
    fun publish(trigger: String) {
        _triggers.tryEmit(trigger)
    }
}
