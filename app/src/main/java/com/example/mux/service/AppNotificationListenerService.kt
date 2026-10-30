package com.example.mux.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.mux.data.collector.NotificationDataCollector
import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.forwarder.KtorDataForwarder
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.InMemoryNotificationLogRepository
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.data.repository.NotificationLogRepository
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Android NotificationListenerService implementation for intercepting system notifications,
 * extracting payload details into [NotificationPayload], and forwarding via [DataForwarder].
 */
class AppNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dataForwarder: DataForwarder
    val notificationCollector = NotificationDataCollector()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepositoryImpl(applicationContext)
        dataForwarder = KtorDataForwarder(settingsRepository)
        notificationCollector.startCollecting()
        Log.d(TAG, "AppNotificationListenerService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "NotificationListenerService connected to system")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        _isServiceConnected.value = false
        Log.w(TAG, "NotificationListenerService disconnected from system")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        if (pkgName == packageName) return

        serviceScope.launch {
            var payload: NotificationPayload? = null
            try {
                if (!settingsRepository.isNotificationForwardingEnabled()) {
                    Log.d(TAG, "Notification forwarding is disabled in settings. Skipping $pkgName")
                    return@launch
                }

                payload = extractNotificationPayload(sbn)
                _lastCapturedNotification.value = payload
                notificationCollector.emitData(payload)

                // Show the row immediately, then resolve its forwarding status in the background.
                logRepository.upsertLog(NotificationLogEntry(payload, ForwardingStatus.pending()))

                val jsonPayload = Json.encodeToString(NotificationPayload.serializer(), payload)
                Log.d(TAG, "Captured notification from $pkgName. Forwarding payload: $jsonPayload")

                val result = dataForwarder.forwardData(jsonPayload)
                val status = if (result.isSuccess) {
                    Log.i(TAG, "Successfully forwarded notification payload for ${payload.id}")
                    ForwardingStatus(isSuccess = true)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Failed to forward notification payload for ${payload.id}: $err")
                    ForwardingStatus(isSuccess = false, errorMessage = err)
                }

                logRepository.updateStatus(payload.id, status)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from $pkgName", e)
                // Resolve any pending row so it never stays stuck in the loading state.
                val id = payload?.id
                if (id != null) {
                    runCatching {
                        logRepository.updateStatus(id, ForwardingStatus(isSuccess = false, errorMessage = e.message))
                    }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationCollector.stopCollecting()
        _isServiceConnected.value = false
        instance = null
        serviceScope.cancel()
        if (::dataForwarder.isInitialized && dataForwarder is KtorDataForwarder) {
            (dataForwarder as KtorDataForwarder).close()
        }
        Log.d(TAG, "AppNotificationListenerService destroyed")
    }

    suspend fun fetchAndSendActiveNotifications() = withContext(Dispatchers.IO) {
        val activeNotifs = try {
            activeNotifications
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active notifications", e)
            null
        } ?: return@withContext

        if (!settingsRepository.isNotificationForwardingEnabled()) {
            Log.d(TAG, "Notification forwarding is disabled in settings. Skipping active notifications.")
            return@withContext
        }

        val payloads = activeNotifs.mapNotNull { sbn ->
            val pkgName = sbn.packageName ?: return@mapNotNull null
            if (pkgName == packageName) return@mapNotNull null
            extractNotificationPayload(sbn)
        }

        if (payloads.isEmpty()) return@withContext

        // Show every active notification immediately, then resolve their shared status.
        val pendingEntries = payloads.map { NotificationLogEntry(it, ForwardingStatus.pending()) }
        pendingEntries.forEach { logRepository.upsertLog(it) }

        val jsonPayloadArray = Json.encodeToString(payloads)
        Log.d(TAG, "Captured active notifications. Forwarding array payload: $jsonPayloadArray")

        val result = dataForwarder.forwardData(jsonPayloadArray)
        val status = if (result.isSuccess) {
            Log.i(TAG, "Successfully forwarded array payload of ${payloads.size} active notifications")
            ForwardingStatus(isSuccess = true)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Unknown error"
            Log.e(TAG, "Failed to forward array payload of ${payloads.size} active notifications: $err")
            ForwardingStatus(isSuccess = false, errorMessage = err)
        }

        payloads.forEach { logRepository.updateStatus(it.id, status) }
    }

    private fun extractNotificationPayload(sbn: StatusBarNotification): NotificationPayload {
        val notification = sbn.notification
        val extras = notification?.extras

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val appName = try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(sbn.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(sbn.packageName, 0)
            }
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification?.channelId
        } else {
            null
        }

        val isOngoing = (notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0
        val timestampIso = formatIso8601(sbn.postTime)

        return NotificationPayload(
            id = sbn.key ?: "${sbn.packageName}_${sbn.id}_${sbn.postTime}",
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            postTime = sbn.postTime,
            timestampIso = timestampIso,
            channelId = channelId,
            notificationKey = sbn.key,
            type = "NOTIFICATION",
            isOngoing = isOngoing
        )
    }

    private fun formatIso8601(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timeMillis))
    }

    companion object {
        private const val TAG = "NotificationEngine"

        var instance: AppNotificationListenerService? = null
            private set

        val logRepository: NotificationLogRepository = InMemoryNotificationLogRepository()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastCapturedNotification = MutableStateFlow<NotificationPayload?>(null)
        val lastCapturedNotification: StateFlow<NotificationPayload?> = _lastCapturedNotification.asStateFlow()
    }
}
