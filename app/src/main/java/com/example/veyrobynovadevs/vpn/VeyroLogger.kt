package com.example.veyrobynovadevs.vpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

enum class VeyroEvent {
    VPN_STARTED,
    TUN_CREATED,
    SOCKET_PROTECT_SUCCESS,
    SOCKET_PROTECT_FAILED,
    SOCKS5_CONNECT,
    VLESS_CONNECT,
    REALITY_HANDSHAKE_START,
    REALITY_HANDSHAKE_SUCCESS,
    TUN_TCP_ESTABLISHED,
    DNS_QUERY,
    DNS_RESPONSE,
    CONNECTION_ERROR
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel,
    val message: String,
    val details: String? = null,
    val event: VeyroEvent? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return sdf.format(Date(timestamp))
        }
}

/**
 * Thread-Safe In-App Logger for Veyro VPN Stack.
 * Maintains structured connection logs and exposes a StateFlow for real-time UI display.
 */
object VeyroLogger {
    private const val MAX_LOGS = 2000
    private val logList = mutableListOf<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        details: String? = null,
        event: VeyroEvent? = null
    ) {
        val entry = LogEntry(
            tag = tag,
            level = level,
            message = message,
            details = details,
            event = event
        )

        val fullMsg = if (details != null) "$message ($details)" else message
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, fullMsg)
            LogLevel.INFO -> Log.i(tag, fullMsg)
            LogLevel.WARN -> Log.w(tag, fullMsg)
            LogLevel.ERROR -> Log.e(tag, fullMsg)
        }

        synchronized(logList) {
            logList.add(entry)
            if (logList.size > MAX_LOGS) {
                logList.removeAt(0)
            }
            _logs.value = ArrayList(logList)
        }
    }

    fun d(tag: String, message: String, details: String? = null, event: VeyroEvent? = null) {
        log(LogLevel.DEBUG, tag, message, details, event)
    }

    fun i(tag: String, message: String, details: String? = null, event: VeyroEvent? = null) {
        log(LogLevel.INFO, tag, message, details, event)
    }

    fun w(tag: String, message: String, details: String? = null, event: VeyroEvent? = null) {
        log(LogLevel.WARN, tag, message, details, event)
    }

    fun e(tag: String, message: String, details: String? = null, event: VeyroEvent? = null) {
        log(LogLevel.ERROR, tag, message, details, event)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.ERROR, tag, message, throwable.message ?: throwable.toString())
    }

    fun logEvent(event: VeyroEvent, tag: String, message: String, details: String? = null) {
        val level = when (event) {
            VeyroEvent.SOCKET_PROTECT_FAILED, VeyroEvent.CONNECTION_ERROR -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        log(level, tag, message, details, event)
    }

    fun clearLogs() {
        synchronized(logList) {
            logList.clear()
            _logs.value = emptyList()
        }
    }
}
