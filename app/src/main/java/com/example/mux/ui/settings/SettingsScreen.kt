package com.example.mux.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.ui.components.PermissionBanner
import com.example.mux.ui.theme.MuxTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkPermission(context)
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    SettingsContent(
        state = state,
        onGrantPermissionClick = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        onSaveServerUrl = { viewModel.setServerUrl(it) },
        onSaveApiKey = { viewModel.setApiKey(it) },
        onToggleNotificationForwarding = { viewModel.setNotificationForwardingEnabled(it) },
        onSaveDeviceStateUrl = { viewModel.setDeviceStateUrl(it) },
        onToggleDeviceStateForwarding = { viewModel.setDeviceStateForwardingEnabled(it) },
        onToggleClipboardCapture = { viewModel.setClipboardCaptureEnabled(it) },
        onSetPackageRuleMode = { viewModel.setPackageRuleMode(it) },
        onAddPackageRule = { viewModel.addPackageRule(it) },
        onRemovePackageRule = { viewModel.removePackageRule(it) },
        onToggleSmsForwarding = { viewModel.toggleSmsForwarding(it) },
        onToggleCallLogForwarding = { viewModel.toggleCallLogForwarding(it) },
        modifier = modifier
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onGrantPermissionClick: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onToggleNotificationForwarding: (Boolean) -> Unit,
    onSaveDeviceStateUrl: (String) -> Unit,
    onToggleDeviceStateForwarding: (Boolean) -> Unit,
    onToggleClipboardCapture: (Boolean) -> Unit,
    onSetPackageRuleMode: (Boolean) -> Unit,
    onAddPackageRule: (String) -> Unit,
    onRemovePackageRule: (String) -> Unit,
    onToggleSmsForwarding: (Boolean) -> Unit,
    onToggleCallLogForwarding: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrlInput by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var deviceStateUrlInput by remember(state.deviceStateUrl) { mutableStateOf(state.deviceStateUrl) }
    var newPackageInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Permission Banner & Status
        if (!state.isNotificationListenerGranted) {
            PermissionBanner(
                isGranted = false,
                onGrantClick = onGrantPermissionClick
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Notification Listener Service is active and authorized.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 2. Data Forwarder Server Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Forwarding Server Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = { serverUrlInput = it },
                    label = { Text("Target Endpoint URL") },
                    placeholder = { Text("http://192.168.1.100:8080/api/notifications") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key / X-Api-Key Header Token") },
                    placeholder = { Text("Optional secret authorization token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Key, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            onSaveServerUrl(serverUrlInput)
                            onSaveApiKey(apiKeyInput)
                        }
                    ) {
                        Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save Settings")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Server Config")
                    }
                }
            }
        }

        // 2b. Device State Channel Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device State Channel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = deviceStateUrlInput,
                    onValueChange = { deviceStateUrlInput = it },
                    label = { Text("Device State Endpoint URL") },
                    placeholder = { Text("http://192.168.1.100:8080/api/device-state") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Forward device state", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = state.isDeviceStateForwardingEnabled,
                        onCheckedChange = onToggleDeviceStateForwarding
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Clipboard capture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Requires opt-in; Android may return empty text when this app is not in the foreground.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isClipboardCaptureEnabled,
                        onCheckedChange = onToggleClipboardCapture
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { onSaveDeviceStateUrl(deviceStateUrlInput) }) {
                        Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save Device State URL")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Endpoint")
                    }
                }
            }
        }

        // 3. Notification Interception & Rule Filter Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Interception",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = state.isNotificationForwardingEnabled,
                        onCheckedChange = onToggleNotificationForwarding
                    )
                }

                Text(
                    text = "Package Filter Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.isBlacklistMode,
                        onClick = { onSetPackageRuleMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Blacklist (Block)")
                    }
                    SegmentedButton(
                        selected = !state.isBlacklistMode,
                        onClick = { onSetPackageRuleMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Whitelist (Allow Only)")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPackageInput,
                        onValueChange = { newPackageInput = it },
                        label = { Text("Package Name to Add") },
                        placeholder = { Text("com.whatsapp") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onAddPackageRule(newPackageInput)
                            newPackageInput = ""
                        }
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Package")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.packageRules.forEach { pkg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = pkg, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onRemovePackageRule(pkg) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Modular Expansion Placeholders (SMS & Call Log)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Modular System Forwarders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // SMS Forwarding Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "SMS Forwarder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Coming Soon", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "Intercept and forward incoming cellular SMS messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isSmsForwardingEnabled,
                        onCheckedChange = onToggleSmsForwarding
                    )
                }

                // Call Log Forwarding Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Call Log Forwarder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Coming Soon", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "Intercept and forward incoming/outgoing phone call events.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isCallLogForwardingEnabled,
                        onCheckedChange = onToggleCallLogForwarding
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 800)
@Composable
fun SettingsScreenPreview() {
    MuxTheme {
        SettingsContent(
            state = SettingsUiState(
                serverUrl = "http://192.168.1.100:8080/api/notifications",
                apiKey = "token_12345",
                isNotificationForwardingEnabled = true,
                deviceStateUrl = "http://192.168.1.100:8080/api/device-state",
                isDeviceStateForwardingEnabled = true,
                packageRules = listOf("com.whatsapp", "com.telegram"),
                isNotificationListenerGranted = true
            ),
            onGrantPermissionClick = {},
            onSaveServerUrl = {},
            onSaveApiKey = {},
            onToggleNotificationForwarding = {},
            onSaveDeviceStateUrl = {},
            onToggleDeviceStateForwarding = {},
            onToggleClipboardCapture = {},
            onSetPackageRuleMode = {},
            onAddPackageRule = {},
            onRemovePackageRule = {},
            onToggleSmsForwarding = {},
            onToggleCallLogForwarding = {}
        )
    }
}
