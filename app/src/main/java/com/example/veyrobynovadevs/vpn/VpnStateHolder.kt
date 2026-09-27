package com.example.veyrobynovadevs.vpn

import com.example.veyrobynovadevs.model.V2RayServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateHolder {
    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _connectedServer = MutableStateFlow<V2RayServer?>(null)
    val connectedServer: StateFlow<V2RayServer?> = _connectedServer.asStateFlow()

    private val _connectTimeMillis = MutableStateFlow<Long?>(null)
    val connectTimeMillis: StateFlow<Long?> = _connectTimeMillis.asStateFlow()

    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _tunRxPackets = MutableStateFlow(0L)
    val tunRxPackets: StateFlow<Long> = _tunRxPackets.asStateFlow()

    private val _tunRxBytes = MutableStateFlow(0L)
    val tunRxBytes: StateFlow<Long> = _tunRxBytes.asStateFlow()

    private val _tunTxPackets = MutableStateFlow(0L)
    val tunTxPackets: StateFlow<Long> = _tunTxPackets.asStateFlow()

    private val _tunTxBytes = MutableStateFlow(0L)
    val tunTxBytes: StateFlow<Long> = _tunTxBytes.asStateFlow()

    fun updateState(state: VpnState, server: V2RayServer? = _connectedServer.value) {
        _vpnState.value = state
        _connectedServer.value = server
        if (state == VpnState.CONNECTED && _connectTimeMillis.value == null) {
            _connectTimeMillis.value = System.currentTimeMillis()
        } else if (state == VpnState.DISCONNECTED) {
            _connectTimeMillis.value = null
            _bytesReceived.value = 0L
            _bytesSent.value = 0L
            _tunRxPackets.value = 0L
            _tunRxBytes.value = 0L
            _tunTxPackets.value = 0L
            _tunTxBytes.value = 0L
        }
    }

    fun updateTraffic(rx: Long, tx: Long) {
        _bytesReceived.value = rx
        _bytesSent.value = tx
        _tunRxBytes.value = rx
        _tunTxBytes.value = tx
    }

    fun updateTrafficStats(bytesSent: Long, bytesReceived: Long) {
        _bytesSent.value = bytesSent
        _bytesReceived.value = bytesReceived
        _tunTxBytes.value = bytesSent
        _tunRxBytes.value = bytesReceived
    }

    fun updateTunStats(rxPackets: Long, rxBytes: Long, txPackets: Long, txBytes: Long) {
        _tunRxPackets.value = rxPackets
        _tunRxBytes.value = rxBytes
        _tunTxPackets.value = txPackets
        _tunTxBytes.value = txBytes
        _bytesReceived.value = rxBytes
        _bytesSent.value = txBytes
        VeyroLogger.i("VpnStateHolder", "STATS_RECEIVED up=$txBytes down=$rxBytes")
    }
}
