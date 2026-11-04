package com.example.mux.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessLogEntry
import com.example.mux.process.model.ProcessStatus
import com.example.mux.data.model.DeviceStatePayload
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.tuna.TunaConfig
import com.example.mux.ui.components.DeviceStateCard
import com.example.mux.ui.components.ProcessStatusCard
import com.example.mux.ui.theme.MuxTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Creating stable lambdas for the content
    val onStartNginx = remember { { viewModel.startNginx() } }
    val onStopNginx = remember { { viewModel.stopNginx() } }
    val onStartTuna = remember { { viewModel.startTuna(TunaConfig()) } }
    val onStopTuna = remember { { viewModel.stopTuna() } }
    val onSendDeviceState = remember { { viewModel.sendDeviceState() } }
    
    val onSourceFilterSelected = remember { { filter: LogSourceFilter -> viewModel.setSourceFilter(filter) } }
    val onTagFilterSelected = remember { { filter: LogTagFilter -> viewModel.setTagFilter(filter) } }
    val onToggleAutoScroll = remember { { viewModel.toggleAutoScroll() } }
    val onClearLogs = remember { { viewModel.clearLogs() } }
    val onCopyLogs = remember { {
        val text = viewModel.getFormattedLogsText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mux Process Logs", text))
        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    } }

    DashboardContent(
        nginxStatus = state.nginxStatus,
        tunaStatus = state.tunaStatus,
        deviceState = state.deviceState,
        isSendingDeviceState = state.isSendingDeviceState,
        logs = logs,
        sourceFilter = state.sourceFilter,
        tagFilter = state.tagFilter,
        isAutoScrollEnabled = state.isAutoScrollEnabled,
        onStartNginx = onStartNginx,
        onStopNginx = onStopNginx,
        onStartTuna = onStartTuna,
        onStopTuna = onStopTuna,
        onSendDeviceState = onSendDeviceState,
        onSourceFilterSelected = onSourceFilterSelected,
        onTagFilterSelected = onTagFilterSelected,
        onToggleAutoScroll = onToggleAutoScroll,
        onClearLogs = onClearLogs,
        onCopyLogs = onCopyLogs,
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    nginxStatus: ProcessStatus,
    tunaStatus: ProcessStatus,
    deviceState: DeviceStatePayload?,
    isSendingDeviceState: Boolean,
    logs: List<ProcessLogEntry>,
    sourceFilter: LogSourceFilter,
    tagFilter: LogTagFilter,
    isAutoScrollEnabled: Boolean,
    onStartNginx: () -> Unit,
    onStopNginx: () -> Unit,
    onStartTuna: () -> Unit,
    onStopTuna: () -> Unit,
    onSendDeviceState: () -> Unit,
    onSourceFilterSelected: (LogSourceFilter) -> Unit,
    onTagFilterSelected: (LogTagFilter) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Process Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProcessStatusCard(
                title = "Nginx Web Server",
                status = nginxStatus,
                subtitle = "Port 8080 • Web Root active",
                onStartClick = onStartNginx,
                onStopClick = onStopNginx,
                modifier = Modifier.weight(1f)
            )

            ProcessStatusCard(
                title = "Tuna Tunnel",
                status = tunaStatus,
                subtitle = "HTTP Tunnel • 127.0.0.1:8080",
                onStartClick = onStartTuna,
                onStopClick = onStopTuna,
                modifier = Modifier.weight(1f)
            )
        }

        // Device state readout + manual capture
        DeviceStateCard(
            deviceState = deviceState,
            isSending = isSendingDeviceState,
            onSendNow = onSendDeviceState
        )

        // Live Terminal Console Section
        ConsoleLogCard(
            logs = logs,
            sourceFilter = sourceFilter,
            tagFilter = tagFilter,
            isAutoScrollEnabled = isAutoScrollEnabled,
            onSourceFilterSelected = onSourceFilterSelected,
            onTagFilterSelected = onTagFilterSelected,
            onToggleAutoScroll = onToggleAutoScroll,
            onClearLogs = onClearLogs,
            onCopyLogs = onCopyLogs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DashboardScreenPreview() {
    MuxTheme {
        DashboardContent(
            nginxStatus = ProcessStatus(tag = ProcessTag.NGINX, isRunning = true, pid = 4321),
            tunaStatus = ProcessStatus(tag = ProcessTag.TUNA, isRunning = false),
            deviceState = null,
            isSendingDeviceState = false,
            logs = listOf(
                ProcessLogEntry(processTag = ProcessTag.NGINX, source = LogSource.STDOUT, message = "[SYSTEM] Started process NGINX (PID: 4321)"),
                ProcessLogEntry(processTag = ProcessTag.NGINX, source = LogSource.STDOUT, message = "2026/08/27 10:00:00 [info] 4321#0: nginx/1.24.0 started"),
                ProcessLogEntry(processTag = ProcessTag.TUNA, source = LogSource.STDERR, message = "[SYSTEM] Failed to start tunnel")
            ),
            sourceFilter = LogSourceFilter.ALL,
            tagFilter = LogTagFilter.ALL,
            isAutoScrollEnabled = true,
            onStartNginx = {},
            onStartTuna = {},
            onStopNginx = {},
            onStopTuna = {},
            onSendDeviceState = {},
            onSourceFilterSelected = {},
            onTagFilterSelected = {},
            onToggleAutoScroll = {},
            onClearLogs = {},
            onCopyLogs = {}
        )
    }
}
