package com.example.mux.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Snapshot of the device's connectivity, audio, and hardware state.
 *
 * Serialized field names mirror the `deviceState` object consumed by the
 * frontend dashboard so the JSON is wire-compatible without any remapping.
 */
@Immutable
@Serializable
data class DeviceStatePayload(
    @SerialName("batteryLevel")
    val batteryLevel: Int,

    @SerialName("isWifiConnected")
    val isWifiConnected: Boolean,

    @SerialName("wifiName")
    val wifiName: String? = null,

    @SerialName("isCellularConnected")
    val isCellularConnected: Boolean = false,

    @SerialName("signalStrength")
    val signalStrength: String = SIGNAL_NONE,

    @SerialName("cellularOperator")
    val cellularOperator: String? = null,

    @SerialName("isAirplaneModeOn")
    val isAirplaneModeOn: Boolean = false,

    @SerialName("isBluetoothOn")
    val isBluetoothOn: Boolean = false,

    @SerialName("isVpnActive")
    val isVpnActive: Boolean = false,

    @SerialName("isMediaPlaying")
    val isMediaPlaying: Boolean = false,

    @SerialName("isHeadphonesConnected")
    val isHeadphonesConnected: Boolean = false,

    @SerialName("volumeLevel")
    val volumeLevel: Int = 0,

    @SerialName("volumeMax")
    val volumeMax: Int = 0,

    @SerialName("volumePercent")
    val volumePercent: Int = 0,

    /**
     * Only populated when the user opts in to clipboard capture. Reads return
     * `null` on Android 10+ whenever the app is not the focused app (or the
     * default IME), which is the platform's anti-keylogger protection.
     */
    @SerialName("clipboardText")
    val clipboardText: String? = null,

    @SerialName("clipboardCapturedAt")
    val clipboardCapturedAt: Long? = null,

    @SerialName("timestamp")
    val timestamp: Long,

    @SerialName("timestampIso")
    val timestampIso: String,

    /** Which event produced this snapshot, see [DeviceStateTrigger]. */
    @SerialName("trigger")
    val trigger: String? = null,

    @SerialName("type")
    val type: String = TYPE,
) {
    /**
     * Everything that identifies this snapshot except its capture timestamp and
     * cause. Used to suppress forwarding when an event fired but nothing the
     * frontend cares about actually changed.
     */
    fun contentSignature(): String =
        copy(timestamp = 0L, timestampIso = "", trigger = null).toString()

    companion object {
        const val TYPE = "DEVICE_STATE"

        const val SIGNAL_LOW = "low"
        const val SIGNAL_MEDIUM = "medium"
        const val SIGNAL_HIGH = "high"
        const val SIGNAL_NONE = "none"

        /** Safety cap so a huge clipboard paste cannot blow up the payload. */
        const val MAX_CLIPBOARD_CHARS = 500
    }
}
