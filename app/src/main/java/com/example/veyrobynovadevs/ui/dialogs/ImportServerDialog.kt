package com.example.veyrobynovadevs.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.veyrobynovadevs.data.ServerRepository
import com.example.veyrobynovadevs.model.V2RayServer

@Composable
fun ImportServerDialog(
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current

    var remoteUrl by remember { mutableStateOf(ServerRepository.DEFAULT_SERVERS_URL) }
    var clipboardText by remember { mutableStateOf("") }

    // Manual Creation fields
    var manualName by remember { mutableStateOf("Custom Node") }
    var manualAddress by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("443") }
    var manualUuid by remember { mutableStateOf("") }
    var manualPath by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val textContent = readTextFromUri(context, uri)
            if (!textContent.isNullOrEmpty()) {
                onImportText(textContent)
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            if (remoteUrl.isNotBlank()) {
                                onImportUrl(remoteUrl.trim())
                                onDismiss()
                            }
                        }
                        1 -> {
                            if (clipboardText.isNotBlank()) {
                                onImportText(clipboardText.trim())
                                onDismiss()
                            }
                        }
                        2 -> {
                            filePickerLauncher.launch("*/*")
                        }
                        3 -> {
                            if (manualAddress.isNotBlank() && manualUuid.isNotBlank()) {
                                val portInt = manualPort.toIntOrNull() ?: 443
                                val newServer = V2RayServer(
                                    name = manualName.trim(),
                                    address = manualAddress.trim(),
                                    port = portInt,
                                    protocol = "vless",
                                    uuid = manualUuid.trim(),
                                    security = "tls",
                                    path = manualPath.trim(),
                                    host = manualHost.trim(),
                                    isCustom = true
                                )
                                onImportText(newServer.toVlessUri())
                                onDismiss()
                            }
                        }
                    }
                }
            ) {
                Text(
                    text = when (selectedTab) {
                        2 -> "Choose File"
                        else -> "Import"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Import / Add Server",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Link, contentDescription = "URL") },
                        text = { Text("URL", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = "Clipboard") },
                        text = { Text("Clipboard", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = "File") },
                        text = { Text("File", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = "Manual") },
                        text = { Text("Manual", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        Text(
                            text = "Enter remote URL containing VLESS / V2Ray JSON or subscription link:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = remoteUrl,
                            onValueChange = { remoteUrl = it },
                            label = { Text("Subscription / JSON URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    1 -> {
                        Text(
                            text = "Paste V2Ray / VLESS configuration string or links:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = systemClipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (clipText.isNotBlank()) {
                                        clipboardText = clipText
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste from Clipboard")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = clipboardText,
                            onValueChange = { clipboardText = it },
                            label = { Text("VLESS / V2Ray text") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }

                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Select a local .json or config file from storage to parse V2Ray / VLESS nodes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    3 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Server Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row {
                                OutlinedTextField(
                                    value = manualAddress,
                                    onValueChange = { manualAddress = it },
                                    label = { Text("Address / IP") },
                                    modifier = Modifier.weight(2f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = manualPort,
                                    onValueChange = { manualPort = it },
                                    label = { Text("Port") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = manualUuid,
                                onValueChange = { manualUuid = it },
                                label = { Text("UUID / Password") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualPath,
                                onValueChange = { manualPath = it },
                                label = { Text("Path (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualHost,
                                onValueChange = { manualHost = it },
                                label = { Text("Host / SNI (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
