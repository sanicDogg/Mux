package com.example.mux.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessLogEntry
import com.example.mux.process.model.ProcessStatus
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.repository.ProcessLogRepository
import com.example.mux.process.tuna.TunaConfig
import com.example.mux.process.tuna.TunaProcessController
import com.example.mux.service.DeviceStateTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LogSourceFilter { ALL, STDOUT, STDERR }
enum class LogTagFilter { ALL, NGINX, TUNA }

data class DashboardUiState(
    val nginxStatus: ProcessStatus = ProcessStatus(tag = ProcessTag.NGINX, isRunning = false),
    val tunaStatus: ProcessStatus = ProcessStatus(tag = ProcessTag.TUNA, isRunning = false),
    val deviceState: DeviceStatePayload? = null,
    val isSendingDeviceState: Boolean = false,
    val sourceFilter: LogSourceFilter = LogSourceFilter.ALL,
    val tagFilter: LogTagFilter = LogTagFilter.ALL,
    val isAutoScrollEnabled: Boolean = true,
    val statusMessage: String? = null
)

class DashboardViewModel(
    private val nativeProcessManager: NativeProcessManager,
    private val nginxProcessController: NginxProcessController,
    private val tunaProcessController: TunaProcessController,
    private val processLogRepository: ProcessLogRepository,
    private val deviceStateTracker: DeviceStateTracker? = null
) : ViewModel() {

    private val _sourceFilter = MutableStateFlow(LogSourceFilter.ALL)
    private val _tagFilter = MutableStateFlow(LogTagFilter.ALL)
    private val _isAutoScrollEnabled = MutableStateFlow(true)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isSendingDeviceState = MutableStateFlow(false)

    private data class DeviceStateSection(
        val payload: DeviceStatePayload?,
        val isSending: Boolean,
        val statusMessage: String?,
    )

    private val deviceStateSection: StateFlow<DeviceStateSection> = combine(
        deviceStateTracker?.lastCaptured ?: MutableStateFlow(null),
        _isSendingDeviceState,
        _statusMessage
    ) { payload, isSending, statusMessage ->
        DeviceStateSection(payload, isSending, statusMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceStateSection(null, false, null)
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        nativeProcessManager.processStatuses,
        deviceStateSection,
        _sourceFilter,
        _tagFilter,
        _isAutoScrollEnabled
    ) { statuses, deviceSection, sourceFilter, tagFilter, isAutoScroll ->
        val nginxStatus = statuses[ProcessTag.NGINX] ?: ProcessStatus(tag = ProcessTag.NGINX, isRunning = false)
        val tunaStatus = statuses[ProcessTag.TUNA] ?: ProcessStatus(tag = ProcessTag.TUNA, isRunning = false)

        DashboardUiState(
            nginxStatus = nginxStatus,
            tunaStatus = tunaStatus,
            deviceState = deviceSection.payload,
            isSendingDeviceState = deviceSection.isSending,
            sourceFilter = sourceFilter,
            tagFilter = tagFilter,
            isAutoScrollEnabled = isAutoScroll,
            statusMessage = deviceSection.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    val filteredLogs: StateFlow<List<ProcessLogEntry>> = combine(
        processLogRepository.logs,
        _sourceFilter,
        _tagFilter
    ) { logs, sourceFilter, tagFilter ->
        logs.filter { entry ->
            val matchSource = when (sourceFilter) {
                LogSourceFilter.ALL -> true
                LogSourceFilter.STDOUT -> entry.source == LogSource.STDOUT
                LogSourceFilter.STDERR -> entry.source == LogSource.STDERR
            }
            val matchTag = when (tagFilter) {
                LogTagFilter.ALL -> true
                LogTagFilter.NGINX -> entry.processTag == ProcessTag.NGINX
                LogTagFilter.TUNA -> entry.processTag == ProcessTag.TUNA
            }
            matchSource && matchTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun startNginx() {
        viewModelScope.launch {
            val result = nginxProcessController.startServer()
            if (result.isFailure) {
                _statusMessage.value = "Failed to start Nginx: ${result.exceptionOrNull()?.message}"
            } else {
                _statusMessage.value = "Nginx process started."
            }
        }
    }

    fun stopNginx() {
        viewModelScope.launch {
            val success = nginxProcessController.stopServer()
            if (!success) {
                _statusMessage.value = "Failed to stop Nginx"
            } else {
                _statusMessage.value = "Nginx process stopped."
            }
        }
    }

    fun startTuna(config: TunaConfig = TunaConfig()) {
        viewModelScope.launch {
            val result = tunaProcessController.startTunnel(config)
            if (result.isFailure) {
                _statusMessage.value = "Failed to start Tuna tunnel: ${result.exceptionOrNull()?.message}"
            } else {
                _statusMessage.value = "Tuna tunnel started."
            }
        }
    }

    fun stopTuna() {
        viewModelScope.launch {
            val success = tunaProcessController.stopTunnel()
            if (!success) {
                _statusMessage.value = "Failed to stop Tuna tunnel"
            } else {
                _statusMessage.value = "Tuna tunnel stopped."
            }
        }
    }

    fun setSourceFilter(filter: LogSourceFilter) {
        _sourceFilter.value = filter
    }

    fun setTagFilter(filter: LogTagFilter) {
        _tagFilter.value = filter
    }

    fun toggleAutoScroll() {
        _isAutoScrollEnabled.update { !it }
    }

    fun sendDeviceState() {
        val tracker = deviceStateTracker ?: return
        viewModelScope.launch {
            _isSendingDeviceState.value = true
            _statusMessage.value = null
            val result = try {
                tracker.captureAndSend()
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _isSendingDeviceState.value = false
            }
            _statusMessage.value = if (result.isSuccess) {
                "Device state sent."
            } else {
                "Device state send failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
        }
    }

    fun clearLogs() {
        processLogRepository.clearLogs()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun getFormattedLogsText(): String {
        return filteredLogs.value.joinToString("\n") { entry ->
            "[${entry.processTag}][${entry.source}] ${entry.formattedTimestamp}: ${entry.message}"
        }
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                nativeProcessManager = app.nativeProcessManager,
                nginxProcessController = app.nginxProcessController,
                tunaProcessController = app.tunaProcessController,
                processLogRepository = app.processLogRepository,
                deviceStateTracker = app.deviceStateTracker
            ) as T
        }
    }
}
