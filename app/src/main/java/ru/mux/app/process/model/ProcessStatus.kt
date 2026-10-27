package ru.mux.app.process.model

data class ProcessStatus(
    val tag: ProcessTag,
    val isRunning: Boolean,
    val pid: Long? = null,
    val exitCode: Int? = null,
    val command: List<String> = emptyList()
)
