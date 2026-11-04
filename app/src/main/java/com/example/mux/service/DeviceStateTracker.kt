package com.example.mux.service

import com.example.mux.data.model.DeviceStatePayload
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of the device-state pipeline for UI consumers.
 *
 * The concrete [DeviceStateEngine] additionally owns lifecycle (start/stop),
 * but the Dashboard only needs to observe the latest snapshot and trigger a
 * manual capture. Keeping this narrow interface makes the ViewModel easy to
 * test with a fake.
 */
interface DeviceStateTracker {
    /** Latest captured snapshot, or null before the first capture. */
    val lastCaptured: StateFlow<DeviceStatePayload?>

    /** Captures the current state and forwards it to the device-state channel. */
    suspend fun captureAndSend(): Result<Unit>
}
