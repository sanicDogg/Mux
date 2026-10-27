package com.example.mux.ui.webserver

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.process.nginx.NginxConfig
import com.example.mux.process.tuna.TunnelProtocol
import com.example.mux.process.tuna.TunaConfig
import com.example.mux.ui.theme.MuxTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebServerConfigScreen(
    viewModel: NginxConfigViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Row
        PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
            Tab(
                selected = state.activeTab == WebServerTab.NGINX_CONF,
                onClick = { viewModel.setActiveTab(WebServerTab.NGINX_CONF) },
                text = { Text("nginx.conf") }
            )
            Tab(
                selected = state.activeTab == WebServerTab.STATIC_FILES,
                onClick = { viewModel.setActiveTab(WebServerTab.STATIC_FILES) },
                text = { Text("Static Files") }
            )
            Tab(
                selected = state.activeTab == WebServerTab.NGINX_SETTINGS,
                onClick = { viewModel.setActiveTab(WebServerTab.NGINX_SETTINGS) },
                text = { Text("Nginx Port") }
            )
            Tab(
                selected = state.activeTab == WebServerTab.TUNA_SETTINGS,
                onClick = { viewModel.setActiveTab(WebServerTab.TUNA_SETTINGS) },
                text = { Text("Tuna Tunnel") }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (state.activeTab) {
                WebServerTab.NGINX_CONF -> NginxConfigEditorTab(
                    state = state,
                    onConfigTextChange = { viewModel.updateRawConfigText(it) },
                    onSaveConfig = { viewModel.saveRawConfig() },
                    onResetConfig = { viewModel.resetConfigToDefault() }
                )
                WebServerTab.STATIC_FILES -> StaticFileManagerTab(
                    state = state,
                    onFileSelected = { viewModel.selectStaticFile(it) },
                    onFileContentChange = { viewModel.updateSelectedFileContent(it) },
                    onSaveFile = { viewModel.saveSelectedStaticFile() },
                    onCreateFile = { name, content -> viewModel.createStaticFile(name, content) },
                    onDeleteFile = { viewModel.deleteStaticFile(it) }
                )
                WebServerTab.NGINX_SETTINGS -> NginxSettingsTab(
                    state = state,
                    onSaveSettings = { port, serverName -> viewModel.updateNginxSettings(port, serverName) }
                )
                WebServerTab.TUNA_SETTINGS -> TunaSettingsTab(
                    state = state,
                    onSaveAndStart = { port, protocol, remoteHost, subdomain, flags ->
                        viewModel.updateTunaConfig(port, protocol, remoteHost, subdomain, flags)
                        viewModel.startTunaWithCurrentConfig()
                    }
                )
            }
        }
    }
}

@Composable
fun NginxConfigEditorTab(
    state: NginxConfigUiState,
    onConfigTextChange: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onResetConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Validation Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isConfigValid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state.isConfigValid) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    contentDescription = null,
                    tint = if (state.isConfigValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isConfigValid) "Configuration Valid" else "Syntax Error Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isConfigValid) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = state.validationMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onResetConfig) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset to Default")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onSaveConfig,
                enabled = state.isConfigValid
            ) {
                Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save nginx.conf")
            }
        }

        // Code Editor Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(8.dp)
        ) {
            OutlinedTextField(
                value = state.rawConfigText,
                onValueChange = onConfigTextChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun StaticFileManagerTab(
    state: NginxConfigUiState,
    onFileSelected: (File) -> Unit,
    onFileContentChange: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onDeleteFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<File>()

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { fileName, content ->
                onCreateFile(fileName, content)
                showCreateDialog = false
            }
        )
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.fillMaxSize(),
        listPane = {
            StaticFileListPane(
                staticFiles = state.staticFiles,
                selectedFile = state.selectedStaticFile,
                onFileSelected = { file ->
                    onFileSelected(file)
                    scope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, file)
                    }
                },
                onCreateFileClick = { showCreateDialog = true }
            )
        },
        detailPane = {
            val selectedFile = navigator.currentDestination?.contentKey ?: state.selectedStaticFile

            StaticFileEditorPane(
                selectedFile = selectedFile,
                fileContent = state.selectedFileContent,
                onFileContentChange = onFileContentChange,
                onSaveFile = onSaveFile,
                onDeleteFile = { file ->
                    onDeleteFile(file)
                    scope.launch {
                        if (navigator.canNavigateBack()) {
                            navigator.navigateBack()
                        }
                    }
                },
                onBackClick = if (navigator.canNavigateBack()) {
                    {
                        scope.launch {
                            navigator.navigateBack()
                        }
                    }
                } else null
            )
        }
    )
}

