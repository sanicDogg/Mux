package com.example.mux.ui.notifications.fast

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class NotificationStatusFilter { ALL, SUCCESS, FAILED }

private val prettyJson = Json { prettyPrint = true }

/**
 * Rarely changing header state: listener service connection flag + forwarding endpoint.
 * Isolated from the hot list flow so a new notification never invalidates the header.
 */
@Immutable
data class FastListenerHeader(
    val isServiceConnected: Boolean = false,
    val isForwardingEnabled: Boolean = true,
    val serverEndpointUrl: String = ""
)

/**
 * Presentation state for the fast notifications screen.
 * Selection is resolved from the full log list by domain ID, so it survives filtering.
 */
@Immutable
data class FastNotificationUiState(
    val searchQuery: String = "",
    val statusFilter: NotificationStatusFilter = NotificationStatusFilter.ALL,
    val selectedEntry: NotificationLogEntry? = null,
    val selectedJson: String? = null,
    val isResending: Boolean = false,
    val statusMessage: String? = null
)

/**
 * Performance-focused ViewModel for the "Notifications Fast" screen.
 *
 * Three focused [StateFlow]s instead of one monolithic state:
 *  - [logList]  — hot list (de-duplicated by payload.id, searched, status-filtered)
 *  - [header]   — rare listener/endpoint header state
 *  - [uiState]  — search, filter, selection (+ precomputed pretty JSON), resend, status message
 *
 * All CPU-bound work (filtering, de-dup, JSON pretty-print) runs on [Dispatchers.Default].
 */
