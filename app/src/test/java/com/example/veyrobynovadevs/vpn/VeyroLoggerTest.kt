package com.example.veyrobynovadevs.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VeyroLoggerTest {

    @Before
    fun setUp() {
        VeyroLogger.clearLogs()
    }

    @Test
    fun testLogEntryAdditionAndClearing() {
        assertEquals(0, VeyroLogger.logs.value.size)

        VeyroLogger.i("TestTag", "Info message")
        VeyroLogger.w("TestTag", "Warn message")
        VeyroLogger.e("TestTag", "Error message")

        val currentLogs = VeyroLogger.logs.value
        assertEquals(3, currentLogs.size)

        assertEquals(LogLevel.INFO, currentLogs[0].level)
        assertEquals("Info message", currentLogs[0].message)

        assertEquals(LogLevel.WARN, currentLogs[1].level)
        assertEquals("Warn message", currentLogs[1].message)

        assertEquals(LogLevel.ERROR, currentLogs[2].level)
        assertEquals("Error message", currentLogs[2].message)

        VeyroLogger.clearLogs()
        assertEquals(0, VeyroLogger.logs.value.size)
    }

    @Test
    fun testLogEventStructuredEntries() {
        VeyroLogger.logEvent(
            event = VeyroEvent.VPN_STARTED,
            tag = "VPN",
            message = "VPN Session Started",
            details = "Server: TestServer"
        )

        val logs = VeyroLogger.logs.value
        assertEquals(1, logs.size)

        val entry = logs[0]
        assertEquals(VeyroEvent.VPN_STARTED, entry.event)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("VPN Session Started", entry.message)
        assertEquals("Server: TestServer", entry.details)
        assertNotNull(entry.formattedTime)
        assertTrue(entry.formattedTime.isNotEmpty())
    }

    @Test
    fun testErrorEventSetsErrorLogLevel() {
        VeyroLogger.logEvent(
            event = VeyroEvent.SOCKET_PROTECT_FAILED,
            tag = "Socket",
            message = "Protect failed"
        )

        val logs = VeyroLogger.logs.value
        assertEquals(1, logs.size)
        assertEquals(LogLevel.ERROR, logs[0].level)
    }
}
