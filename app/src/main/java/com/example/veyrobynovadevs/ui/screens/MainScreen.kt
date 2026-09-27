package com.example.veyrobynovadevs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.ui.components.DeveloperWatermark
import com.example.veyrobynovadevs.ui.components.ServerCard
import com.example.veyrobynovadevs.ui.components.TrafficStatsCard
import com.example.veyrobynovadevs.ui.components.VpnPowerButton
import com.example.veyrobynovadevs.ui.theme.VpnConnectedGreen
import com.example.veyrobynovadevs.vpn.VpnState
import com.example.veyrobynovadevs.update.UpdateState
import com.example.veyrobynovadevs.update.UpdateInfo
import androidx.compose.material3.LinearProgressIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vpnState: VpnState,
    selectedServer: V2RayServer?,
    connectTimeMillis: Long?,
    bytesReceived: Long,
    bytesSent: Long,
    updateState: UpdateState,
    onToggleVpn: () -> Unit,
    onChangeServerClick: () -> Unit,
    onTestAndFilterLiveClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onUpdateConfirm: (UpdateInfo) -> Unit,
    onUpdateDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    when (val state = updateState) {
        is UpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { if (!state.updateInfo.mandatory) onUpdateDismiss() },
                title = { Text(text = state.updateInfo.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Version ${state.updateInfo.versionName} is available.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("What's new:", fontWeight = FontWeight.Bold)
                        state.updateInfo.changelog.forEach { log ->
                            Text("• $log", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Size: ${formatBytes(state.updateInfo.size)}", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(onClick = { onUpdateConfirm(state.updateInfo) }) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    if (!state.updateInfo.mandatory) {
                        TextButton(onClick = onUpdateDismiss) {
                            Text("Later")
                        }
                    }
                }
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Downloading Update") },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${formatBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatBytes(state.totalBytes) else "Unknown"}", fontSize = 12.sp)
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateState.Verifying -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Verifying Update") },
                text = { Text("Checking file integrity...") },
                confirmButton = {}
            )
        }
        is UpdateState.Installing -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Installing Update") },
                text = { Text("Launching package installer...") },
                confirmButton = {}
            )
        }
        else -> {}
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = {
                Text(
                    text = "Disconnect VPN?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect from Veyro VPN? Your network connection will no longer be encrypted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        onToggleVpn()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "App Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Veyro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "by Nova devs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Protected / Unprotected Pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (vpnState == VpnState.CONNECTED) VpnConnectedGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (vpnState == VpnState.CONNECTED) "PROTECTED" else "UNPROTECTED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vpnState == VpnState.CONNECTED) VpnConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onAboutClick) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "About App",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Central VPN Power Button
                VpnPowerButton(
                    vpnState = vpnState,
                    onClick = {
                        if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING) {
                            showDisconnectDialog = true
                        } else {
                            onToggleVpn()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Selected Server Card
                ServerCard(
                    server = selectedServer,
                    onChangeServerClick = onChangeServerClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dashboard Action Buttons Row: View Connection Logs & Network Diagnostics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewLogsClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDiagnosticsClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Prominent "Test & Filter Live Proxies" Dashboard Action Button
                Button(
                    onClick = onTestAndFilterLiveClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test & Filter Live Proxies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Proxy Statistics Card
                TrafficStatsCard(
                    vpnState = vpnState,
                    connectTimeMillis = connectTimeMillis,
                    bytesReceived = bytesReceived,
                    bytesSent = bytesSent
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                DeveloperWatermark()
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
