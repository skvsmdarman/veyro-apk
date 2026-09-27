package com.example.veyrobynovadevs.vpn

import com.example.veyrobynovadevs.model.V2RayServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel

class VeyroVpnServiceTest {

    private class DummyVeyroVpnService : VeyroVpnService() {
        var protectSocketCalled = false
        var protectDatagramCalled = false
        var protectFdCalled = false
        var returnStatus = true

        override fun protect(socket: Socket): Boolean {
            protectSocketCalled = true
            return returnStatus
        }

        override fun protect(socket: DatagramSocket): Boolean {
            protectDatagramCalled = true
            return returnStatus
        }

        override fun protect(socket: Int): Boolean {
            protectFdCalled = true
            return returnStatus
        }
    }

    @Before
    fun setUp() {
        VeyroVpnService.activeInstance = null
        VeyroLogger.clearLogs()
    }

    @After
    fun tearDown() {
        VeyroVpnService.activeInstance = null
        VeyroLogger.clearLogs()
    }

    @Test
    fun testVpnStateHolderTunStatsUpdatesAndReset() {
        VpnStateHolder.updateTunStats(
            rxPackets = 150L,
            rxBytes = 102400L,
            txPackets = 80L,
            txBytes = 51200L
        )

        assertEquals(150L, VpnStateHolder.tunRxPackets.value)
        assertEquals(102400L, VpnStateHolder.tunRxBytes.value)
        assertEquals(80L, VpnStateHolder.tunTxPackets.value)
        assertEquals(51200L, VpnStateHolder.tunTxBytes.value)
        assertEquals(102400L, VpnStateHolder.bytesReceived.value)
        assertEquals(51200L, VpnStateHolder.bytesSent.value)

        // Test state transition to DISCONNECTED resets all counters
        VpnStateHolder.updateState(VpnState.DISCONNECTED)

        assertEquals(0L, VpnStateHolder.tunRxPackets.value)
        assertEquals(0L, VpnStateHolder.tunRxBytes.value)
        assertEquals(0L, VpnStateHolder.tunTxPackets.value)
        assertEquals(0L, VpnStateHolder.tunTxBytes.value)
        assertEquals(0L, VpnStateHolder.bytesReceived.value)
        assertEquals(0L, VpnStateHolder.bytesSent.value)
    }
}
