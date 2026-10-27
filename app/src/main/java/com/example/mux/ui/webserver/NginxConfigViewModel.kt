package com.example.mux.ui.webserver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mux.MuxApplication
import com.example.mux.process.nginx.NginxConfig
import com.example.mux.process.nginx.NginxConfigManager
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.tuna.TunnelProtocol
import com.example.mux.process.tuna.TunaConfig
import com.example.mux.process.tuna.TunaProcessController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WebServerTab { NGINX_CONF, STATIC_FILES, NGINX_SETTINGS, TUNA_SETTINGS }

data class NginxConfigUiState(
    val rawConfigText: String = "",
    val parsedConfig: NginxConfig? = null,
    val isConfigValid: Boolean = true,
    val validationMessage: String? = null,
    val staticFiles: List<File> = emptyList(),
    val selectedStaticFile: File? = null,
    val selectedFileContent: String = "",
    val nginxPort: Int = 8080,
    val nginxServerName: String = "localhost",
    val tunaConfig: TunaConfig = TunaConfig(),
    val activeTab: WebServerTab = WebServerTab.NGINX_CONF,
    val statusMessage: String? = null
)

class NginxConfigViewModel(
    private val nginxConfigManager: NginxConfigManager,
    private val nginxProcessController: NginxProcessController,
    private val tunaProcessController: TunaProcessController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(NginxConfigUiState())
    val uiState: StateFlow<NginxConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfigAndFiles()
    }

    fun loadConfigAndFiles() {
        viewModelScope.launch(ioDispatcher) {
            val configText = nginxConfigManager.readConfig()
            val parsed = try {
                nginxConfigManager.parseConfig()
            } catch (_: Exception) {
                null
            }
            val isValid = nginxConfigManager.validateConfig(configText)
            val files = listStaticFiles()
            val initialSelectedFile = files.firstOrNull { it.name == "index.html" } ?: files.firstOrNull()
            val fileContent = initialSelectedFile?.let { readFileContent(it) } ?: ""

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        rawConfigText = configText,
                        parsedConfig = parsed,
                        isConfigValid = isValid,
                        validationMessage = if (isValid) "Config syntax is valid." else "Invalid syntax (mismatched braces or missing required blocks).",
                        staticFiles = files,
                        selectedStaticFile = initialSelectedFile,
                        selectedFileContent = fileContent,
                        nginxPort = parsed?.port ?: 8080,
                        nginxServerName = parsed?.serverName ?: "localhost"
                    )
                }
            }
        }
    }

    fun updateRawConfigText(text: String) {
        val isValid = nginxConfigManager.validateConfig(text)
        _uiState.update { current ->
            current.copy(
                rawConfigText = text,
                isConfigValid = isValid,
                validationMessage = if (isValid) "Config syntax is valid." else "Invalid syntax (mismatched braces or missing required blocks)."
            )
        }
    }

    fun saveRawConfig() {
        viewModelScope.launch(ioDispatcher) {
            val text = _uiState.value.rawConfigText
            if (!nginxConfigManager.validateConfig(text)) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(statusMessage = "Cannot save: Invalid Nginx configuration syntax.") }
                }
                return@launch
            }
            nginxConfigManager.writeRawConfig(text)
            val parsed = try { nginxConfigManager.parseConfig() } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        parsedConfig = parsed,
                        nginxPort = parsed?.port ?: current.nginxPort,
                        nginxServerName = parsed?.serverName ?: current.nginxServerName,
                        statusMessage = "nginx.conf saved successfully."
                    )
                }
            }
        }
    }

    fun resetConfigToDefault() {
        viewModelScope.launch(ioDispatcher) {
            val defaultConfig = NginxConfig(rootDir = nginxConfigManager.htmlDir.absolutePath)
            nginxConfigManager.writeConfig(defaultConfig)
            val text = nginxConfigManager.readConfig()
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        rawConfigText = text,
                        parsedConfig = defaultConfig,
                        isConfigValid = true,
                        validationMessage = "Reset to default configuration.",
                        nginxPort = defaultConfig.port,
                        nginxServerName = defaultConfig.serverName,
                        statusMessage = "Nginx config reset to default."
                    )
                }
            }
        }
    }

    fun updateNginxSettings(port: Int, serverName: String) {
        viewModelScope.launch(ioDispatcher) {
            val updated = nginxConfigManager.updatePort(port)
            nginxConfigManager.updateServerName(serverName)
            val newText = nginxConfigManager.readConfig()
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        rawConfigText = newText,
                        parsedConfig = updated.copy(serverName = serverName),
                        nginxPort = port,
                        nginxServerName = serverName,
                        statusMessage = "Nginx settings updated."
                    )
                }
            }
        }
    }

    fun updateTunaConfig(
        localPort: Int,
        protocol: TunnelProtocol,
        remoteHost: String?,
        subdomain: String?,
        customFlagsText: String
    ) {
        val flags = customFlagsText.split(" ").filter { it.isNotBlank() }
        val newTunaConfig = TunaConfig(
            localPort = localPort,
            protocol = protocol,
            remoteHost = remoteHost?.takeIf { it.isNotBlank() },
            subdomain = subdomain?.takeIf { it.isNotBlank() },
            customFlags = flags
        )
        _uiState.update { it.copy(tunaConfig = newTunaConfig) }
    }

    fun startTunaWithCurrentConfig() {
        viewModelScope.launch {
            val config = _uiState.value.tunaConfig
            val result = tunaProcessController.startTunnel(config)
            if (result.isSuccess) {
                _uiState.update { it.copy(statusMessage = "Tuna tunnel started on port ${config.localPort}") }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to start Tuna: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun setActiveTab(tab: WebServerTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectStaticFile(file: File) {
        viewModelScope.launch(ioDispatcher) {
            val content = readFileContent(file)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedStaticFile = file, selectedFileContent = content) }
            }
        }
    }

    fun updateSelectedFileContent(content: String) {
        _uiState.update { it.copy(selectedFileContent = content) }
    }

    fun saveSelectedStaticFile() {
        val file = _uiState.value.selectedStaticFile ?: return
        val content = _uiState.value.selectedFileContent
        viewModelScope.launch(ioDispatcher) {
            file.writeText(content)
            val files = listStaticFiles()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(staticFiles = files, statusMessage = "File '${file.name}' saved.") }
            }
        }
    }

    fun createStaticFile(fileName: String, initialContent: String) {
        if (fileName.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val newFile = File(nginxConfigManager.htmlDir, fileName.trim())
            newFile.writeText(initialContent)
            val files = listStaticFiles()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        staticFiles = files,
                        selectedStaticFile = newFile,
                        selectedFileContent = initialContent,
                        statusMessage = "Created file '${newFile.name}'."
                    )
                }
            }
        }
    }

    fun deleteStaticFile(file: File) {
        viewModelScope.launch(ioDispatcher) {
            if (file.exists()) {
                file.delete()
            }
            val files = listStaticFiles()
            val nextSelected = files.firstOrNull()
            val content = nextSelected?.let { readFileContent(it) } ?: ""
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        staticFiles = files,
                        selectedStaticFile = nextSelected,
                        selectedFileContent = content,
                        statusMessage = "Deleted file '${file.name}'."
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun listStaticFiles(): List<File> {
        val htmlDir = nginxConfigManager.htmlDir
        if (!htmlDir.exists()) {
            nginxConfigManager.createDefaultIndexFile()
        }
        return htmlDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    }

    private fun readFileContent(file: File): String {
        return try {
            if (file.exists()) file.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    class Factory(private val app: MuxApplication = MuxApplication.instance) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NginxConfigViewModel(
                nginxConfigManager = app.nginxConfigManager,
                nginxProcessController = app.nginxProcessController,
                tunaProcessController = app.tunaProcessController
            ) as T
        }
    }
}
