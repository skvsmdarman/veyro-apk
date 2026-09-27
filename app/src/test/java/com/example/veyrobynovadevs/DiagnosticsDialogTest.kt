package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.ui.dialogs.TestResult
import com.example.veyrobynovadevs.ui.dialogs.TestStatus
import com.example.veyrobynovadevs.ui.dialogs.formatBytes
import com.example.veyrobynovadevs.ui.dialogs.runDnsResolutionTest
import com.example.veyrobynovadevs.ui.dialogs.runHttpsDiagnosticTest
import com.example.veyrobynovadevs.ui.dialogs.runServerTcpTest
import com.example.veyrobynovadevs.ui.dialogs.runUdpTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsDialogTest {

    @Test
    fun testTestResultDataClassDefaults() {
        val result = TestResult()
        assertEquals(TestStatus.IDLE, result.status)
        assertEquals("", result.message)
        assertEquals(0L, result.durationMs)
    }

    @Test
    fun testFormatBytesUtility() {
        assertEquals("500 B", formatBytes(500L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.00 MB", formatBytes(1048576L))
    }

    @Test
    fun testTestStatusEnumValues() {
        val entries = TestStatus.entries
        assertEquals(4, entries.size)
        assertTrue(entries.contains(TestStatus.IDLE))
        assertTrue(entries.contains(TestStatus.RUNNING))
        assertTrue(entries.contains(TestStatus.SUCCESS))
        assertTrue(entries.contains(TestStatus.FAILED))
    }

    @Test
    fun testRunHttpsDiagnosticTestExecution() = runBlocking {
        val results = mutableMapOf<String, TestResult>()
        runHttpsDiagnosticTest(results)

        val httpsResult = results["https"]
        assertNotNull(httpsResult)
        assertTrue(
            "HTTPS test status should be SUCCESS or FAILED depending on network",
            httpsResult!!.status == TestStatus.SUCCESS || httpsResult.status == TestStatus.FAILED
        )
        assertTrue("HTTPS test message should not be empty", httpsResult.message.isNotBlank())
    }

    @Test
    fun testRunDnsResolutionTestExecution() = runBlocking {
        val results = mutableMapOf<String, TestResult>()
        runDnsResolutionTest(results)

        val dnsResult = results["dns"]
        assertNotNull(dnsResult)
        assertTrue("DNS test message should not be empty", dnsResult!!.message.isNotBlank())
    }

    @Test
    fun testRunServerTcpTestExecution() = runBlocking {
        val results = mutableMapOf<String, TestResult>()
        val dummyServer = V2RayServer(address = "127.0.0.1", port = 1)
        runServerTcpTest(dummyServer, results)

        val handshakeResult = results["handshake"]
        assertNotNull(handshakeResult)
        assertTrue("Server TCP test message should not be empty", handshakeResult!!.message.isNotBlank())
    }

    @Test
    fun testRunUdpTestExecution() = runBlocking {
        val results = mutableMapOf<String, TestResult>()
        runUdpTest(results)

        val udpResult = results["udp"]
        assertNotNull(udpResult)
        assertTrue("UDP test message should not be empty", udpResult!!.message.isNotBlank())
    }
}
