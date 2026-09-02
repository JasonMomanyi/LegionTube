package io.github.jasonmomanyi.legiontube.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * High-performance circular buffer logger for LegionTube.
 * Captures subsystem log tags ([Player], [Extractor], [Network], [Cipher], [Database], etc.)
 * in memory without disk write overhead, making full session logs available for in-app diagnostics.
 */
object LegionTubeLog {

    data class LogEntry(
        val timestamp: Long,
        val level: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun formatted(): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            val levelChar = when (level) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }
            val throwableStr = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
            return "[$dateStr] [$levelChar] [$tag] $message$throwableStr"
        }
    }

    private const val MAX_LOG_CAPACITY = 1000
    private val buffer = ConcurrentLinkedQueue<LogEntry>()

    private fun addEntry(entry: LogEntry) {
        buffer.add(entry)
        while (buffer.size > MAX_LOG_CAPACITY) {
            buffer.poll()
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        addEntry(LogEntry(System.currentTimeMillis(), Log.VERBOSE, tag, message, throwable))
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        addEntry(LogEntry(System.currentTimeMillis(), Log.DEBUG, tag, message, throwable))
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        addEntry(LogEntry(System.currentTimeMillis(), Log.INFO, tag, message, throwable))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        addEntry(LogEntry(System.currentTimeMillis(), Log.WARN, tag, message, throwable))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        addEntry(LogEntry(System.currentTimeMillis(), Log.ERROR, tag, message, throwable))
    }

    fun getLogs(): List<LogEntry> {
        return buffer.toList()
    }

    fun getFormattedLogs(): String {
        return buffer.joinToString(separator = "\n") { it.formatted() }
    }

    fun clear() {
        buffer.clear()
    }
}
