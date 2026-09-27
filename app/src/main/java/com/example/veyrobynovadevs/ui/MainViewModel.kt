package com.example.veyrobynovadevs.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.veyrobynovadevs.data.ServerRepository
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.update.UpdateInfo
import com.example.veyrobynovadevs.update.UpdateManager
import com.example.veyrobynovadevs.vpn.VeyroVpnService
import com.example.veyrobynovadevs.vpn.VpnState
import com.example.veyrobynovadevs.vpn.VpnStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServerRepository.getInstance(application)

    val vpnState: StateFlow<VpnState> = VpnStateHolder.vpnState
    val connectedServer: StateFlow<V2RayServer?> = VpnStateHolder.connectedServer
    val connectTimeMillis: StateFlow<Long?> = VpnStateHolder.connectTimeMillis
    val bytesReceived: StateFlow<Long> = VpnStateHolder.bytesReceived
    val bytesSent: StateFlow<Long> = VpnStateHolder.bytesSent

    val servers: StateFlow<List<V2RayServer>> = repository.servers
    val selectedServer: StateFlow<V2RayServer?> = repository.selectedServer
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage

    private val _pingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val pingProgress: StateFlow<Pair<Int, Int>?> = _pingProgress.asStateFlow()

    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    private val updateManager = UpdateManager(application)
    val updateState = updateManager.updateState

    init {
        // Initial fetch if list is empty
        if (servers.value.isEmpty()) {
            fetchRemoteServers()
        }
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean = true) {
        viewModelScope.launch {
            updateManager.checkForUpdates(manual)
        }
    }

    fun startUpdateDownload(updateInfo: UpdateInfo) {
        viewModelScope.launch {
            updateManager.downloadAndInstallUpdate(updateInfo)
        }
    }

    fun dismissUpdate() {
        updateManager.dismissUpdate()
    }

    fun fetchRemoteServers() {
        viewModelScope.launch {
            repository.fetchRemoteServers()
        }
    }

    fun toggleVpn(context: Context, onPrepareNeeded: () -> Unit) {
        val currentState = vpnState.value
        if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING) {
            VeyroVpnService.stopVpn(context)
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                onPrepareNeeded()
            } else {
                startVpnDirectly(context)
            }
        }
    }

    fun startVpnDirectly(context: Context, isUserTap: Boolean = true, connectSource: String = "UserUI") {
        viewModelScope.launch {
            val serverToConnect = selectedServer.value ?: servers.value.firstOrNull()
            if (serverToConnect == null) {
                _uiNotice.value = "No server selected or available to connect."
                return@launch
            }

            VeyroVpnService.startVpn(context, serverToConnect, isUserTap = isUserTap, connectSource = connectSource)
        }
    }

    fun selectServer(server: V2RayServer, isUserTap: Boolean = true, connectSource: String = "UserUI_ServerList") {
        repository.selectServer(server)
        // CRITICAL: DO NOT automatically reconnect the VPN just because the selected server changed.
        // A server-list emission must NEVER initiate a VPN connection.
        // The user must explicitly press Connect to start a new session.
    }

    fun testAndSortServers() {
        viewModelScope.launch {
            _pingProgress.value = Pair(0, servers.value.size)
            repository.testAndSortServers { completed, total ->
                _pingProgress.value = Pair(completed, total)
            }
            _pingProgress.value = null
        }
    }

    fun testAndFilterLiveProxies() {
        viewModelScope.launch {
            _pingProgress.value = Pair(0, servers.value.size)
            repository.testAndSortServers { completed, total ->
                _pingProgress.value = Pair(completed, total)
            }
            _pingProgress.value = null

            val onlineCount = servers.value.count { it.isOnline }
            val totalCount = servers.value.size

            if (onlineCount > 0) {
                _uiNotice.value = "Tested all proxies: $onlineCount of $totalCount online."
            } else {
                _uiNotice.value = "Tested all proxies: 0 of $totalCount online (all dead)."
            }
        }
    }

    fun autoSelectBestServer() {
        viewModelScope.launch {
            _pingProgress.value = Pair(0, servers.value.size)
            repository.testAndSortServers { completed, total ->
                _pingProgress.value = Pair(completed, total)
            }
            _pingProgress.value = null

            val bestServer = servers.value.firstOrNull { it.isOnline }

            if (bestServer != null) {
                selectServer(bestServer, isUserTap = false)
                _uiNotice.value = "Selected best online server: ${bestServer.name} (${bestServer.displayLatency})"
            } else {
                _uiNotice.value = "No reachable online proxies found."
            }
        }
    }

    fun importFromUrl(url: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.importFromUrl(url)
            if (result.isSuccess) {
                val count = result.getOrNull()?.size ?: 0
                _uiNotice.value = "Successfully imported $count server(s)!"
                onComplete(true)
            } else {
                _uiNotice.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to import from URL"
                onComplete(false)
            }
        }
    }

    fun importFromText(text: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.importFromText(text)
            if (result.isSuccess) {
                val count = result.getOrNull()?.size ?: 0
                _uiNotice.value = "Successfully imported $count server(s)!"
                onComplete(true)
            } else {
                _uiNotice.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to import configurations"
                onComplete(false)
            }
        }
    }

    fun pasteFromClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            val text = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.text?.toString() ?: ""
            } else ""

            if (text.isBlank()) {
                _uiNotice.value = "Clipboard is empty!"
                return
            }

            importFromText(text)
        } catch (e: Exception) {
            _uiNotice.value = "Clipboard read error: ${e.localizedMessage}"
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteServer(serverId)
            _uiNotice.value = "Server removed."
        }
    }

    fun deleteDeadServers() {
        viewModelScope.launch {
            val deadCount = servers.value.count { !it.isOnline && it.lastTestedAt > 0L }
            repository.deleteDeadServers()
            _uiNotice.value = "Removed $deadCount dead server(s)."
        }
    }

    fun clearUiNotice() {
        _uiNotice.value = null
    }
}
