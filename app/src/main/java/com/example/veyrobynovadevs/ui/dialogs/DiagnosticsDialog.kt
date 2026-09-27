package com.example.veyrobynovadevs.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.veyrobynovadevs.data.ServerRepository
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.vpn.VeyroEvent
import com.example.veyrobynovadevs.vpn.VeyroLogger
import com.example.veyrobynovadevs.vpn.VpnState
import com.example.veyrobynovadevs.vpn.VpnStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

enum class TestStatus {
    IDLE, RUNNING, SUCCESS, FAILED
}

data class TestResult(
    val status: TestStatus = TestStatus.IDLE,
    val message: String = "",
    val durationMs: Long = 0L
)

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun formatDuration(connectTimeMs: Long?): String {
    if (connectTimeMs == null) return "Disconnected"
    val elapsedSec = (System.currentTimeMillis() - connectTimeMs) / 1000
    val hours = elapsedSec / 3600
    val minutes = (elapsedSec % 3600) / 60
    val seconds = elapsedSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun DiagnosticsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vpnState by VpnStateHolder.vpnState.collectAsState()
    val connectedServer by VpnStateHolder.connectedServer.collectAsState()
    val selectedServer by ServerRepository.getInstance(context).selectedServer.collectAsState()
    val rxBytes by VpnStateHolder.bytesReceived.collectAsState()
    val txBytes by VpnStateHolder.bytesSent.collectAsState()
    val connectTimeMs by VpnStateHolder.connectTimeMillis.collectAsState()

    val targetServer = connectedServer ?: selectedServer
    val isVpnConnected = vpnState == VpnState.CONNECTED

    val testResults = remember {
        mutableStateMapOf(
            "https" to TestResult(),
            "dns" to TestResult(),
            "handshake" to TestResult(),
            "udp" to TestResult()
        )
    }

    var isRunningAll by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Network Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Native Xray TUN status & end-to-end tests",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // VPN Stack Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "VPN STACK STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. VPN Tunnel Interface Status
                        StatusRow(
                            label = "VPN Tunnel Interface",
                            subLabel = if (vpnState == VpnState.CONNECTED) "Active (172.19.0.1 / IPv4-only)" else "Disconnected",
                            isActive = vpnState == VpnState.CONNECTED
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. sing-box Core Engine Status
                        StatusRow(
                            label = "sing-box Engine",
                            subLabel = if (vpnState == VpnState.CONNECTED) "Active (libbox TUN Inbound)" else "Inactive",
                            isActive = vpnState == VpnState.CONNECTED
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 4. Traffic Stats
                        StatusRow(
                            label = "Traffic Statistics",
                            subLabel = "Rx: ${formatBytes(rxBytes)} | Tx: ${formatBytes(txBytes)}",
                            isActive = vpnState == VpnState.CONNECTED
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 5. Connection Duration
                        StatusRow(
                            label = "Connected Time",
                            subLabel = formatDuration(connectTimeMs),
                            isActive = vpnState == VpnState.CONNECTED
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AUTOMATED DIAGNOSTIC TESTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test Item 1: HTTPS Diagnostic Test
                DiagnosticTestItem(
                    title = "1. HTTPS End-to-End Test",
                    description = "Performs HTTPS request to https://www.google.com with 5000ms timeout",
                    result = testResults["https"] ?: TestResult(),
                    onRunTest = {
                        scope.launch { runHttpsDiagnosticTest(testResults) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test Item 2: DNS Resolution Test
                DiagnosticTestItem(
                    title = "2. DNS Resolution Test",
                    description = "Queries DNS resolution for www.google.com",
                    result = testResults["dns"] ?: TestResult(),
                    onRunTest = {
                        scope.launch { runDnsResolutionTest(testResults) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test Item 3: Server TCP Socket Test
                DiagnosticTestItem(
                    title = "3. Server TCP Socket Test",
                    description = "Verifies TCP socket connection to proxy server address:port",
                    result = testResults["handshake"] ?: TestResult(),
                    onRunTest = {
                        scope.launch { runServerTcpTest(targetServer, testResults) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test Item 4: UDP Echo Test
                DiagnosticTestItem(
                    title = "4. UDP Connectivity Test",
                    description = "Sends UDP packet to 1.1.1.1:53 with 5000ms timeout",
                    result = testResults["udp"] ?: TestResult(),
                    onRunTest = {
                        scope.launch { runUdpTest(testResults) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Run All Button
                Button(
                    onClick = {
                        scope.launch {
                            isRunningAll = true
                            runHttpsDiagnosticTest(testResults)
                            runDnsResolutionTest(testResults)
                            runServerTcpTest(targetServer, testResults)
                            runUdpTest(testResults)
                            isRunningAll = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRunningAll
                ) {
                    if (isRunningAll) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Diagnostics...", fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run All Diagnostic Tests", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    subLabel: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isActive) "ACTIVE" else "INACTIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF4CAF50) else Color.Gray
            )
        }
    }
}

@Composable
fun DiagnosticTestItem(
    title: String,
    description: String,
    result: TestResult,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (result.status != TestStatus.IDLE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (result.status) {
                            TestStatus.RUNNING -> {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            TestStatus.SUCCESS -> {
                                Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(result.message, fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                            TestStatus.FAILED -> {
                                Icon(imageVector = Icons.Rounded.Error, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(result.message, fontSize = 11.sp, color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                            }
                            else -> {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onRunTest,
                enabled = result.status != TestStatus.RUNNING,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Run", fontSize = 11.sp)
            }
        }
    }
}

// Diagnostic Test Implementation Functions
suspend fun runHttpsDiagnosticTest(
    results: MutableMap<String, TestResult>
) = withContext(Dispatchers.IO) {
    results["https"] = TestResult(status = TestStatus.RUNNING)
    val startTime = System.currentTimeMillis()

    try {
        val url = URL("https://www.google.com")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        val duration = System.currentTimeMillis() - startTime
        connection.disconnect()

        if (responseCode in 200..399 || responseCode == 404) {
            results["https"] = TestResult(
                status = TestStatus.SUCCESS,
                message = "PASSED: HTTP $responseCode (${duration} ms)",
                durationMs = duration
            )
            VeyroLogger.i("Diagnostics", "HTTPS diagnostic test PASSED: responseCode=$responseCode in ${duration}ms")
        } else {
            results["https"] = TestResult(
                status = TestStatus.FAILED,
                message = "FAILED: Response Code $responseCode (${duration} ms)",
                durationMs = duration
            )
            VeyroLogger.w("Diagnostics", "HTTPS diagnostic test FAILED: responseCode=$responseCode in ${duration}ms")
        }
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        VeyroLogger.w("Diagnostics", "HTTPS diagnostic test error: ${e.message}")
        results["https"] = TestResult(
            status = TestStatus.FAILED,
            message = "FAILED: ${e.message ?: e.javaClass.simpleName} (${duration} ms)",
            durationMs = duration
        )
    }
}

suspend fun runDnsResolutionTest(
    results: MutableMap<String, TestResult>
) = withContext(Dispatchers.IO) {
    results["dns"] = TestResult(status = TestStatus.RUNNING)
    val startTime = System.currentTimeMillis()

    try {
        VeyroLogger.logEvent(VeyroEvent.DNS_QUERY, "Diagnostics", "DNS query for www.google.com")
        val addresses = InetAddress.getAllByName("www.google.com")
        val duration = System.currentTimeMillis() - startTime

        if (addresses.isNotEmpty()) {
            val resolvedIp = addresses[0].hostAddress ?: "127.0.0.1"
            VeyroLogger.logEvent(VeyroEvent.DNS_RESPONSE, "Diagnostics", "Resolved www.google.com to $resolvedIp")
            results["dns"] = TestResult(
                status = TestStatus.SUCCESS,
                message = "PASSED: Resolved www.google.com -> $resolvedIp (${duration} ms)",
                durationMs = duration
            )
        } else {
            results["dns"] = TestResult(
                status = TestStatus.FAILED,
                message = "FAILED: No IP returned for www.google.com"
            )
        }
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        VeyroLogger.w("Diagnostics", "DNS test error: ${e.message}")
        results["dns"] = TestResult(
            status = TestStatus.FAILED,
            message = "FAILED: ${e.message ?: e.javaClass.simpleName} (${duration} ms)",
            durationMs = duration
        )
    }
}

suspend fun runServerTcpTest(
    targetServer: V2RayServer?,
    results: MutableMap<String, TestResult>
) = withContext(Dispatchers.IO) {
    results["handshake"] = TestResult(status = TestStatus.RUNNING)
    val startTime = System.currentTimeMillis()

    try {
        val host = targetServer?.address?.takeIf { it.isNotBlank() } ?: "1.1.1.1"
        val port = if ((targetServer?.port ?: 0) > 0) targetServer!!.port else 443

        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.close()

        val duration = System.currentTimeMillis() - startTime
        results["handshake"] = TestResult(
            status = TestStatus.SUCCESS,
            message = "PASSED: Server TCP Socket Connected ($host:$port) (${duration} ms)",
            durationMs = duration
        )
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        VeyroLogger.w("Diagnostics", "Server TCP test error: ${e.message}")
        results["handshake"] = TestResult(
            status = TestStatus.FAILED,
            message = "FAILED: ${e.message ?: e.javaClass.simpleName} (${duration} ms)",
            durationMs = duration
        )
    }
}

suspend fun runUdpTest(
    results: MutableMap<String, TestResult>
) = withContext(Dispatchers.IO) {
    results["udp"] = TestResult(status = TestStatus.RUNNING)
    val startTime = System.currentTimeMillis()

    try {
        val udpSocket = DatagramSocket()
        udpSocket.soTimeout = 5000

        val targetAddr = InetAddress.getByName("1.1.1.1")
        val dummyQuery = byteArrayOf(0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1)
        val packet = DatagramPacket(dummyQuery, dummyQuery.size, targetAddr, 53)
        udpSocket.send(packet)

        val recvBuf = ByteArray(512)
        val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
        udpSocket.receive(recvPacket)
        udpSocket.close()

        val duration = System.currentTimeMillis() - startTime
        if (recvPacket.length > 0) {
            results["udp"] = TestResult(
                status = TestStatus.SUCCESS,
                message = "PASSED: UDP Transmit & Reply OK (${recvPacket.length} bytes) (${duration} ms)",
                durationMs = duration
            )
        } else {
            results["udp"] = TestResult(
                status = TestStatus.FAILED,
                message = "FAILED: No UDP packet received"
            )
        }
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        VeyroLogger.w("Diagnostics", "UDP test error: ${e.message}")
        results["udp"] = TestResult(
            status = TestStatus.FAILED,
            message = "FAILED: ${e.message ?: e.javaClass.simpleName} (${duration} ms)",
            durationMs = duration
        )
    }
}
