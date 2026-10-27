package ru.mux.app.nginx

import ru.mux.app.process.manager.BinaryManager
import ru.mux.app.process.manager.NativeProcessManager
import ru.mux.app.process.model.ProcessStatus
import ru.mux.app.process.model.ProcessTag
import java.io.File

class NginxProcessController(
    private val processManager: NativeProcessManager,
    private val binaryManager: BinaryManager,
    private val configManager: NginxConfigManager
) {
    val nginxBinaryFile: File
        get() = binaryManager.getBinaryFile("nginx")

    fun buildCommand(): List<String> {
        val executable = if (nginxBinaryFile.exists()) {
            nginxBinaryFile.absolutePath
        } else {
            "nginx"
        }
        return listOf(
            executable,
            "-c", configManager.configFile.absolutePath,
            "-g", "daemon off;"
        )
    }

    fun startServer(): Result<ProcessStatus> {
        val binaryFile = binaryManager.extractBinaryFromAssets("nginx", "nginx")
        binaryManager.makeExecutable(binaryFile)

        val command = buildCommand()
        return processManager.startProcess(
            tag = ProcessTag.NGINX,
            command = command,
            workingDir = configManager.nginxDir
        )
    }

    fun stopServer(): Boolean {
        return processManager.stopProcess(ProcessTag.NGINX)
    }

    fun isServerRunning(): Boolean {
        return processManager.isProcessRunning(ProcessTag.NGINX)
    }

    fun getServerStatus(): ProcessStatus {
        return processManager.getProcessStatus(ProcessTag.NGINX)
    }
}
