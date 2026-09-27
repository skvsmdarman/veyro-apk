package com.example.veyrobynovadevs.ping

import com.example.veyrobynovadevs.model.V2RayServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingTester {

    private const val DEFAULT_TIMEOUT_MS = 2500

    /**
     * Performs a real TCP socket handshake check for a single server.
     * Returns an updated V2RayServer with latencyMs, isOnline, and lastTestedAt populated.
     * If socket cannot connect or times out, marks isOnline = false and latencyMs = -1L ("DEAD").
     */
    suspend fun testServerConnectivity(
        server: V2RayServer,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): V2RayServer = withContext(Dispatchers.IO) {
        if (server.address.isEmpty() || server.port <= 0) {
            return@withContext server.copy(
                latencyMs = -1L,
                isOnline = false,
                lastTestedAt = System.currentTimeMillis()
            )
        }

        val socket = Socket()
        val startTime = System.currentTimeMillis()
        try {
            val address = InetSocketAddress(server.address, server.port)
            socket.connect(address, timeoutMs)
            val endTime = System.currentTimeMillis()
            socket.close()
            val ping = endTime - startTime
            if (ping in 0..timeoutMs.toLong()) {
                server.copy(
                    latencyMs = ping,
                    isOnline = true,
                    lastTestedAt = System.currentTimeMillis()
                )
            } else {
                server.copy(
                    latencyMs = -1L,
                    isOnline = false,
                    lastTestedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {}
            server.copy(
                latencyMs = -1L,
                isOnline = false,
                lastTestedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Legacy helper to get latency in ms, or -1 if unreachable/dead.
     */
    suspend fun testServerPing(server: V2RayServer, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Long {
        return testServerConnectivity(server, timeoutMs).latencyMs
    }

    /**
     * Tests latency for a list of servers concurrently and returns updated list with connectivity results.
     */
    suspend fun testAllServersPing(
        servers: List<V2RayServer>,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        onProgress: ((completed: Int, total: Int, updatedServer: V2RayServer) -> Unit)? = null
    ): List<V2RayServer> = coroutineScope {
        var completedCount = 0
        val total = servers.size

        val deferredList = servers.map { server ->
            async(Dispatchers.IO) {
                val updatedServer = testServerConnectivity(server, timeoutMs)
                synchronized(this@coroutineScope) {
                    completedCount++
                    onProgress?.invoke(completedCount, total, updatedServer)
                }
                updatedServer
            }
        }

        deferredList.awaitAll()
    }

    /**
     * Sorts servers by lowest ping.
     * Online servers (isOnline == true) come first, sorted ascending by latencyMs.
     * Offline or dead servers (isOnline == false) appear at the bottom.
     */
    fun sortByLowestPing(servers: List<V2RayServer>): List<V2RayServer> {
        return servers.sortedWith { s1, s2 ->
            when {
                s1.isOnline && s2.isOnline -> s1.latencyMs.compareTo(s2.latencyMs)
                s1.isOnline && !s2.isOnline -> -1
                !s1.isOnline && s2.isOnline -> 1
                else -> 0
            }
        }
    }
}
