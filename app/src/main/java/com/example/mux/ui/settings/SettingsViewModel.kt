package com.example.mux.ui.settings

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isNotificationForwardingEnabled: Boolean = true,
    val deviceStateUrl: String = "",
    val isDeviceStateForwardingEnabled: Boolean = true,
    val isClipboardCaptureEnabled: Boolean = false,
    val packageRules: List<String> = emptyList(),
    val isBlacklistMode: Boolean = true,
    val isSmsForwardingEnabled: Boolean = false,
    val isCallLogForwardingEnabled: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val statusMessage: String? = null
)

private data class RepoSettings(
    val serverUrl: String,
    val apiKey: String,
    val isNotificationForwardingEnabled: Boolean,
    val deviceStateUrl: String,
    val isDeviceStateForwardingEnabled: Boolean,
    val isClipboardCaptureEnabled: Boolean
)

private data class LocalRuleSettings(
    val packageRules: List<String>,
    val isBlacklistMode: Boolean,
    val isSmsForwardingEnabled: Boolean,
    val isCallLogForwardingEnabled: Boolean,
    val isNotificationListenerGranted: Boolean,
    val statusMessage: String?
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _packageRules = MutableStateFlow<List<String>>(
        listOf("com.android.systemui", "com.google.android.gms")
    )
    private val _isBlacklistMode = MutableStateFlow(true)
    private val _isSmsForwardingEnabled = MutableStateFlow(false)
    private val _isCallLogForwardingEnabled = MutableStateFlow(false)
    private val _isNotificationListenerGranted = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val repoSettingsFlow = combine(
        combine(
            settingsRepository.serverUrl,
            settingsRepository.apiKey,
            settingsRepository.isNotificationForwardingEnabled
        ) { url, key, enabled -> Triple(url, key, enabled) },
        settingsRepository.deviceStateUrl,
        settingsRepository.isDeviceStateForwardingEnabled,
        settingsRepository.isClipboardCaptureEnabled
    ) { base, deviceStateUrl, deviceStateEnabled, clipboardEnabled ->
        RepoSettings(
            serverUrl = base.first,
            apiKey = base.second,
            isNotificationForwardingEnabled = base.third,
            deviceStateUrl = deviceStateUrl,
            isDeviceStateForwardingEnabled = deviceStateEnabled,
            isClipboardCaptureEnabled = clipboardEnabled
        )
    }

    private val localSettingsFlow = combine(
        _packageRules,
        _isBlacklistMode,
        _isSmsForwardingEnabled,
        _isCallLogForwardingEnabled,
        _isNotificationListenerGranted
    ) { rules, isBlacklist, sms, callLog, granted ->
        LocalRuleSettings(
            packageRules = rules,
            isBlacklistMode = isBlacklist,
            isSmsForwardingEnabled = sms,
            isCallLogForwardingEnabled = callLog,
            isNotificationListenerGranted = granted,
            statusMessage = _statusMessage.value
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        repoSettingsFlow,
        localSettingsFlow,
        _statusMessage
    ) { repo, local, msg ->
        SettingsUiState(
            serverUrl = repo.serverUrl,
            apiKey = repo.apiKey,
            isNotificationForwardingEnabled = repo.isNotificationForwardingEnabled,
            deviceStateUrl = repo.deviceStateUrl,
            isDeviceStateForwardingEnabled = repo.isDeviceStateForwardingEnabled,
            isClipboardCaptureEnabled = repo.isClipboardCaptureEnabled,
            packageRules = local.packageRules,
            isBlacklistMode = local.isBlacklistMode,
            isSmsForwardingEnabled = local.isSmsForwardingEnabled,
            isCallLogForwardingEnabled = local.isCallLogForwardingEnabled,
            isNotificationListenerGranted = local.isNotificationListenerGranted,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun checkPermission(context: Context) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isGranted = flat != null && flat.contains(context.packageName)
        _isNotificationListenerGranted.value = isGranted
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setServerUrl(url)
            _statusMessage.value = "Server URL updated."
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setApiKey(key)
            _statusMessage.value = "API Key / Auth Token updated."
        }
    }

    fun setNotificationForwardingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationForwardingEnabled(enabled)
            _statusMessage.value = if (enabled) "Notification forwarding enabled." else "Notification forwarding disabled."
        }
    }

    fun setDeviceStateUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setDeviceStateUrl(url)
            _statusMessage.value = "Device-state endpoint updated."
        }
    }

    fun setDeviceStateForwardingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDeviceStateForwardingEnabled(enabled)
            _statusMessage.value = if (enabled) "Device-state forwarding enabled." else "Device-state forwarding disabled."
        }
    }

    fun setClipboardCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardCaptureEnabled(enabled)
            _statusMessage.value = if (enabled) "Clipboard capture enabled." else "Clipboard capture disabled."
        }
    }

    fun addPackageRule(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return
        _packageRules.update { current ->
            if (current.contains(trimmed)) current else current + trimmed
        }
        _statusMessage.value = "Package '$trimmed' added to rule list."
    }

    fun removePackageRule(packageName: String) {
        _packageRules.update { current ->
            current.filterNot { it == packageName }
        }
        _statusMessage.value = "Package '$packageName' removed from rule list."
    }

    fun setPackageRuleMode(isBlacklist: Boolean) {
        _isBlacklistMode.value = isBlacklist
        _statusMessage.value = if (isBlacklist) "Set to Blacklist mode." else "Set to Whitelist mode."
    }

    fun toggleSmsForwarding(enabled: Boolean) {
        _isSmsForwardingEnabled.value = enabled
        _statusMessage.value = if (enabled) "SMS Forwarding enabled (Modular Placeholder)." else "SMS Forwarding disabled."
    }

    fun toggleCallLogForwarding(enabled: Boolean) {
        _isCallLogForwardingEnabled.value = enabled
        _statusMessage.value = if (enabled) "Call Log Forwarding enabled (Modular Placeholder)." else "Call Log Forwarding disabled."
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsRepository = app.settingsRepository
            ) as T
        }
    }
}
