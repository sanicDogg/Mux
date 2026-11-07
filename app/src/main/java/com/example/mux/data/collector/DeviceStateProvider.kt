package com.example.mux.data.collector

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the current device hardware/connectivity state and maps it into a
 * [DeviceStatePayload]. Every read is defensive so a missing sensor, OEM
 * limitation, or missing permission degrades to a sensible default instead of
 * crashing the collector.
 */
class DeviceStateProvider(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * All system-service lookups and permission checks go through the
     * application context so passing an Activity here can never retain it
     * (and its view hierarchy) beyond a single capture call.
     */
    private val appContext: Context = context.applicationContext

    /**
     * Snapshots the full device state off the main thread. Several of the reads
     * below touch system services (telephony, Bluetooth, Wi-Fi) that may do
     * binder calls or blocking work, so the whole capture is dispatched to
     * [Dispatchers.IO] regardless of the caller's context.
     */
    suspend fun capture(trigger: String? = null): DeviceStatePayload =
        withContext(Dispatchers.IO) {
            val wifiName = readWifiName()
            val timestamp = System.currentTimeMillis()
            val includeClipboard = settingsRepository.isClipboardCaptureEnabled()
            val clipboardText = if (includeClipboard) readClipboardText() else null

            DeviceStatePayload(
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
                clipboardText = clipboardText,
                clipboardCapturedAt = if (clipboardText != null) timestamp else null,
                timestamp = timestamp,
                timestampIso = formatIso8601(timestamp),
                trigger = trigger,
            )
        }

    private fun readBatteryLevel(): Int = try {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun readAirplaneMode(): Boolean = try {
        // minSdk is 24, so Settings.Global is always available.
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (_: Exception) {
        false
    }

    /**
     * Resolves the connected Wi-Fi SSID.
     *
     * On Android 10+ (API 29+) the deprecated [WifiManager.connectionInfo] is
     * frequently stale or returns `<unknown ssid>`, so we first try the
     * [NetworkCapabilities.transportInfo] of the active Wi-Fi network and only
     * fall back to [WifiManager.connectionInfo] when that is unavailable (some
     * OEMs / power states don't populate transportInfo). API 24–28 always use
     * [WifiManager.connectionInfo].
     */
    private fun readWifiName(): String? = try {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) return null

        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readWifiSsidFromNetworkCapabilities() ?: readWifiSsidFromConnectionInfo()
        } else {
            readWifiSsidFromConnectionInfo()
        }

        rawSsid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != WIFI_SSID_UNKNOWN }
    } catch (_: Exception) {
        null
    }

    private fun readWifiSsidFromNetworkCapabilities(): String? {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)?.transportInfo as? WifiInfo
        }?.ssid
    }

    @Suppress("DEPRECATION")
    private fun readWifiSsidFromConnectionInfo(): String? {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.connectionInfo?.ssid
    }

    private fun readWifiConnected(): Boolean = try {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun readCellularConnected(): Boolean = try {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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
            val telephonyManager =
                appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
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
        val telephonyManager =
            appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun readCellularOperators(): List<String> = try {
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        val subscriptionManager =
            appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val telephonyManager =
            appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
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
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    /**
     * Name of the Bluetooth device this phone is currently connected to, or an
     * empty string when nothing is connected.
     *
     * Two sources are consulted because no single public API covers every
     * profile: audio sinks catch headphones/speakers/car kits (the common case),
     * while the GATT connection list catches BLE peripherals such as watches and
     * trackers.
     */
    private fun readConnectedBluetoothDeviceName(): String = try {
        readBluetoothAudioSinkName().ifEmpty { readBluetoothGattDeviceName() }
    } catch (_: Exception) {
        ""
    }

    /**
     * Actively routed Bluetooth audio output (A2DP/SCO/LE Audio), if any.
     *
     * [AudioDeviceInfo.getProductName] frequently returns a model/SDP string
     * (e.g. "A063" for AirPods) rather than the user-facing name, so we resolve
     * the remote [android.bluetooth.BluetoothDevice] from its address and read
     * its alias/name instead. If that is unavailable, we degrade to a
     * profile-based friendly name.
     */
    @SuppressLint("MissingPermission")
    private fun readBluetoothAudioSinkName(): String = try {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ""
        val audioDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device -> device.isSink && device.type in BLUETOOTH_AUDIO_TYPES }
            ?: return ""

        // Preferred: map the audio device back to its BluetoothDevice and read
        // the friendly name/alias the user sees in Bluetooth settings.
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioDevice.address
        } else {
            null
        }
        resolveBluetoothDeviceName(address)
            ?.takeIf { it.isNotBlank() }
            // Fallback when the address isn't exposed or names aren't readable.
            ?: friendlyAudioDeviceName(audioDevice)
    } catch (_: Exception) {
        ""
    }

    /** Connected BLE peripheral (watch, tracker, LE accessory), if any. */
    @SuppressLint("MissingPermission")
    private fun readBluetoothGattDeviceName(): String = try {
        if (!canReadBluetoothNames()) return ""
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return ""
        if (bluetoothManager.adapter?.isEnabled != true) return ""
        bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            ?.firstNotNullOfOrNull { device -> device.name?.takeIf { it.isNotBlank() } }
            ?: ""
    } catch (_: Exception) {
        ""
    }

    /**
     * Maps the audio device address back to a [android.bluetooth.BluetoothDevice]
     * and reads its user-facing name, guarding every permission-sensitive call so
     * a missing [Manifest.permission.BLUETOOTH_CONNECT] degrades to `null`
     * instead of throwing a [SecurityException].
     */
    @SuppressLint("MissingPermission")
    private fun resolveBluetoothDeviceName(address: String?): String? {
        if (address.isNullOrBlank()) return null
        if (!canReadBluetoothNames()) return null
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return null
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias
            } else {
                @Suppress("DEPRECATION")
                device.name
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** True when the app may read remote Bluetooth device names/aliases. */
    private fun canReadBluetoothNames(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)

    /** Friendly, human-readable label derived from the audio profile type. */
    private fun friendlyAudioDeviceName(audioDevice: AudioDeviceInfo): String {
        val type = audioDevice.type
        return when {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Headphones"
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Headset"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
            else -> "Bluetooth Device"
        }
    }

    @Suppress("DEPRECATION")
    private fun readVpnActive(): Boolean = try {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.allNetworks?.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun readMediaActive(): Boolean = try {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isMusicActive == true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun readHeadphonesConnected(): Boolean = try {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        (audioManager?.isWiredHeadsetOn == true) || (audioManager?.isBluetoothA2dpOn == true)
    } catch (_: Exception) {
        false
    }

    private fun readVolumeLevel(): Int = try {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun readVolumeMax(): Int = try {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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

    /**
     * Reads the primary clip only when the app owns a visible foreground
     * activity. Starting with Android 13 (API 33), the system shows a toast
     * whenever a background app reads the clipboard, so reading unconditionally
     * from the always-running device-state collector would repeatedly disturb
     * the user. In that case we return `null` and set no capture timestamp.
     */
    private fun readClipboardText(): String? = try {
        if (!isAppInForeground()) return null
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
            ?: return null
        text.take(DeviceStatePayload.MAX_CLIPBOARD_CHARS)
    } catch (_: Exception) {
        null
    }

    /**
     * True only when this process is the foreground UI process. A foreground
     * [android.service.notification.NotificationListenerService] raises the
     * importance to `IMPORTANCE_FOREGROUND_SERVICE` (not `IMPORTANCE_FOREGROUND`),
     * which is correctly treated as "not in foreground" here to avoid the
     * Android 13+ clipboard toast.
     */
    private fun isAppInForeground(): Boolean = try {
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
        val myPid = Process.myPid()
        activityManager.runningAppProcesses
            ?.firstOrNull { it.pid == myPid }
            ?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (_: Exception) {
        false
    }

    private fun formatIso8601(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timeMillis))
    }

    private fun isPermissionGranted(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /**
         * Audio output routes that mean "a Bluetooth device is attached".
         *
         * Base profiles (A2DP/SCO) exist on every supported API level; LE Audio
         * profiles are appended only when the running OS actually defines them.
         */
        val BLUETOOTH_AUDIO_TYPES: Set<Int> = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setOf(
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
            )
        } else {
            emptySet()
        }

        // Android reports this literal when the real SSID is unavailable,
        // typically because location services are off (API 29+) or no permission.
        const val WIFI_SSID_UNKNOWN = "<unknown ssid>"
    }
}
