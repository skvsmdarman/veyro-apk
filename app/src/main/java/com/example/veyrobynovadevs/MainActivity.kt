package com.example.veyrobynovadevs

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.veyrobynovadevs.data.ServerRepository
import com.example.veyrobynovadevs.ui.MainViewModel
import com.example.veyrobynovadevs.ui.dialogs.AboutDialog
import com.example.veyrobynovadevs.ui.dialogs.DiagnosticsDialog
import com.example.veyrobynovadevs.ui.dialogs.ImportServerDialog
import com.example.veyrobynovadevs.ui.dialogs.LogsDialog
import com.example.veyrobynovadevs.ui.screens.MainScreen
import com.example.veyrobynovadevs.ui.screens.ServerListScreen
import com.example.veyrobynovadevs.ui.theme.VeyroByNovaDevsTheme
import com.example.veyrobynovadevs.vpn.VeyroVpnService

enum class Screen {
    MAIN,
    SERVER_LIST
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentExtras(intent)

        setContent {
            VeyroByNovaDevsTheme {
                VeyroApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("auto_disconnect", false) == true) {
            VeyroVpnService.stopVpn(applicationContext)
        } else if (intent?.getBooleanExtra("auto_connect", false) == true) {
            val defaultServer = ServerRepository.BUILTIN_DEFAULT_SERVERS.first()
            VeyroVpnService.startVpn(applicationContext, defaultServer)
        }
    }
}

@Composable
fun VeyroApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val vpnState by viewModel.vpnState.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val connectTimeMillis by viewModel.connectTimeMillis.collectAsState()
    val bytesReceived by viewModel.bytesReceived.collectAsState()
    val bytesSent by viewModel.bytesSent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pingProgress by viewModel.pingProgress.collectAsState()
    val uiNotice by viewModel.uiNotice.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    // Auto connect / disconnect for debug trigger
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity?.intent?.getBooleanExtra("auto_disconnect", false) == true) {
            VeyroVpnService.stopVpn(context)
        } else if (activity?.intent?.getBooleanExtra("auto_connect", false) == true) {
            viewModel.servers.collect { list ->
                if (list.isNotEmpty()) {
                    viewModel.startVpnDirectly(context, isUserTap = true, connectSource = "AutoConnectDebugIntent")
                    return@collect
                }
            }
        }
    }

    // Handle VPN prepare Intent
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnDirectly(context)
        } else {
            Toast.makeText(context, "VPN permission is required to connect.", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle UI notices / snackbars
    LaunchedEffect(uiNotice) {
        val notice = uiNotice
        if (!notice.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(notice)
            viewModel.clearUiNotice()
        }
    }

    // Handle back press when on ServerList screen
    BackHandler(enabled = currentScreen == Screen.SERVER_LIST) {
        currentScreen = Screen.MAIN
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screenTransition",
            modifier = Modifier.padding(innerPadding)
        ) { targetScreen ->
            when (targetScreen) {
                Screen.MAIN -> {
                    MainScreen(
                        vpnState = vpnState,
                        selectedServer = selectedServer,
                        connectTimeMillis = connectTimeMillis,
                        bytesReceived = bytesReceived,
                        bytesSent = bytesSent,
                        updateState = updateState,
                        onToggleVpn = {
                            viewModel.toggleVpn(
                                context = context,
                                onPrepareNeeded = {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPrepareLauncher.launch(intent)
                                    } else {
                                        viewModel.startVpnDirectly(context)
                                    }
                                }
                            )
                        },
                        onChangeServerClick = {
                            currentScreen = Screen.SERVER_LIST
                        },
                        onTestAndFilterLiveClick = {
                            viewModel.testAndFilterLiveProxies()
                        },
                        onViewLogsClick = {
                            showLogsDialog = true
                        },
                        onDiagnosticsClick = {
                            showDiagnosticsDialog = true
                        },
                        onAboutClick = {
                            showAboutDialog = true
                        },
                        onUpdateConfirm = { updateInfo ->
                            viewModel.startUpdateDownload(updateInfo)
                        },
                        onUpdateDismiss = {
                            viewModel.dismissUpdate()
                        }
                    )
                }

                Screen.SERVER_LIST -> {
                    ServerListScreen(
                        servers = servers,
                        selectedServer = selectedServer,
                        isLoading = isLoading,
                        pingProgress = pingProgress,
                        onSelectServer = { server ->
                            viewModel.selectServer(server)
                            currentScreen = Screen.MAIN
                        },
                        onAutoSelectBest = {
                            viewModel.autoSelectBestServer()
                        },
                        onPasteClipboard = {
                            viewModel.pasteFromClipboard(context)
                        },
                        onPingAll = {
                            viewModel.testAndSortServers()
                        },
                        onRefreshServers = {
                            viewModel.fetchRemoteServers()
                        },
                        onOpenImportDialog = {
                            showImportDialog = true
                        },
                        onDeleteServer = { serverId ->
                            viewModel.deleteServer(serverId)
                        },
                        onDeleteDeadServers = {
                            viewModel.deleteDeadServers()
                        },
                        onBackClick = {
                            currentScreen = Screen.MAIN
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showImportDialog) {
        ImportServerDialog(
            onDismiss = { showImportDialog = false },
            onImportUrl = { url ->
                viewModel.importFromUrl(url)
            },
            onImportText = { text ->
                viewModel.importFromText(text)
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showLogsDialog) {
        LogsDialog(
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
}
