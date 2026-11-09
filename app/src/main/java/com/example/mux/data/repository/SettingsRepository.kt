package com.example.mux.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mux_settings")

/**
 * Repository interface for retrieving and saving dynamic server configuration and enablement settings.
 */
interface SettingsRepository {
    val serverUrl: Flow<String>
    val isNotificationForwardingEnabled: Flow<Boolean>
    val apiKey: Flow<String>
    val deviceStateUrl: Flow<String>
    val isDeviceStateForwardingEnabled: Flow<Boolean>
    val isClipboardCaptureEnabled: Flow<Boolean>

    suspend fun getServerUrl(): String
    suspend fun isNotificationForwardingEnabled(): Boolean
    suspend fun getApiKey(): String
    suspend fun getDeviceStateUrl(): String
    suspend fun isDeviceStateForwardingEnabled(): Boolean
    suspend fun isClipboardCaptureEnabled(): Boolean

    suspend fun setServerUrl(url: String)
    suspend fun setNotificationForwardingEnabled(enabled: Boolean)
    suspend fun setApiKey(key: String)
    suspend fun setDeviceStateUrl(url: String)
    suspend fun setDeviceStateForwardingEnabled(enabled: Boolean)
    suspend fun setClipboardCaptureEnabled(enabled: Boolean)
}

/**
 * Implementation of SettingsRepository backed by AndroidX DataStore Preferences.
 */
class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_NOTIFICATION_FORWARDING_ENABLED = booleanPreferencesKey("notification_forwarding_enabled")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_DEVICE_STATE_URL = stringPreferencesKey("device_state_url")
        val KEY_DEVICE_STATE_FORWARDING_ENABLED = booleanPreferencesKey("device_state_forwarding_enabled")
        val KEY_CLIPBOARD_CAPTURE_ENABLED = booleanPreferencesKey("clipboard_capture_enabled")

        const val DEFAULT_SERVER_URL = "http://192.168.1.100:8080/api/pushes"
        const val DEFAULT_DEVICE_STATE_URL = "http://192.168.1.100:8080/api/device-state"
        const val DEFAULT_FORWARDING_ENABLED = true
        const val DEFAULT_DEVICE_STATE_FORWARDING_ENABLED = true
        const val DEFAULT_CLIPBOARD_CAPTURE_ENABLED = false
    }

    override val serverUrl: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
        }

    override val isNotificationForwardingEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_NOTIFICATION_FORWARDING_ENABLED] ?: DEFAULT_FORWARDING_ENABLED
        }

    override val apiKey: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_API_KEY] ?: ""
        }

    override val deviceStateUrl: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DEVICE_STATE_URL] ?: DEFAULT_DEVICE_STATE_URL
        }

    override val isDeviceStateForwardingEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DEVICE_STATE_FORWARDING_ENABLED] ?: DEFAULT_DEVICE_STATE_FORWARDING_ENABLED
        }

    override val isClipboardCaptureEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CLIPBOARD_CAPTURE_ENABLED] ?: DEFAULT_CLIPBOARD_CAPTURE_ENABLED
        }

    override suspend fun getServerUrl(): String {
        return serverUrl.first()
    }

    override suspend fun isNotificationForwardingEnabled(): Boolean {
        return isNotificationForwardingEnabled.first()
    }

    override suspend fun getApiKey(): String {
        return apiKey.first()
    }

    override suspend fun getDeviceStateUrl(): String {
        return deviceStateUrl.first()
    }

    override suspend fun isDeviceStateForwardingEnabled(): Boolean {
        return isDeviceStateForwardingEnabled.first()
    }

    override suspend fun isClipboardCaptureEnabled(): Boolean {
        return isClipboardCaptureEnabled.first()
    }

    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url
        }
    }

    override suspend fun setNotificationForwardingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_FORWARDING_ENABLED] = enabled
        }
    }

    override suspend fun setApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_API_KEY] = key
        }
    }

    override suspend fun setDeviceStateUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_STATE_URL] = url
        }
    }

    override suspend fun setDeviceStateForwardingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_STATE_FORWARDING_ENABLED] = enabled
        }
    }

    override suspend fun setClipboardCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLIPBOARD_CAPTURE_ENABLED] = enabled
        }
    }
}