@Composable
fun StaticFileListPane(
    staticFiles: List<File>,
    selectedFile: File?,
    onFileSelected: (File) -> Unit,
    onCreateFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Web Root Files",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCreateFileClick) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "New File")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (staticFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No static files in web root.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(staticFiles, key = { it.name }) { file ->
                        val isSelected = file.name == selectedFile?.name
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFileSelected(file) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaticFileEditorPane(
    selectedFile: File?,
    fileContent: String,
    onFileContentChange: (String) -> Unit,
    onSaveFile: () -> Unit,
    onDeleteFile: (File) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedFile == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No file selected. Select or create a static file.")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to file list"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "Editing: ${selectedFile.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = { onDeleteFile(selectedFile) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete File",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSaveFile) {
                        Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save File")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save File")
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp)
            ) {
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = onFileContentChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFE0E0E0)
                    )
                )
            }
        }
    }
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var fileName by remember { mutableStateOf("page.html") }
    var content by remember { mutableStateOf("<!DOCTYPE html>\n<html>\n<body>\n<h1>New Page</h1>\n</body>\n</html>") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Web Root File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Initial HTML Content") },
                    modifier = Modifier.height(150.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(fileName, content) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NginxSettingsTab(
    state: NginxConfigUiState,
    onSaveSettings: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var portText by remember(state.nginxPort) { mutableStateOf(state.nginxPort.toString()) }
    var serverName by remember(state.nginxServerName) { mutableStateOf(state.nginxServerName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                Text(
                    text = "Nginx Web Server Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Listen Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 8080
                        onSaveSettings(port, serverName)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save Settings")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Apply Nginx Settings")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunaSettingsTab(
    state: NginxConfigUiState,
    onSaveAndStart: (Int, TunnelProtocol, String?, String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var localPortText by remember(state.tunaConfig.localPort) { mutableStateOf(state.tunaConfig.localPort.toString()) }
    var selectedProtocol by remember(state.tunaConfig.protocol) { mutableStateOf(state.tunaConfig.protocol) }
    var remoteHost by remember(state.tunaConfig.remoteHost) { mutableStateOf(state.tunaConfig.remoteHost ?: "") }
    var subdomain by remember(state.tunaConfig.subdomain) { mutableStateOf(state.tunaConfig.subdomain ?: "") }
    var customFlags by remember(state.tunaConfig.customFlags) { mutableStateOf(state.tunaConfig.customFlags.joinToString(" ")) }
    var isProtocolDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                Text(
                    text = "Tuna Tunnel Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Protocol Dropdown
                ExposedDropdownMenuBox(
                    expanded = isProtocolDropdownExpanded,
                    onExpandedChange = { isProtocolDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProtocol.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProtocolDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = isProtocolDropdownExpanded,
                        onDismissRequest = { isProtocolDropdownExpanded = false }
                    ) {
                        TunnelProtocol.entries.forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.name) },
                                onClick = {
                                    selectedProtocol = proto
                                    isProtocolDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = localPortText,
                    onValueChange = { localPortText = it },
                    label = { Text("Local Port (e.g., 8080)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subdomain,
                    onValueChange = { subdomain = it },
                    label = { Text("Subdomain (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    label = { Text("Remote Host (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customFlags,
                    onValueChange = { customFlags = it },
                    label = { Text("Custom CLI Flags (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val port = localPortText.toIntOrNull() ?: 8080
                        onSaveAndStart(port, selectedProtocol, remoteHost, subdomain, customFlags)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "Start Tunnel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Start Tuna Tunnel")
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
fun WebServerConfigScreenPreview() {
    MuxTheme {
        NginxConfigEditorTab(
            state = NginxConfigUiState(
                rawConfigText = "worker_processes 1;\nhttp {\n  server {\n    listen 8080;\n  }\n}",
                isConfigValid = true,
                validationMessage = "Config syntax is valid."
            ),
            onConfigTextChange = {},
            onSaveConfig = {},
            onResetConfig = {}
        )
    }
}
