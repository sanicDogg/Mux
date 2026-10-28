package ru.mux.app.process.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProcessLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val processTag: ProcessTag,
    val source: LogSource,
    val message: String
) {
    val formattedTimestamp: String = formatter.get()?.format(Date(timestamp)) ?: ""

    companion object {
        private val formatter = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        }
    }
}
