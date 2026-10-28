package com.example.mux.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mux.process.model.LogSource
import com.example.mux.process.model.ProcessLogEntry
import com.example.mux.process.model.ProcessTag
import com.example.mux.ui.components.FilterChipGroup

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

            TerminalLogViewArea(
                logs = logs,
                listState = listState,
                isAutoScrollEnabled = isAutoScrollEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun TerminalLogViewArea(
    logs: List<ProcessLogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isAutoScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(logs.size, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = modifier,
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
                items(logs, key = { it.id }) { entry ->
                    LogItemRow(entry = entry)
                }
            }
        }
    }
}

private val SystemLogColor = Color(0xFFFFD54F)
private val ErrorLogColor = Color(0xFFFF8A65)
private val NginxLogColor = Color(0xFF81C784)
private val TunaLogColor = Color(0xFF4FC3F7)
private val DefaultLogColor = Color(0xFFE0E0E0)

@Composable
fun LogItemRow(entry: ProcessLogEntry) {
    val textColor = when {
        entry.message.startsWith("[SYSTEM]") -> SystemLogColor
        entry.source == LogSource.STDERR -> ErrorLogColor
        entry.processTag == ProcessTag.NGINX -> NginxLogColor
        entry.processTag == ProcessTag.TUNA -> TunaLogColor
        else -> DefaultLogColor
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
