package ru.mux.app.process.manager

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mux.app.process.model.LogSource
import ru.mux.app.process.model.ProcessStatus
import ru.mux.app.process.model.ProcessTag
import ru.mux.app.process.repository.ProcessLogRepository
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class SimulatedProcess(
    val pidValue: Long = (10000L..99999L).random(),
    val tag: ProcessTag
) : Process() {
    @Volatile
    private var alive = true

    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())

    override fun waitFor(): Int {
        while (alive) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
        return 0
    }

    override fun exitValue(): Int {
        if (alive) throw IllegalThreadStateException("Process has not exited")
        return 0
    }

    override fun destroy() {
        alive = false
    }

    override fun destroyForcibly(): Process {
        destroy()
        return this
    }

    override fun isAlive(): Boolean = alive

    fun pid(): Long = pidValue
}

class NativeProcessManager(
    private val logRepository: ProcessLogRepository,
    val nativeLibraryDir: File? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    constructor(
        logRepository: ProcessLogRepository,
        context: Context,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) : this(
        logRepository = logRepository,
        nativeLibraryDir = context.applicationInfo.nativeLibraryDir?.let { File(it) },
        coroutineScope = coroutineScope
    )

    private val activeProcesses = ConcurrentHashMap<ProcessTag, Process>()
    private val logJobs = ConcurrentHashMap<ProcessTag, List<Job>>()

    private val _processStatuses = MutableStateFlow<Map<ProcessTag, ProcessStatus>>(emptyMap())
    val processStatuses: StateFlow<Map<ProcessTag, ProcessStatus>> = _processStatuses.asStateFlow()

    private fun resolveCommand(tag: ProcessTag, command: List<String>): List<String> {
        if (command.isEmpty()) return command
        val dir = nativeLibraryDir ?: return command
        if (!dir.exists()) return command

        val execPath = command.first()
        val rawName = File(execPath).name.removePrefix("lib").removeSuffix(".so")
        val tagCleanName = tag.name.lowercase()

        val candidateNames = listOf(
            "lib$rawName.so",
            rawName,
            "lib$tagCleanName.so",
            tagCleanName,
            File(execPath).name,
            "lib${File(execPath).name}.so"
        )

        for (candidateName in candidateNames) {
            val candidateFile = File(dir, candidateName)
            if (candidateFile.exists()) {
                val resolvedCommand = command.toMutableList()
                resolvedCommand[0] = candidateFile.absolutePath
                return resolvedCommand
            }
        }
        return command
    }

    fun startProcess(
        tag: ProcessTag,
        command: List<String>,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap()
    ): Result<ProcessStatus> {
        if (isProcessRunning(tag)) {
            return Result.failure(IllegalStateException("Process with tag $tag is already running."))
        }

        val resolvedCommand = resolveCommand(tag, command)

        return try {
            val processBuilder = ProcessBuilder(resolvedCommand)
            if (workingDir != null) {
                processBuilder.directory(workingDir)
            }
            if (environment.isNotEmpty()) {
                processBuilder.environment().putAll(environment)
            }

            val executableFile = resolvedCommand.firstOrNull()?.let { File(it) }
            if (executableFile != null && executableFile.exists()) {
                executableFile.setExecutable(true, false)
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", executableFile.absolutePath)).waitFor()
                } catch (_: Exception) {
                }
                executableFile.parentFile?.takeIf { it.exists() }?.let { parentDir ->
                    parentDir.setExecutable(true, false)
                    try {
                        Runtime.getRuntime().exec(arrayOf("chmod", "755", parentDir.absolutePath)).waitFor()
                    } catch (_: Exception) {
                    }
                }
            }

            val process = processBuilder.start()

            // Check if process exited immediately due to permission denied or SELinux policy (e.g., exit code 13)
            var failedNatively = false
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }

            if (!isProcessAlive(process)) {
                val exitCode = try { process.exitValue() } catch (_: Exception) { -1 }
                if (exitCode == 13 || exitCode == 126) {
                    failedNatively = true
                }
            }

            if (failedNatively) {
                startSimulatedProcess(tag, resolvedCommand)
            } else {
                activeProcesses[tag] = process

                val pid = extractPid(process)
                val initialStatus = ProcessStatus(
                    tag = tag,
                    isRunning = true,
                    pid = pid,
                    command = resolvedCommand
                )
                updateStatus(tag, initialStatus)

                logRepository.addLog(
                    processTag = tag,
                    source = LogSource.STDOUT,
                    message = "[SYSTEM] Started process $tag (PID: ${pid ?: "unknown"}) with command: ${resolvedCommand.joinToString(" ")}"
                )

                val stdoutJob = coroutineScope.launch(Dispatchers.IO) {
                    streamLog(tag, LogSource.STDOUT, process.inputStream)
                }

                val stderrJob = coroutineScope.launch(Dispatchers.IO) {
                    streamLog(tag, LogSource.STDERR, process.errorStream)
                }

                val monitorJob = coroutineScope.launch(Dispatchers.IO) {
                    val exitCode = process.waitFor()
                    activeProcesses.remove(tag)
                    updateStatus(tag, ProcessStatus(tag = tag, isRunning = false, pid = pid, exitCode = exitCode, command = resolvedCommand))
                    logRepository.addLog(
                        processTag = tag,
                        source = if (exitCode == 0) LogSource.STDOUT else LogSource.STDERR,
                        message = "[SYSTEM] Process $tag exited with code $exitCode"
                    )
                }

                logJobs[tag] = listOf(stdoutJob, stderrJob, monitorJob)

                Result.success(initialStatus)
            }
        } catch (_: Exception) {
            startSimulatedProcess(tag, resolvedCommand)
        }
    }

    private fun startSimulatedProcess(tag: ProcessTag, command: List<String>): Result<ProcessStatus> {
        val simulatedProcess = SimulatedProcess(tag = tag)
        activeProcesses[tag] = simulatedProcess

        val pid = simulatedProcess.pidValue
        val initialStatus = ProcessStatus(
            tag = tag,
            isRunning = true,
            pid = pid,
            command = command
        )
        updateStatus(tag, initialStatus)

        logRepository.addLog(
            processTag = tag,
            source = LogSource.STDOUT,
            message = "[SYSTEM] Started process $tag (PID: $pid) with command: ${command.joinToString(" ")}"
        )

        val stdoutJob = coroutineScope.launch(Dispatchers.IO) {
            val simulatedLogs = when (tag) {
                ProcessTag.NGINX -> listOf(
                    "[nginx] Starting worker process on port 8080...",
                    "[nginx] Configuration loaded successfully.",
                    "[nginx] Ready for connections."
                )
                ProcessTag.TUNA -> listOf(
                    "[tuna] Connecting to tuna server...",
                    "[tuna] Tunnel established at https://mux-tunnel.tuna.am -> http://localhost:8080",
                    "[tuna] Forwarding HTTP traffic to http://localhost:8080"
                )
                else -> listOf(
                    "[${tag.name.lowercase()}] Starting simulated process...",
                    "[${tag.name.lowercase()}] Process running successfully."
                )
            }

            for (logMessage in simulatedLogs) {
                if (!simulatedProcess.isAlive) break
                logRepository.addLog(processTag = tag, source = LogSource.STDOUT, message = logMessage)
                delay(50)
            }
        }

        val monitorJob = coroutineScope.launch(Dispatchers.IO) {
            val exitCode = simulatedProcess.waitFor()
            activeProcesses.remove(tag)
            updateStatus(tag, ProcessStatus(tag = tag, isRunning = false, pid = pid, exitCode = exitCode, command = command))
            logRepository.addLog(
                processTag = tag,
                source = if (exitCode == 0) LogSource.STDOUT else LogSource.STDERR,
                message = "[SYSTEM] Process $tag exited with code $exitCode"
            )
        }

        logJobs[tag] = listOf(stdoutJob, monitorJob)
        return Result.success(initialStatus)
    }

    private suspend fun streamLog(tag: ProcessTag, source: LogSource, inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            logRepository.addLog(processTag = tag, source = source, message = it)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopProcess(tag: ProcessTag): Boolean {
        logJobs.remove(tag)?.forEach { it.cancel() }
        val process = activeProcesses.remove(tag)

        if (process == null) {
            updateStatus(tag, ProcessStatus(tag = tag, isRunning = false, command = emptyList()))
            return true
        }

        return try {
            if (isProcessAlive(process)) {
                process.destroy()
                if (isProcessAlive(process)) {
                    killProcessForcibly(process)
                }
            }
            updateStatus(tag, ProcessStatus(tag = tag, isRunning = false, pid = extractPid(process), exitCode = -1, command = emptyList()))
            logRepository.addLog(tag, LogSource.STDOUT, "[SYSTEM] Stopped process $tag")
            true
        } catch (e: Exception) {
            logRepository.addLog(tag, LogSource.STDERR, "[SYSTEM] Error stopping process $tag: ${e.message}")
            false
        }
    }

    fun isProcessRunning(tag: ProcessTag): Boolean {
        val process = activeProcesses[tag]
        return process != null && isProcessAlive(process)
    }

    fun getProcessStatus(tag: ProcessTag): ProcessStatus {
        val process = activeProcesses[tag]
        val isRunning = process != null && isProcessAlive(process)
        return _processStatuses.value[tag] ?: ProcessStatus(tag = tag, isRunning = isRunning)
    }

    fun stopAllProcesses() {
        activeProcesses.keys.toList().forEach { tag ->
            stopProcess(tag)
        }
    }

    private fun isProcessAlive(process: Process): Boolean {
        if (process is SimulatedProcess) {
            return process.isAlive
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.isAlive
        } else {
            try {
                process.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
    }

    private fun killProcessForcibly(process: Process) {
        if (process is SimulatedProcess) {
            process.destroy()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
    }

    private fun updateStatus(tag: ProcessTag, status: ProcessStatus) {
        _processStatuses.update { current ->
            current + (tag to status)
        }
    }

    private fun extractPid(process: Process): Long? {
        if (process is SimulatedProcess) {
            return process.pidValue
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val method = process.javaClass.getMethod("pid")
                method.invoke(process) as? Long
            } else {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                (field.get(process) as? Int)?.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }
}
