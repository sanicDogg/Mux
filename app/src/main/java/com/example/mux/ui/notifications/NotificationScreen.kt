package com.example.mux.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.data.model.ForwardingStatus
import com.example.mux.data.model.NotificationPayload
import com.example.mux.data.repository.NotificationLogEntry
import com.example.mux.ui.components.FilterChipGroup
import com.example.mux.ui.components.PermissionBanner
import com.example.mux.ui.theme.MuxTheme
import kotlinx.serialization.json.Json

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationLogViewModel,
    isNotificationListenerGranted: Boolean,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.resendResult) {
        state.resendResult?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<NotificationLogEntry>()

    Column(modifier = modifier.fillMaxSize()) {
        if (!isNotificationListenerGranted) {
            PermissionBanner(isGranted = false, modifier = Modifier.padding(16.dp))
        }

        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                NotificationListPane(
                    state = state,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onStatusFilterSelected = { viewModel.setStatusFilter(it) },
                    onSelectEntry = { entry ->
                        viewModel.selectEntry(entry)
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry)
                        }
                    },
                    onClearLogs = { viewModel.clearLogs() }
                )
            },
            detailPane = {
                val currentSelected = (navigator.currentDestination?.contentKey as? NotificationLogEntry)
                    ?: state.selectedEntry

                NotificationInspectorPane(
                    entry = currentSelected,
                    isResending = state.isResending,
                    onResendClick = { entry -> viewModel.resendNotification(entry) },
                    onBackClick = {
                        scope.launch {
                            if (navigator.canNavigateBack()) {
                                navigator.navigateBack()
                            } else {
                                viewModel.clearSelection()
                            }
                        }
                    }
                )
            }
        )
    }
}

@Composable
fun NotificationListPane(
    state: NotificationLogUiState,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterSelected: (NotificationStatusFilter) -> Unit,
    onSelectEntry: (NotificationLogEntry) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Service & Endpoint Status Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusBadgeColor = if (state.isServiceConnected) Color(0xFF4CAF50) else Color(0xFFFFB74D)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusBadgeColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isServiceConnected) "Listener Active" else "Listener Disconnected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusBadgeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Forwarding Target: ${state.serverEndpointUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onClearLogs) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Clear Notification Logs")
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by App, Title, Package, or ID...") },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search") },
            singleLine = true
        )

        // Status Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Status:", style = MaterialTheme.typography.labelMedium)
            FilterChipGroup(
                options = NotificationStatusFilter.entries,
                selectedOption = state.statusFilter,
                onOptionSelected = onStatusFilterSelected,
                labelProvider = { it.name }
            )
        }

        // Notification List
        if (state.filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No intercepted notifications.",
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
                items(state.filteredLogs, key = { it.payload.id + "_" + it.loggedAt }) { entry ->
                    val isSelected = entry.payload.id == state.selectedEntry?.payload?.id
                    NotificationLogItemCard(
                        entry = entry,
                        isSelected = isSelected,
                        onClick = { onSelectEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationLogItemCard(
    entry: NotificationLogEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val payload = entry.payload
    val isSuccess = entry.status.isSuccess

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = if (isSuccess) "Forwarded Successfully" else "Forwarding Failed",
                tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = payload.timestampIso.takeLast(12).take(8), // format HH:mm:ss
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
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationInspectorPane(
    entry: NotificationLogEntry?,
    isResending: Boolean,
    onResendClick: (NotificationLogEntry) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        if (entry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a notification to inspect details and payload.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val payload = entry.payload
            val jsonText = try {
                Json { prettyPrint = true }.encodeToString(NotificationPayload.serializer(), payload)
            } catch (e: Exception) {
                payload.toString()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notification Payload Inspector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // App & Notification Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = payload.appName ?: payload.packageName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Package: ${payload.packageName}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "ID: ${payload.id}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text(text = "Posted At: ${payload.timestampIso}", style = MaterialTheme.typography.bodySmall)
                        if (payload.channelId != null) {
                            Text(text = "Channel ID: ${payload.channelId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Notification Message Content Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Message Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(text = "Title: ${payload.title ?: "<none>"}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Text: ${payload.text ?: "<none>"}", style = MaterialTheme.typography.bodyMedium)
                        if (!payload.subText.isNullOrBlank()) {
                            Text(text = "SubText: ${payload.subText}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!payload.bigText.isNullOrBlank()) {
                            Text(text = "BigText: ${payload.bigText}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Forwarding Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.status.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (entry.status.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                contentDescription = null,
                                tint = if (entry.status.isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (entry.status.isSuccess) "Forwarding Status: Success" else "Forwarding Status: Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.status.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }

                        if (!entry.status.errorMessage.isNullOrBlank()) {
                            Text(
                                text = "Error: ${entry.status.errorMessage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                // Resend Action Button
                Button(
                    onClick = { onResendClick(entry) },
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
                        Text("Forwarding Payload...")
                    } else {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Resend")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resend / Forward Payload")
                    }
                }

                // Raw JSON Inspector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "RAW JSON PAYLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = jsonText,
                            color = Color(0xFF81C784),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun NotificationScreenPreview() {
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
        NotificationInspectorPane(
            entry = NotificationLogEntry(payload = samplePayload, status = ForwardingStatus(isSuccess = true)),
            isResending = false,
            onResendClick = {},
            onBackClick = {}
        )
    }
}
