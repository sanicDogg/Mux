package com.example.mux.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessLogEntry
import com.example.mux.process.model.ProcessStatus
import com.example.mux.process.model.ProcessTag
import com.example.mux.process.tuna.TunaConfig
import com.example.mux.ui.components.FilterChipGroup
import com.example.mux.ui.components.PermissionBanner
import com.example.mux.ui.components.ProcessStatusCard
import com.example.mux.ui.theme.MuxTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    isNotificationListenerGranted: Boolean,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    DashboardContent(
        state = state,
        isNotificationListenerGranted = isNotificationListenerGranted,
        onStartNginx = { viewModel.startNginx() },
        onStopNginx = { viewModel.stopNginx() },
        onStartTuna = { viewModel.startTuna(TunaConfig()) },
        onStopTuna = { viewModel.stopTuna() },
        onSourceFilterSelected = { viewModel.setSourceFilter(it) },
        onTagFilterSelected = { viewModel.setTagFilter(it) },
        onToggleAutoScroll = { viewModel.toggleAutoScroll() },
        onClearLogs = { viewModel.clearLogs() },
        onCopyLogs = {
            val text = viewModel.getFormattedLogsText()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Mux Process Logs", text))
            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    isNotificationListenerGranted: Boolean,
    onStartNginx: () -> Unit,
    onStopNginx: () -> Unit,
    onStartTuna: () -> Unit,
    onStopTuna: () -> Unit,
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
        if (!isNotificationListenerGranted) {
            PermissionBanner(isGranted = false)
        }

        // Process Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProcessStatusCard(
                title = "Nginx Web Server",
                status = state.nginxStatus,
                subtitle = "Port 8080 • Web Root active",
                onStartClick = onStartNginx,
                onStopClick = onStopNginx,
                modifier = Modifier.weight(1f)
            )

            ProcessStatusCard(
                title = "Tuna Tunnel",
                status = state.tunaStatus,
                subtitle = "HTTP Tunnel • 127.0.0.1:8080",
                onStartClick = onStartTuna,
                onStopClick = onStopTuna,
                modifier = Modifier.weight(1f)
            )
        }

        // Live Terminal Console Section
        ConsoleLogCard(
            logs = state.filteredLogs,
            sourceFilter = state.sourceFilter,
            tagFilter = state.tagFilter,
            isAutoScrollEnabled = state.isAutoScrollEnabled,
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

@Composable
fun ConsoleLogCard(
    logs: List<ProcessLogEntry>,
    sourceFilter: LogSourceFilter,
    tagFilter: LogTagFilter,
    isAutoScrollEnabled: Boolean,
    onSourceFilterSelected: (LogSourceFilter) -> Unit,
    onTagFilterSelected: (LogTagFilter) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Log Header Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Terminal Console",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = isAutoScrollEnabled,
                        onCheckedChange = { onToggleAutoScroll() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignBottom,
                            contentDescription = "Autoscroll",
                            tint = if (isAutoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onCopyLogs) {
                        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy Logs")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Clear Logs")
                    }
                }
            }

            // Filter Chips
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Source:", style = MaterialTheme.typography.labelSmall)
                    FilterChipGroup(
                        options = LogSourceFilter.entries,
                        selectedOption = sourceFilter,
                        onOptionSelected = onSourceFilterSelected,
                        labelProvider = { it.name }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Tag:", style = MaterialTheme.typography.labelSmall)
                    FilterChipGroup(
                        options = LogTagFilter.entries,
                        selectedOption = tagFilter,
                        onOptionSelected = onTagFilterSelected,
                        labelProvider = { it.name }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Terminal Log View Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color(0xFF1E1E1E), // Dark terminal background
                shape = RoundedCornerShape(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No log output available...",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { entry ->
                            LogItemRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(entry: ProcessLogEntry) {
    val textColor = when {
        entry.message.startsWith("[SYSTEM]") -> Color(0xFFFFD54F) // Yellow
        entry.source == LogSource.STDERR -> Color(0xFFFF8A65) // Light Red/Orange
        entry.processTag == ProcessTag.NGINX -> Color(0xFF81C784) // Green
        entry.processTag == ProcessTag.TUNA -> Color(0xFF4FC3F7) // Cyan
        else -> Color(0xFFE0E0E0)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[${entry.formattedTimestamp}][${entry.processTag}][${entry.source}] ${entry.message}",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DashboardScreenPreview() {
    MuxTheme {
        DashboardContent(
            state = DashboardUiState(
                nginxStatus = ProcessStatus(tag = ProcessTag.NGINX, isRunning = true, pid = 4321),
                tunaStatus = ProcessStatus(tag = ProcessTag.TUNA, isRunning = false),
                filteredLogs = listOf(
                    ProcessLogEntry(processTag = ProcessTag.NGINX, source = LogSource.STDOUT, message = "[SYSTEM] Started process NGINX (PID: 4321)"),
                    ProcessLogEntry(processTag = ProcessTag.NGINX, source = LogSource.STDOUT, message = "2026/08/27 10:00:00 [info] 4321#0: nginx/1.24.0 started"),
                    ProcessLogEntry(processTag = ProcessTag.TUNA, source = LogSource.STDERR, message = "[SYSTEM] Failed to start tunnel")
                )
            ),
            isNotificationListenerGranted = false,
            onStartNginx = {},
            onStopNginx = {},
            onStartTuna = {},
            onStopTuna = {},
            onSourceFilterSelected = {},
            onTagFilterSelected = {},
            onToggleAutoScroll = {},
            onClearLogs = {},
            onCopyLogs = {}
        )
    }
}
