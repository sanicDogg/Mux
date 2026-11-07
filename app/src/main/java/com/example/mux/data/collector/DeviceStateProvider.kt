package com.example.mux.data.collector

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads the current device hardware/connectivity state and maps it into a
 * [DeviceStatePayload]. Every read is defensive so a missing sensor, OEM
 * limitation, or missing permission degrades to a sensible default instead of
 * crashing the collector.
 */
class DeviceStateProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun capture(trigger: String? = null): DeviceStatePayload {
        val wifiName = readWifiName()
        val timestamp = System.currentTimeMillis()
        val includeClipboard = settingsRepository.isClipboardCaptureEnabled()

        return DeviceStatePayload(
            batteryLevel = readBatteryLevel(),
            isWifiConnected = readWifiConnected(),
            wifiName = wifiName,
            isCellularConnected = readCellularConnected(),
            signalStrength = readSignalStrength(),
            cellularOperator = readCellularOperator(),
            cellularOperators = readCellularOperators(),
            isAirplaneModeOn = readAirplaneMode(),
            isBluetoothOn = readBluetoothEnabled(),
            bluetoothName = readConnectedBluetoothDeviceName(),
            isVpnActive = readVpnActive(),
            isMediaPlaying = readMediaActive(),
            isHeadphonesConnected = readHeadphonesConnected(),
            volumeLevel = readVolumeLevel(),
            volumeMax = readVolumeMax(),
            volumePercent = readVolumePercent(),
            clipboardText = if (includeClipboard) readClipboardText() else null,
            clipboardCapturedAt = if (includeClipboard) timestamp else null,
            timestamp = timestamp,
            timestampIso = formatIso8601(timestamp),
            trigger = trigger,
        )
    }

    private fun readBatteryLevel(): Int = try {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun readAirplaneMode(): Boolean = try {
        // minSdk is 24, so Settings.Global is always available.
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun readWifiName(): String? = try {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != WIFI_SSID_UNKNOWN }
    } catch (_: Exception) {
        null
    }

    private fun readWifiConnected(): Boolean = try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun readCellularConnected(): Boolean = try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun readSignalStrength(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return DeviceStatePayload.SIGNAL_NONE
        }
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            when (telephonyManager?.signalStrength?.level ?: 0) {
                in 4..Int.MAX_VALUE -> DeviceStatePayload.SIGNAL_HIGH
                3 -> DeviceStatePayload.SIGNAL_MEDIUM
                1, 2 -> DeviceStatePayload.SIGNAL_LOW
                else -> DeviceStatePayload.SIGNAL_NONE
            }
        } catch (_: Exception) {
            DeviceStatePayload.SIGNAL_NONE
        }
    }

    private fun readCellularOperator(): String? = try {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun readCellularOperators(): List<String> = try {
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if ((subscriptionManager == null) || (telephonyManager == null)) return emptyList()
        subscriptionManager.activeSubscriptionInfoList
            ?.asSequence()
            ?.mapNotNull { info ->
                telephonyManager.createForSubscriptionId(info.subscriptionId)
                    .networkOperatorName
                    ?.takeIf { it.isNotBlank() }
            }
            ?.distinct()
            ?.toList()
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun readBluetoothEnabled(): Boolean = try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    /**
     * Name of the Bluetooth device this phone is currently connected to, or an
     * empty string when nothing is connected.
     *
     * Two sources are consulted because no single public API covers every
     * profile: audio sinks catch headphones/speakers/car kits (the common case,
     * and it needs no permission), while the GATT connection list catches BLE
     * peripherals such as watches and trackers.
     */
    private fun readConnectedBluetoothDeviceName(): String = try {
        readBluetoothAudioSinkName().ifEmpty { readBluetoothGattDeviceName() }
    } catch (_: Exception) {
        ""
    }

    /** Actively routed Bluetooth audio output (A2DP/SCO/LE Audio), if any. */
    private fun readBluetoothAudioSinkName(): String = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ""
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device -> device.isSink && device.type in BLUETOOTH_AUDIO_TYPES }
            ?.productName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: ""
    } catch (_: Exception) {
        ""
    }

    /** Connected BLE peripheral (watch, tracker, LE accessory), if any. */
    @SuppressLint("MissingPermission")
    private fun readBluetoothGattDeviceName(): String = try {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) &&
            !isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            return ""
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return ""
        if (bluetoothManager.adapter?.isEnabled != true) return ""
        bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            ?.firstNotNullOfOrNull { device -> device.name?.takeIf { it.isNotBlank() } }
            ?: ""
    } catch (_: Exception) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun readVpnActive(): Boolean = try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.allNetworks?.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun readMediaActive(): Boolean = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isMusicActive == true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun readHeadphonesConnected(): Boolean = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        (audioManager?.isWiredHeadsetOn == true) || (audioManager?.isBluetoothA2dpOn == true)
    } catch (_: Exception) {
        false
    }

    private fun readVolumeLevel(): Int = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun readVolumeMax(): Int = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun readVolumePercent(): Int {
        val level = readVolumeLevel()
        val max = readVolumeMax()
        if ((level < 0) || (max <= 0)) return -1
        return ((level * 100f) / max).toInt().coerceIn(0, 100)
    }

    private fun readClipboardText(): String? = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?: return null
        text.take(DeviceStatePayload.MAX_CLIPBOARD_CHARS)
    } catch (_: Exception) {
        null
    }

    private fun formatIso8601(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timeMillis))
    }

    private fun isPermissionGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** Audio output routes that mean "a Bluetooth device is attached". */
        val BLUETOOTH_AUDIO_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }

        // Android reports this literal when the real SSID is unavailable,
        // typically because location services are off (API 29+) or no permission.
        const val WIFI_SSID_UNKNOWN = "<unknown ssid>"
    }
}
