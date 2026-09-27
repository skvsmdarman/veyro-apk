package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.ping.PingTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingTesterTest {

    @Test
    fun testServerOnlineDeadStatus() {
        val onlineServer = V2RayServer(id = "1", name = "Online", latencyMs = 120L, isOnline = true)
        val deadServer = V2RayServer(id = "2", name = "Dead", latencyMs = -1L, isOnline = false)

        assertTrue(onlineServer.isOnline)
        assertEquals("120 ms", onlineServer.displayLatency)

        assertFalse(deadServer.isOnline)
        assertEquals("DEAD / UNREACHABLE", deadServer.displayLatency)
    }

    @Test
    fun testSortByLowestPing() {
        val serverFast = V2RayServer(id = "1", name = "Fast", latencyMs = 50L, isOnline = true)
        val serverSlow = V2RayServer(id = "2", name = "Slow", latencyMs = 350L, isOnline = true)
        val serverMedium = V2RayServer(id = "3", name = "Medium", latencyMs = 120L, isOnline = true)
        val serverOffline = V2RayServer(id = "4", name = "Offline", latencyMs = -1L, isOnline = false)
        val serverTimeout = V2RayServer(id = "5", name = "Timeout", latencyMs = -1L, isOnline = false)

        val list = listOf(serverSlow, serverOffline, serverFast, serverTimeout, serverMedium)
        val sorted = PingTester.sortByLowestPing(list)

        assertEquals("Fast", sorted[0].name)
        assertEquals("Medium", sorted[1].name)
        assertEquals("Slow", sorted[2].name)
        assertFalse(sorted[3].isOnline)
        assertFalse(sorted[4].isOnline)
    }
}
