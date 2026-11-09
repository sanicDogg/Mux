package com.example.mux.data.model

/**
 * Identifies *why* a device-state snapshot was captured.
 *
 * The device-state channel is event-driven, so every forwarded snapshot carries
 * the event that caused it. This lets the frontend decide whether to re-render
 * the whole panel or just patch the affected indicator.
 */
object DeviceStateTrigger {
    /** First snapshot after the collector was attached. */
    const val STARTUP = "STARTUP"

    /** Explicitly requested by the user (button / API call). */
    const val MANUAL = "MANUAL"

    /** App returned to the foreground (activity resumed). */
    const val FOREGROUND = "FOREGROUND"

    const val AIRPLANE_MODE = "AIRPLANE_MODE"
    const val BATTERY = "BATTERY"
    const val CHARGING = "CHARGING"
    const val CONNECTIVITY = "CONNECTIVITY"
    const val BLUETOOTH = "BLUETOOTH"
    const val HEADSET = "HEADSET"
    const val VOLUME = "VOLUME"
    const val RINGER_MODE = "RINGER_MODE"
    const val CLIPBOARD = "CLIPBOARD"
    const val NOTIFICATION = "NOTIFICATION"
}
