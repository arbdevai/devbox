package com.arbdev.devbox.util

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    COMMAND,
    OUTPUT
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object AppLogger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val logs = mutableStateListOf<LogEntry>()
    private const val MAX_LOGS = 1500

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        logs.add(entry)
        if (logs.size > MAX_LOGS) {
            logs.removeRange(0, logs.size - MAX_LOGS)
        }
    }

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val text = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        log(LogLevel.ERROR, tag, text)
    }

    fun cmd(command: String, exitCode: Int, stdout: String, stderr: String) {
        log(LogLevel.COMMAND, "CMD", "$ $command (exit: $exitCode)")
        if (stdout.isNotBlank()) {
            log(LogLevel.OUTPUT, "STDOUT", stdout)
        }
        if (stderr.isNotBlank()) {
            log(LogLevel.ERROR, "STDERR", stderr)
        }
    }

    fun clear() {
        logs.clear()
    }

    fun getAllAsText(): String {
        return logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
    }
}
