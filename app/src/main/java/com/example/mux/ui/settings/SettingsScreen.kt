package com.example.mux.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.R
import com.example.mux.ui.components.PermissionBanner
import com.example.mux.ui.theme.MuxTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDeviceState: () -> Unit,
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
        onOpenDeviceState = onOpenDeviceState,
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
    onOpenDeviceState: () -> Unit,
    modifier: Modifier = Modifier,
    showPackageFiltering: Boolean = false
) {
    var serverUrlInput by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var deviceStateUrlInput by remember(state.deviceStateUrl) { mutableStateOf(state.deviceStateUrl) }
    var newPackageInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Permission banner — only shown while access is missing.
        //    Once granted, the live status is surfaced on the Notifications Feed screen.
        if (!state.isNotificationListenerGranted) {
            PermissionBanner(
                isGranted = false,
                onGrantClick = onGrantPermissionClick
            )
        }

        // 2. Notification Interception — endpoint, credentials and the on/off switch.
        SettingsSection(
            title = "Notification Interception",
            icon = Icons.Rounded.Notifications,
            trailing = {
                Switch(
                    checked = state.isNotificationForwardingEnabled,
                    onCheckedChange = onToggleNotificationForwarding
                )
            }
        ) {
            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = { serverUrlInput = it },
                label = { Text("Target Endpoint URL") },
                placeholder = { Text("http://192.168.1.100:8080/api/notifications") },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Rounded.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key / X-Api-Key Header Token") },
                placeholder = { Text("Optional secret authorization token") },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Rounded.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
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

            if (showPackageFiltering) {
                PackageFilteringSection(
                    isBlacklistMode = state.isBlacklistMode,
                    packageRules = state.packageRules,
                    newPackageInput = newPackageInput,
                    onNewPackageInputChange = { newPackageInput = it },
                    onSetPackageRuleMode = onSetPackageRuleMode,
                    onAddPackageRule = { pkg -> onAddPackageRule(pkg).also { newPackageInput = "" } },
                    onRemovePackageRule = onRemovePackageRule
                )
            }
        }

        // 3. Device State Channel Configuration
        SettingsSection(
            title = stringResource(R.string.device_state_channel_title),
            icon = Icons.Rounded.Settings,
            trailing = {
                OutlinedButton(onClick = onOpenDeviceState) {
                    Text(stringResource(R.string.device_state_open_screen))
                }
            }
        ) {
            OutlinedTextField(
                value = deviceStateUrlInput,
                onValueChange = { deviceStateUrlInput = it },
                label = { Text(stringResource(R.string.device_state_endpoint_label)) },
                placeholder = { Text(stringResource(R.string.device_state_endpoint_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SettingToggleRow(
                title = stringResource(R.string.device_state_forward_toggle),
                checked = state.isDeviceStateForwardingEnabled,
                onCheckedChange = onToggleDeviceStateForwarding
            )

            SettingToggleRow(
                title = stringResource(R.string.device_state_clipboard_toggle),
                description = stringResource(R.string.device_state_clipboard_description),
                checked = state.isClipboardCaptureEnabled,
                onCheckedChange = onToggleClipboardCapture
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { onSaveDeviceStateUrl(deviceStateUrlInput) }) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.device_state_save_endpoint_desc)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.device_state_save_endpoint))
                }
            }
        }

        // 4. Modular Expansion Placeholders (SMS & Call Log)
        SettingsSection(
            title = "Modular System Forwarders",
            icon = Icons.Rounded.Security
        ) {
            SettingToggleRow(
                title = "SMS Forwarder",
                description = "Intercept and forward incoming cellular SMS messages.",
                checked = state.isSmsForwardingEnabled,
                onCheckedChange = onToggleSmsForwarding,
                badge = "Coming Soon"
            )

            SettingToggleRow(
                title = "Call Log Forwarder",
                description = "Intercept and forward incoming/outgoing phone call events.",
                checked = state.isCallLogForwardingEnabled,
                onCheckedChange = onToggleCallLogForwarding,
                badge = "Coming Soon"
            )
        }
    }
}

/**
 * Package allow/block rules. Kept out of the main layout for now — flip
 * [SettingsContent]'s `showPackageFiltering` flag to surface it again.
 */
@Composable
private fun PackageFilteringSection(
    isBlacklistMode: Boolean,
    packageRules: List<String>,
    newPackageInput: String,
    onNewPackageInputChange: (String) -> Unit,
    onSetPackageRuleMode: (Boolean) -> Unit,
    onAddPackageRule: (String) -> Unit,
    onRemovePackageRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "Package Filter Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isBlacklistMode,
                onClick = { onSetPackageRuleMode(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Blacklist (Block)")
            }
            SegmentedButton(
                selected = !isBlacklistMode,
                onClick = { onSetPackageRuleMode(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Whitelist (Allow Only)")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newPackageInput,
                onValueChange = onNewPackageInputChange,
                label = { Text("Package Name to Add") },
                placeholder = { Text("com.whatsapp") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onAddPackageRule(newPackageInput) }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Package")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            packageRules.forEach { pkg ->
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

/** Shared card container: icon + title, optional trailing content, divider, then body. */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            content()
        }
    }
}

/** Label (+ optional description and badge) on the left, switch on the right. */
@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    badge: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(badge, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
            onToggleCallLogForwarding = {},
            onOpenDeviceState = {}
        )
    }
}
