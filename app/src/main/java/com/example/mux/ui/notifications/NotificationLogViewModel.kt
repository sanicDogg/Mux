package com.example.mux.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.data.repository.NotificationLogRepository
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.service.AppNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class NotificationStatusFilter { ALL, SUCCESS, FAILED }

data class NotificationLogUiState(
    val logs: List<NotificationLogEntry> = emptyList(),
    val filteredLogs: List<NotificationLogEntry> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: NotificationStatusFilter = NotificationStatusFilter.ALL,
    val selectedEntry: NotificationLogEntry? = null,
    val isServiceConnected: Boolean = false,
    val serverEndpointUrl: String = "",
    val isResending: Boolean = false,
    val resendResult: String? = null,
    val statusMessage: String? = null
)

class NotificationLogViewModel(
    private val notificationLogRepository: NotificationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val dataForwarder: DataForwarder,
    private val isServiceConnectedFlow: StateFlow<Boolean> = AppNotificationListenerService.isServiceConnected
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow(NotificationStatusFilter.ALL)
    private val _selectedEntry = MutableStateFlow<NotificationLogEntry?>(null)
    private val _isResending = MutableStateFlow(false)
    private val _resendResult = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationLogUiState> = combine(
        notificationLogRepository.logs,
        _searchQuery,
        _statusFilter,
        _selectedEntry,
        isServiceConnectedFlow
    ) { logs, query, statusFilter, selected, isConnected ->
        val endpointUrl = settingsRepository.getServerUrl()

        val filtered = logs.filter { entry ->
            val payload = entry.payload
            val matchesQuery = query.isBlank() ||
                (payload.title?.contains(query, ignoreCase = true) == true) ||
                (payload.text?.contains(query, ignoreCase = true) == true) ||
                (payload.appName?.contains(query, ignoreCase = true) == true) ||
                (payload.packageName.contains(query, ignoreCase = true)) ||
                (payload.id.contains(query, ignoreCase = true))

            val matchesStatus = when (statusFilter) {
                NotificationStatusFilter.ALL -> true
                NotificationStatusFilter.SUCCESS -> entry.status.isSuccess
                NotificationStatusFilter.FAILED -> !entry.status.isSuccess
            }

            matchesQuery && matchesStatus
        }

        NotificationLogUiState(
            logs = logs,
            filteredLogs = filtered,
            searchQuery = query,
            statusFilter = statusFilter,
            selectedEntry = selected,
            isServiceConnected = isConnected,
            serverEndpointUrl = endpointUrl,
            isResending = _isResending.value,
            resendResult = _resendResult.value,
            statusMessage = _statusMessage.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationLogUiState()
    )

    fun onAction(action: NotificationAction) {
        when (action) {
            is NotificationAction.Search -> _searchQuery.value = action.query
            is NotificationAction.FilterStatus -> _statusFilter.value = action.status
            is NotificationAction.SelectEntry -> _selectedEntry.value = action.entry
            is NotificationAction.ResendNotification -> resendNotification(action.entry)
            NotificationAction.ClearLogs -> {
                notificationLogRepository.clearLogs()
                _selectedEntry.value = null
            }
            NotificationAction.ClearStatusMessage -> {
                _statusMessage.value = null
                _resendResult.value = null
            }
        }
    }

    private fun resendNotification(entry: NotificationLogEntry) {
        viewModelScope.launch {
            _isResending.value = true
            _resendResult.value = null
            try {
                val jsonPayload = Json.encodeToString(NotificationPayload.serializer(), entry.payload)
                val targetUrl = settingsRepository.getServerUrl()
                val result = dataForwarder.forwardDataToEndpoint(jsonPayload, targetUrl)

                val newStatus = if (result.isSuccess) {
                    _resendResult.value = "Payload forwarded successfully to $targetUrl"
                    ForwardingStatus(isSuccess = true)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    _resendResult.value = "Resend failed: $errorMsg"
                    ForwardingStatus(isSuccess = false, errorMessage = errorMsg)
                }

                val newEntry = NotificationLogEntry(
                    payload = entry.payload.copy(id = "${entry.payload.id}_resend_${System.currentTimeMillis()}"),
                    status = newStatus
                )
                notificationLogRepository.addLog(newEntry)
                if (_selectedEntry.value?.payload?.id == entry.payload.id) {
                    _selectedEntry.value = newEntry
                }
            } catch (e: Exception) {
                _resendResult.value = "Error during resend: ${e.message}"
            } finally {
                _isResending.value = false
            }
        }
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationLogViewModel(
                notificationLogRepository = app.notificationLogRepository,
                settingsRepository = app.settingsRepository,
                dataForwarder = app.dataForwarder
            ) as T
        }
    }
}
