package com.example.mux.ui.notifications.fast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.R
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.ui.components.FilterChipGroup
import com.example.mux.ui.components.ListenerStatusHeader
import com.example.mux.ui.components.PermissionBanner
import com.example.mux.ui.components.RawJsonPayload
import com.example.mux.ui.notifications.fast.NotificationStatusFilter
import com.example.mux.ui.theme.MuxTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TYPE_SUCCESS = "success"
private const val TYPE_FAILED = "failed"
private const val TYPE_PENDING = "pending"

private val SuccessGreen = Color(0xFF4CAF50)
private val FailedRed = Color(0xFFF44336)

/**
 * One formatter per thread, shared by every row. `SimpleDateFormat` compiles its pattern
 * on construction, so we avoid per-row allocation entirely.
 */
private val timeFormat = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}

private fun entryContentType(entry: NotificationLogEntry): String = when {
    entry.status.isPending -> TYPE_PENDING
    entry.status.isSuccess -> TYPE_SUCCESS
    else -> TYPE_FAILED
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FastNotificationScreen(
    viewModel: FastNotificationViewModel,
    isNotificationListenerGranted: Boolean,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val logList by viewModel.logList.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.onAction(FastNotificationAction.ClearStatusMessage)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isNotificationListenerGranted) {
            PermissionBanner(isGranted = false, modifier = Modifier.padding(16.dp))
        }

        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                FastNotificationListPane(
                    logList = logList,
                    header = header,
                    searchQuery = state.searchQuery,
                    statusFilter = state.statusFilter,
                    selectedId = state.selectedEntry?.payload?.id,
                    onAction = { action ->
                        if (action is FastNotificationAction.SelectEntry && action.id != null) {
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, action.id)
                            }
                        }
                        viewModel.onAction(action)
                    }
                )
            },
            detailPane = {
                FastNotificationInspectorPane(
                    entry = state.selectedEntry,
                    selectedJson = state.selectedJson,
                    isResending = state.isResending,
                    onAction = { viewModel.onAction(it) },
                    onBackClick = {
                        scope.launch {
                            if (navigator.canNavigateBack()) {
                                navigator.navigateBack()
                            } else {
                                viewModel.onAction(FastNotificationAction.SelectEntry(null))
                            }
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun FastNotificationListPane(
    logList: List<NotificationLogEntry>,
    header: FastListenerHeader,
    searchQuery: String,
    statusFilter: NotificationStatusFilter,
    selectedId: String?,
    onAction: (FastNotificationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FastStatusHeader(header = header, onAction = onAction)

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onAction(FastNotificationAction.Search(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            },
            singleLine = true
        )

        // Status Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allLabel = stringResource(R.string.filter_all)
            val successLabel = stringResource(R.string.filter_success)
            val failedLabel = stringResource(R.string.filter_failed)

            Text(text = stringResource(R.string.status_label), style = MaterialTheme.typography.labelMedium)
            FilterChipGroup(
                options = NotificationStatusFilter.entries,
                selectedOption = statusFilter,
                onOptionSelected = { onAction(FastNotificationAction.FilterStatus(it)) },
                labelProvider = {
                    when (it) {
                        NotificationStatusFilter.ALL -> allLabel
                        NotificationStatusFilter.SUCCESS -> successLabel
                        NotificationStatusFilter.FAILED -> failedLabel
                    }
                }
            )
        }

        // Notification List
        if (logList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = logList,
                    key = { it.payload.id },
                    contentType = { entryContentType(it) }
                ) { entry ->
                    FastNotificationRow(
                        entry = entry,
                        isSelected = entry.payload.id == selectedId,
                        onClick = { onAction(FastNotificationAction.SelectEntry(entry.payload.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FastStatusHeader(
    header: FastListenerHeader,
    onAction: (FastNotificationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = header.isServiceConnected && header.isForwardingEnabled
    val inactiveLabel = if (!header.isServiceConnected) {
        stringResource(R.string.listener_disconnected)
    } else {
        stringResource(R.string.notification_forwarding_disabled)
    }

    ListenerStatusHeader(
        isActive = isActive,
        activeLabel = stringResource(R.string.listener_active),
        inactiveLabel = inactiveLabel,
        targetLabel = stringResource(R.string.forwarding_target, header.serverEndpointUrl),
        modifier = modifier
    ) {
        IconButton(onClick = { onAction(FastNotificationAction.FetchActiveNotifications) }) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.acc_fetch_active)
            )
        }
        IconButton(onClick = { onAction(FastNotificationAction.ClearLogs) }) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.acc_clear_logs)
            )
        }
    }
}

@Composable
private fun FastNotificationRow(
    entry: NotificationLogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = entry.payload
    val isPending = entry.status.isPending
    val isSuccess = entry.status.isSuccess
    val formattedTime = remember(payload.postTime) {
        timeFormat.get()!!.format(Date(payload.postTime))
    }

    val backgroundColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val titleColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    val statusIcon = when {
        isPending -> Icons.Rounded.Schedule
        isSuccess -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Error
    }
    val statusTint = when {
        isPending -> MaterialTheme.colorScheme.onSurfaceVariant
        isSuccess -> SuccessGreen
        else -> FailedRed
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = payload.appName ?: payload.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!payload.title.isNullOrBlank()) {
                Text(
                    text = payload.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            if (!payload.text.isNullOrBlank()) {
                Text(
                    text = payload.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FastNotificationInspectorPane(
    entry: NotificationLogEntry?,
    selectedJson: String?,
    isResending: Boolean,
    onAction: (FastNotificationAction) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (entry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.select_notification_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val payload = entry.payload

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.acc_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inspector_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = payload.appName ?: payload.packageName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.package_format, payload.packageName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.id_format, payload.id),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.posted_at_format, payload.timestampIso),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (payload.channelId != null) {
                        Text(
                            text = stringResource(R.string.channel_id_format, payload.channelId),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val none = stringResource(R.string.none_placeholder)
                    Text(
                        text = stringResource(R.string.message_details),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.title_format, payload.title ?: none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.text_format, payload.text ?: none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!payload.subText.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.subtext_format, payload.subText),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!payload.bigText.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.bigtext_format, payload.bigText),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (entry.status.isPending) Color(0xFFFFF8E1)
                            else if (entry.status.isSuccess) Color(0xFFE8F5E9)
                            else Color(0xFFFFEBEE),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                entry.status.isPending -> Icons.Rounded.Schedule
                                entry.status.isSuccess -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.Error
                            },
                            contentDescription = null,
                            tint = when {
                                entry.status.isPending -> Color(0xFFF9A825)
                                entry.status.isSuccess -> SuccessGreen
                                else -> FailedRed
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                when {
                                    entry.status.isPending -> R.string.forwarding_status_pending
                                    entry.status.isSuccess -> R.string.forwarding_status_success
                                    else -> R.string.forwarding_status_failed
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                entry.status.isPending -> Color(0xFFF57F17)
                                entry.status.isSuccess -> Color(0xFF2E7D32)
                                else -> Color(0xFFC62828)
                            }
                        )
                    }

                    if (!entry.status.errorMessage.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.error_format, entry.status.errorMessage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828)
                        )
                    }
                }

                Button(
                    onClick = { onAction(FastNotificationAction.ResendNotification(payload.id)) },
                    enabled = !isResending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isResending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.forwarding_payload))
                    } else {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.resend_payload))
                    }
                }

                RawJsonPayload(json = selectedJson ?: payload.toString())
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
private fun FastNotificationInspectorPanePreview() {
    val samplePayload = NotificationPayload(
        id = "sample_123",
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        title = "John Doe",
        text = "Hey, are you coming to the meeting?",
        postTime = System.currentTimeMillis(),
        timestampIso = "2026-08-27T10:00:00.000Z"
    )
    MuxTheme {
        FastNotificationInspectorPane(
            entry = NotificationLogEntry(payload = samplePayload, status = ForwardingStatus(isSuccess = true)),
            selectedJson = null,
            isResending = false,
            onAction = {},
            onBackClick = {}
        )
    }
}
