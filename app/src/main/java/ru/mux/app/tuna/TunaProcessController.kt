package ru.mux.app.tuna

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import ru.mux.app.process.manager.BinaryManager
import ru.mux.app.process.manager.NativeProcessManager
import ru.mux.app.process.model.ProcessStatus
import ru.mux.app.process.model.ProcessTag
import ru.mux.app.process.repository.ProcessLogRepository
import java.io.File

enum class TunnelProtocol(val value: String) {
    HTTP("http"),
    HTTPS("https"),
    TCP("tcp"),
    UDP("udp")
}

data class TunaConfig(
    val localPort: Int = 8080,
    val protocol: TunnelProtocol = TunnelProtocol.HTTP,
    val remoteHost: String? = null,
    val subdomain: String? = null,
    val customFlags: List<String> = emptyList()
)

class TunaProcessController(
    private val processManager: NativeProcessManager,
    private val binaryManager: BinaryManager,
    private val logRepository: ProcessLogRepository
) {
    val tunaBinaryFile: File
        get() = binaryManager.getBinaryFile("tuna")

    fun buildCommand(config: TunaConfig): List<String> {
        val command = mutableListOf<String>()
        val executable = if (tunaBinaryFile.exists()) {
            tunaBinaryFile.absolutePath
        } else {
            "tuna"
        }
        command.add(executable)
        command.add(config.protocol.value)
        command.add(config.localPort.toString())

        config.subdomain?.let {
            command.add("--subdomain")
            command.add(it)
        }

        config.remoteHost?.let {
            command.add("--remote-host")
            command.add(it)
        }

        if (config.customFlags.isNotEmpty()) {
            command.addAll(config.customFlags)
        }

        return command
    }

    fun startTunnel(config: TunaConfig): Result<ProcessStatus> {
        val binaryFile = binaryManager.extractBinaryFromAssets("tuna", "tuna")
        binaryManager.makeExecutable(binaryFile)

        val command = buildCommand(config)
        return processManager.startProcess(
            tag = ProcessTag.TUNA,
            command = command,
            workingDir = binaryManager.binDir
        )
    }

    fun stopTunnel(): Boolean {
        return processManager.stopProcess(ProcessTag.TUNA)
    }

    fun isTunnelRunning(): Boolean {
        return processManager.isProcessRunning(ProcessTag.TUNA)
    }

    fun getTunnelStatus(): ProcessStatus {
        return processManager.getProcessStatus(ProcessTag.TUNA)
    }

    fun observeLogs(): Flow<String> {
        return logRepository.logStream
            .filter { it.processTag == ProcessTag.TUNA }
            .map { "[${it.source}] ${it.formattedTimestamp}: ${it.message}" }
    }
}