class FastNotificationViewModel(
    private val notificationLogRepository: NotificationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val dataForwarder: DataForwarder,
    private val isServiceConnectedFlow: StateFlow<Boolean> = AppNotificationListenerService.isServiceConnected,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow(NotificationStatusFilter.ALL)
    private val _selectedId = MutableStateFlow<String?>(null)
    private val _isResending = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    /**
     * Cached pretty JSON for the current selection. Re-encoded only when the resolved
     * entry actually changes, not on every log emission.
     */
    private var cachedSelection: Pair<NotificationLogEntry, String>? = null

    /**
     * Hot list: single O(N) pass keeping only the latest entry per [NotificationPayload.id]
     * (the repository emits newest-first), then applying search + status filter.
     */
    val logList: StateFlow<List<NotificationLogEntry>> = combine(
        notificationLogRepository.logs,
        _searchQuery,
        _statusFilter
    ) { logs, query, statusFilter ->
        withContext(processingDispatcher) {
            filterLogs(logs, query, statusFilter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Rare header state. Uses the DataStore flow (no suspend read inside the transform).
     */
    val header: StateFlow<FastListenerHeader> = combine(
        isServiceConnectedFlow,
        settingsRepository.isNotificationForwardingEnabled,
        settingsRepository.serverUrl
    ) { isConnected, isForwardingEnabled, serverUrl ->
        FastListenerHeader(
            isServiceConnected = isConnected,
            isForwardingEnabled = isForwardingEnabled,
            serverEndpointUrl = serverUrl
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FastListenerHeader())

    /**
     * Resolves the selected entry from the full (unfiltered) log list by ID and
     * precomputes the pretty-printed JSON on [Dispatchers.Default].
     */
    private val selectionFlow: Flow<Pair<NotificationLogEntry?, String?>> = combine(
        notificationLogRepository.logs,
        _selectedId
    ) { logs, selectedId ->
        withContext(processingDispatcher) {
            resolveSelection(logs, selectedId)
        }
    }

    val uiState: StateFlow<FastNotificationUiState> = combine(
        _searchQuery,
        _statusFilter,
        selectionFlow,
        _isResending,
        _statusMessage
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val query = args[0] as String
        val statusFilter = args[1] as NotificationStatusFilter
        val selection = args[2] as Pair<NotificationLogEntry?, String?>
        val isResending = args[3] as Boolean
        val statusMessage = args[4] as String?

        FastNotificationUiState(
            searchQuery = query,
            statusFilter = statusFilter,
            selectedEntry = selection.first,
            selectedJson = selection.second,
            isResending = isResending,
            statusMessage = statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FastNotificationUiState()
    )

    fun onAction(action: FastNotificationAction) {
        when (action) {
            is FastNotificationAction.Search -> _searchQuery.value = action.query
            is FastNotificationAction.FilterStatus -> _statusFilter.value = action.status
            is FastNotificationAction.SelectEntry -> _selectedId.value = action.id
            is FastNotificationAction.ResendNotification -> resendNotification(action.id)
            FastNotificationAction.ClearLogs -> {
                notificationLogRepository.clearLogs()
                _selectedId.value = null
            }
            FastNotificationAction.ClearStatusMessage -> {
                _statusMessage.value = null
            }
            FastNotificationAction.FetchActiveNotifications -> {
                viewModelScope.launch(Dispatchers.IO) {
                    AppNotificationListenerService.instance?.fetchAndSendActiveNotifications()
                }
            }
        }
    }

    /**
     * De-duplicates by [NotificationPayload.id] (latest wins — the repository emits
     * newest-first) and applies the search query + status filter in one pass.
     */
    private fun filterLogs(
        logs: List<NotificationLogEntry>,
        query: String,
        statusFilter: NotificationStatusFilter
    ): List<NotificationLogEntry> {
        if (logs.isEmpty()) return emptyList()

        val seen = HashSet<String>(logs.size)
        val result = ArrayList<NotificationLogEntry>(logs.size)

        for (entry in logs) {
            val payload = entry.payload
            if (!seen.add(payload.id)) continue

            if (query.isNotBlank() && !matchesQuery(payload, query)) continue

            when (statusFilter) {
                NotificationStatusFilter.ALL -> Unit
                NotificationStatusFilter.SUCCESS -> if (!entry.status.isSuccess || entry.status.isPending) continue
                NotificationStatusFilter.FAILED -> if (entry.status.isSuccess || entry.status.isPending) continue
            }

            result.add(entry)
        }
        return result
    }

    private fun matchesQuery(payload: NotificationPayload, query: String): Boolean {
        return payload.title?.contains(query, ignoreCase = true) == true ||
            payload.text?.contains(query, ignoreCase = true) == true ||
            payload.appName?.contains(query, ignoreCase = true) == true ||
            payload.packageName.contains(query, ignoreCase = true) ||
            payload.id.contains(query, ignoreCase = true)
    }

    private fun resolveSelection(
        logs: List<NotificationLogEntry>,
        selectedId: String?
    ): Pair<NotificationLogEntry?, String?> {
        if (selectedId == null) {
            cachedSelection = null
            return null to null
        }

        val entry = logs.firstOrNull { it.payload.id == selectedId }
        if (entry == null) {
            cachedSelection = null
            return null to null
        }

        val cached = cachedSelection
        val json = if (cached?.first == entry) {
            cached.second
        } else {
            val formatted = try {
                prettyJson.encodeToString(NotificationPayload.serializer(), entry.payload)
            } catch (e: Exception) {
                entry.payload.toString()
            }
            cachedSelection = entry to formatted
            formatted
        }
        return entry to json
    }

    private fun resendNotification(id: String) {
        viewModelScope.launch {
            val target = notificationLogRepository.logs.value
                .firstOrNull { it.payload.id == id } ?: return@launch

            _isResending.value = true
            _statusMessage.value = null
            try {
                val jsonPayload = Json.encodeToString(NotificationPayload.serializer(), target.payload)
                val targetUrl = settingsRepository.getServerUrl()
                val result = dataForwarder.forwardDataToEndpoint(jsonPayload, targetUrl)

                val newStatus = if (result.isSuccess) {
                    _statusMessage.value = "Payload forwarded successfully to $targetUrl"
                    ForwardingStatus(isSuccess = true)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    _statusMessage.value = "Resend failed: $errorMsg"
                    ForwardingStatus(isSuccess = false, errorMessage = errorMsg)
                }

                val newEntry = NotificationLogEntry(
                    payload = target.payload.copy(id = "${target.payload.id}_resend_${System.currentTimeMillis()}"),
                    status = newStatus
                )
                notificationLogRepository.addLog(newEntry)
                if (_selectedId.value == id) {
                    _selectedId.value = newEntry.payload.id
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error during resend: ${e.message}"
            } finally {
                _isResending.value = false
            }
        }
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FastNotificationViewModel(
                notificationLogRepository = app.notificationLogRepository,
                settingsRepository = app.settingsRepository,
                dataForwarder = app.dataForwarder
            ) as T
        }
    }
}
