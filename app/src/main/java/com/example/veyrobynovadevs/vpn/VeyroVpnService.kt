package com.example.veyrobynovadevs.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.core.app.NotificationCompat
import com.example.veyrobynovadevs.MainActivity
import com.example.veyrobynovadevs.R
import com.example.veyrobynovadevs.data.ServerRepository
import com.example.veyrobynovadevs.model.SingBoxConfigBuilder
import com.example.veyrobynovadevs.model.V2RayServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.nekohasekai.libbox.*
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

open class VeyroVpnService : VpnService() {

    enum class ServiceState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        STOPPING
    }

    companion object {
        private const val TAG = "VeyroVpnService"
        const val CHANNEL_ID = "veyro_vpn_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "com.example.veyrobynovadevs.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.veyrobynovadevs.vpn.DISCONNECT"
        const val EXTRA_SERVER_JSON = "extra_server_json"
        const val EXTRA_USER_TAP = "extra_user_tap"
        const val EXTRA_CONNECT_SOURCE = "extra_connect_source"
        const val EXTRA_USER_ACTION_ID = "extra_user_action_id"

        private val globalGenerationCounter = AtomicLong(0L)

        @Volatile
        var activeInstance: VeyroVpnService? = null

        fun startVpn(
            context: Context,
            server: V2RayServer,
            isUserTap: Boolean = true,
            connectSource: String = "UserUI",
            userActionId: String = UUID.randomUUID().toString()
        ) {
            val gen = globalGenerationCounter.incrementAndGet()
            VeyroLogger.i(TAG, "CONNECT_SOURCE=$connectSource USER_TAP=$isUserTap selectedServerId=${server.id} selectedServerAddress=${server.address}:${server.port} generation=$gen USER_ACTION_ID=$userActionId")
            VeyroLogger.i(TAG, "CONNECT_REQUEST source=$connectSource userActionId=$userActionId serverId=${server.id} serverAddress=${server.address}:${server.port} generation=$gen")

            val intent = Intent(context, VeyroVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_JSON, Json.encodeToString(server))
                putExtra(EXTRA_USER_TAP, isUserTap)
                putExtra(EXTRA_CONNECT_SOURCE, connectSource)
                putExtra(EXTRA_USER_ACTION_ID, userActionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val gen = globalGenerationCounter.get()
            VeyroLogger.i(TAG, "USER_DISCONNECT_REQUEST generation=$gen")
            VeyroLogger.i(TAG, "VPN_STOP_REQUEST generation=$gen reason=UserToggle caller=UserUI")

            val instance = activeInstance
            if (instance != null) {
                instance.performHardDisconnect("UserToggle")
            } else {
                val intent = Intent(context, VeyroVpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                context.startService(intent)
            }

            try {
                val stopIntent = Intent(context, VeyroVpnService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {
                VeyroLogger.w(TAG, "stopService failed: ${e.message}")
            }
        }

        init {
            try {
                Os.setenv("GODEBUG", "runtime_epoll_pwait2=0", true)
            } catch (e: Exception) {
                VeyroLogger.w(TAG, "Failed to set GODEBUG: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var boxService: CommandServer? = null
    private var commandClient: CommandClient? = null

    private val instanceId: Int get() = System.identityHashCode(this)

    @Volatile
    var currentState: ServiceState = ServiceState.DISCONNECTED
        private set

    @Volatile
    private var activeGeneration: Long = 0L

    @Volatile
    private var activeUserActionId: String = ""

    var currentServer: V2RayServer? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        VeyroLogger.i(TAG, "SERVICE_CREATED")
        VeyroLogger.i(TAG, "SERVICE_INSTANCE=$instanceId")
        logAlwaysOnStatus()
        createNotificationChannel()
    }

    private fun logAlwaysOnStatus() {
        try {
            val alwaysOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isAlwaysOn else false
            val lockdown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isLockdownEnabled else false
            VeyroLogger.i(TAG, "VPN_ALWAYS_ON=$alwaysOn")
            VeyroLogger.i(TAG, "VPN_LOCKDOWN=$lockdown")
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Failed to read Always-on status: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        val actionStr = intent?.action ?: "null"
        VeyroLogger.i(TAG, "SERVICE_START_COMMAND action=$actionStr instance=$instanceId")

        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            performHardDisconnect("ServiceIntentDisconnect")
            return START_NOT_STICKY
        }

        if (action == ACTION_CONNECT) {
            val isUserTap = intent.getBooleanExtra(EXTRA_USER_TAP, true)
            val connectSource = intent.getStringExtra(EXTRA_CONNECT_SOURCE) ?: "UnknownSource"
            val userActionId = intent.getStringExtra(EXTRA_USER_ACTION_ID) ?: ""
            val serverJson = intent.getStringExtra(EXTRA_SERVER_JSON)
            val server = if (!serverJson.isNullOrEmpty()) {
                try {
                    Json.decodeFromString<V2RayServer>(serverJson)
                } catch (_: Exception) {
                    null
                }
            } else null

            val targetServer = server
                ?: try {
                    ServerRepository.getInstance(applicationContext).selectedServer.value
                } catch (_: Exception) { null }
                ?: V2RayServer(name = "Default Veyro Server", address = "127.0.0.1")

            startVpnInternal(targetServer, isUserTap, connectSource, userActionId)
            return START_NOT_STICKY
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun applyAppFiltering(builder: Builder) {
        val myPackage = packageName
        val file = File(filesDir, "pkgs.json")

        if (!file.exists()) {
            try {
                builder.addDisallowedApplication(myPackage)
            } catch (e: Exception) {
                VeyroLogger.w(TAG, "addDisallowedApplication failed: ${e.message}")
            }
            return
        }

        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val pkg = jsonArray.getString(i)
                if (pkg != myPackage) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: Exception) {
                        VeyroLogger.w(TAG, "addAllowedApplication($pkg) failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            try {
                builder.addDisallowedApplication(myPackage)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun startVpnInternal(server: V2RayServer, isUserTap: Boolean, connectSource: String, userActionId: String) {
        if (!isUserTap) {
            VeyroLogger.w(TAG, "UNEXPECTED_SECOND_CONNECT_BLOCKED source=$connectSource userActionId=$userActionId activeUserActionId=$activeUserActionId")
            VeyroLogger.w(TAG, "Preventing non-user trigger from changing the server.")
            return
        }

        if (currentState == ServiceState.STOPPING) {
            VeyroLogger.w(TAG, "UNEXPECTED_SECOND_CONNECT_BLOCKED source=$connectSource userActionId=$userActionId activeUserActionId=$activeUserActionId")
            VeyroLogger.w(TAG, "Preventing start because service is currently STOPPING.")
            return
        }

        if (currentState == ServiceState.CONNECTING || currentState == ServiceState.CONNECTED) {
            if (activeUserActionId == userActionId) {
                VeyroLogger.i(TAG, "Ignoring duplicate CONNECT_REQUEST for the same userActionId=$userActionId")
                return
            }

            VeyroLogger.w(TAG, "UNEXPECTED_SECOND_CONNECT_BLOCKED source=$connectSource userActionId=$userActionId activeUserActionId=$activeUserActionId")
            VeyroLogger.w(TAG, "Preventing automatic server switch while VPN is active.")
            return
        }

        val generation = globalGenerationCounter.get()
        activeGeneration = generation
        activeUserActionId = userActionId
        currentState = ServiceState.CONNECTING

        try {
            val notification = buildNotification("Veyro VPN Service running")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Failed to start foreground notification: ${e.message}")
        }

        VeyroLogger.i(TAG, "VPN_START_BEGIN generation=$generation")

        // Validate server configuration
        try {
            SingBoxConfigBuilder.validateServer(server)
        } catch (e: IllegalArgumentException) {
            VeyroLogger.e(TAG, "LIBBOX_CONFIG_CHECK_FAILED generation=$generation reason=InvalidServerConfig: ${e.message}")
            performHardDisconnect("InvalidServerConfig")
            return
        }

        currentServer = server
        VpnStateHolder.updateState(VpnState.CONNECTING, server)
        updateNotification("Connecting to ${server.name}...")

        try {
            val configJson = SingBoxConfigBuilder.buildConfig(server)
            VeyroLogger.i(TAG, "Generated sing-box config:\n$configJson")

            val options = SetupOptions()
            val base = File(filesDir, "libbox").apply { mkdirs() }
            val work = File(cacheDir, "libbox").apply { mkdirs() }

            options.setBasePath(base.absolutePath)
            options.setWorkingPath(work.absolutePath)

            Libbox.setup(options)

            val platform = object : PlatformInterface {
                override fun openTun(options: TunOptions): Int {
                    if (vpnInterface != null) {
                        val fd = vpnInterface?.fd ?: -1
                        VeyroLogger.i(TAG, "TUN_ESTABLISHED generation=$generation fd=$fd (reused)")
                        return fd
                    }

                    val builder = Builder()
                        .setSession("Veyro: ${server.name}")
                        .setMtu(1400)
                        .addAddress("172.19.0.1", 30)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("8.8.8.8")
                        .addDnsServer("1.1.1.1")
                        .setBlocking(true)

                    applyAppFiltering(builder)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        builder.setMetered(false)
                    }

                    return try {
                        VeyroLogger.i(TAG, "VPN_ESTABLISH_BEGIN instance=$instanceId generation=$generation caller=PlatformInterface.openTun")
                        vpnInterface = builder.establish()
                        val fd = vpnInterface?.fd ?: -1
                        val pfdId = System.identityHashCode(vpnInterface)
                        VeyroLogger.i(TAG, "VPN_ESTABLISH_SUCCESS fd=$fd instance=$instanceId generation=$generation pfdIdentity=$pfdId")
                        VeyroLogger.i(TAG, "TUN_ESTABLISHED generation=$generation fd=$fd")
                        fd
                    } catch (e: Exception) {
                        VeyroLogger.e(TAG, "Failed to establish VPN in openTun generation=$generation: ${e.message}")
                        -1
                    }
                }

                override fun autoDetectInterfaceControl(fd: Int) {
                    val result = protect(fd)
                    VeyroLogger.d(TAG, "autoDetectInterfaceControl protect($fd) result: $result generation=$generation")
                }

                override fun clearDNSCache() {}
                override fun closeDefaultInterfaceMonitor(l: InterfaceUpdateListener) {}
                override fun findConnectionOwner(
                    protocol: Int,
                    srcAddress: String,
                    srcPort: Int,
                    destAddress: String,
                    destPort: Int,
                ): ConnectionOwner {
                    val owner = ConnectionOwner()
                    owner.setUserId(0)
                    owner.setUserName("veyro")
                    owner.setProcessPath("")
                    return owner
                }

                override fun getInterfaces(): NetworkInterfaceIterator? = null
                override fun includeAllNetworks(): Boolean = false
                override fun localDNSTransport(): LocalDNSTransport? = null
                override fun readWIFIState(): WIFIState? = null
                override fun sendNotification(n: io.nekohasekai.libbox.Notification) {}
                override fun startDefaultInterfaceMonitor(l: InterfaceUpdateListener) {}
                override fun systemCertificates(): StringIterator? = null
                override fun underNetworkExtension(): Boolean = false
                override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
                override fun useProcFS(): Boolean = false
                override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}
                override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
                override fun registerMyInterface(name: String?) {}
            }

            val handler = object : CommandServerHandler {
                override fun serviceReload() {}
                override fun serviceStop() {
                    performHardDisconnect("CommandServerServiceStop")
                }
                override fun getSystemProxyStatus(): SystemProxyStatus? = null
                override fun setSystemProxyEnabled(e: Boolean) {}
                override fun writeDebugMessage(m: String?) {
                    VeyroLogger.d(TAG, "libbox debug generation=$generation: $m")
                }
                override fun triggerNativeCrash() {}
            }

            boxService = Libbox.newCommandServer(handler, platform)
            VeyroLogger.i(TAG, "LIBBOX_COMMAND_SERVER_CREATED generation=$generation")

            boxService?.start()
            VeyroLogger.i(TAG, "LIBBOX_COMMAND_SERVER_STARTED generation=$generation")

            VeyroLogger.i(TAG, "LIBBOX_CONFIG_CHECK_BEGIN generation=$generation")
            boxService?.checkConfig(configJson)
            VeyroLogger.i(TAG, "LIBBOX_CONFIG_CHECK_SUCCESS generation=$generation")

            VeyroLogger.i(TAG, "LIBBOX_SERVICE_START_BEGIN generation=$generation")
            val override = OverrideOptions()
            boxService?.startOrReloadService(configJson, override)
            VeyroLogger.i(TAG, "LIBBOX_SERVICE_START_SUCCESS generation=$generation")

            val clientHandler = object : CommandClientHandler {
                override fun connected() {
                    VeyroLogger.i(TAG, "CommandClient connected generation=$generation")
                }
                override fun disconnected(message: String?) {
                    VeyroLogger.i(TAG, "CommandClient disconnected generation=$generation: $message")
                }
                override fun clearLogs() {}
                override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
                override fun setDefaultLogLevel(level: Int) {}
                override fun updateClashMode(newMode: String?) {}
                override fun writeConnectionEvents(events: ConnectionEvents?) {}
                override fun writeGroups(message: OutboundGroupIterator?) {}
                override fun writeLogs(messageList: LogIterator?) {
                    if (messageList == null) return
                    while (messageList.hasNext()) {
                        val msg = messageList.next().toString()
                        VeyroLogger.d(TAG, "[libbox] $msg")
                        if (msg.contains("outbound/vless")) {
                            if (msg.contains("outbound connection")) {
                                VeyroLogger.i(TAG, "OUTBOUND_CONNECT $msg")
                                VeyroLogger.i(TAG, "OUTBOUND_CONNECTED $msg")
                            } else if (msg.contains("handshake success") || msg.contains("XtlsFilterTls")) {
                                VeyroLogger.i(TAG, "OUTBOUND_HANDSHAKE_SUCCESS $msg")
                                VeyroLogger.i(TAG, "OUTBOUND_FIRST_READ $msg")
                            } else if (msg.contains("error") || msg.contains("failed") || msg.contains("refused")) {
                                VeyroLogger.i(TAG, "OUTBOUND_ERROR $msg")
                            } else if (msg.contains("close") || msg.contains("closed")) {
                                VeyroLogger.i(TAG, "OUTBOUND_CLOSE $msg")
                            }
                        }
                    }
                }
                override fun writeStatus(message: StatusMessage?) {
                    if (message == null || activeGeneration != generation) return
                    val rxTotal = message.downlinkTotal
                    val txTotal = message.uplinkTotal
                    val rxSpeed = message.downlink
                    val txSpeed = message.uplink

                    VeyroLogger.i(TAG, "LIBBOX_STATUS downlink=$rxSpeed uplink=$txSpeed downlinkTotal=$rxTotal uplinkTotal=$txTotal")
                    VeyroLogger.i(TAG, "STATS_BROADCAST up=$txTotal down=$rxTotal upSpeed=$txSpeed downSpeed=$rxSpeed")
                    VeyroLogger.i(TAG, "OUTBOUND_BYTES_UP=$txTotal OUTBOUND_BYTES_DOWN=$rxTotal")

                    VpnStateHolder.updateTunStats(
                        rxPackets = 0,
                        rxBytes = rxTotal,
                        txPackets = 0,
                        txBytes = txTotal
                    )
                }
                override fun writeOutbounds(it: OutboundGroupItemIterator) {}
            }

            val clientOptions = CommandClientOptions()
            clientOptions.addCommand(Libbox.CommandStatus)
            clientOptions.addCommand(Libbox.CommandLog)
            clientOptions.statusInterval = 1000L * 1000L * 1000L // 1 second in ns

            try {
                commandClient = Libbox.newCommandClient(clientHandler, clientOptions)
                commandClient?.connect()
                VeyroLogger.i(TAG, "CommandClient connected successfully generation=$generation")
            } catch (e: Exception) {
                VeyroLogger.e(TAG, "CommandClient connect error generation=$generation: ${e.message}")
            }

            currentState = ServiceState.CONNECTED
            VpnStateHolder.updateState(VpnState.CONNECTED, server)
            updateNotification("Connected to ${server.name}")
            VeyroLogger.i(TAG, "VPN_CONNECTED generation=$generation")

        } catch (e: Exception) {
            VeyroLogger.e(TAG, "Failed to start VPN generation=$generation: ${e.message}", e)
            performHardDisconnect("StartException")
        }
    }

    @Synchronized
    fun performHardDisconnect(reason: String) {
        if (currentState == ServiceState.DISCONNECTED || currentState == ServiceState.STOPPING) {
            return
        }

        val gen = activeGeneration
        activeGeneration = -1L // Invalidate generation
        currentState = ServiceState.STOPPING

        VeyroLogger.i(TAG, "USER_DISCONNECT_REQUEST serviceInstance=$instanceId generation=$gen")
        VeyroLogger.i(TAG, "VPN_STOP_BEGIN serviceInstance=$instanceId generation=$gen reason=$reason")
        VeyroLogger.i(TAG, "VPN_DATA_PLANE_STOP_BEGIN")

        // 1. Close CommandClient
        VeyroLogger.i(TAG, "LIBBOX_DISCONNECT_BEGIN")
        try {
            commandClient?.disconnect()
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Error disconnecting commandClient: ${e.message}")
        }
        commandClient = null
        VeyroLogger.i(TAG, "COMMAND_CLIENT_CLOSED")

        // 2. Shut down sing-box core service via closeService()
        VeyroLogger.i(TAG, "LIBBOX_CLOSE_BEGIN")
        try {
            boxService?.closeService()
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Error in boxService.closeService(): ${e.message}")
        }

        // 3. Close CommandServer
        try {
            boxService?.close()
        } catch (e: Throwable) {
            VeyroLogger.w(TAG, "Error in boxService.close(): ${e.message}")
        }
        boxService = null
        VeyroLogger.i(TAG, "LIBBOX_CLOSE_COMPLETE")
        VeyroLogger.i(TAG, "COMMAND_SERVER_CLOSED")

        // 4. Close Android VPN ParcelFileDescriptor
        val fd = vpnInterface?.fd ?: -1
        val pfdId = System.identityHashCode(vpnInterface)
        VeyroLogger.i(TAG, "VPN_FD_CLOSE_BEGIN fd=$fd instance=$instanceId pfdIdentity=$pfdId")
        VeyroLogger.i(TAG, "TUN_FD_CLOSE_BEGIN fd=$fd")
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Error closing vpnInterface: ${e.message}")
        }
        vpnInterface = null
        VeyroLogger.i(TAG, "TUN_FD_CLOSE_COMPLETE")
        VeyroLogger.i(TAG, "VPN_INTERFACE_NULL=true")

        // 5. Remove Foreground Notification
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Error stopping foreground: ${e.message}")
        }
        VeyroLogger.i(TAG, "FOREGROUND_STOPPED=true")

        currentServer = null

        // 6. Verify System Teardown
        VeyroLogger.i(TAG, "SYSTEM_VPN_TEARDOWN_VERIFY_BEGIN")
        val teardownSuccess = vpnInterface == null && activeInstance == null
        VeyroLogger.i(TAG, "SYSTEM_VPN_TEARDOWN_VERIFY_RESULT=vpnInterface_null=${vpnInterface == null}_activeInstance_null=${activeInstance == null}_success=$teardownSuccess")

        // 7. Stop Service
        try {
            stopSelf()
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Error in stopSelf: ${e.message}")
        }
        VeyroLogger.i(TAG, "STOP_SELF_CALLED=true serviceInstance=$instanceId")

        currentState = ServiceState.DISCONNECTED
        VpnStateHolder.updateState(VpnState.DISCONNECTED)
        VeyroLogger.i(TAG, "VPN_DISCONNECT_COMPLETE")
    }

    override fun onRevoke() {
        VeyroLogger.i(TAG, "VPN_ON_REVOKE instance=$instanceId")
        performHardDisconnect("ANDROID_VPN_REVOKED")
        super.onRevoke()
    }

    override fun onDestroy() {
        VeyroLogger.i(TAG, "ON_DESTROY serviceInstance=$instanceId")
        performHardDisconnect("ServiceDestroyed")
        serviceScope.cancel()
        if (activeInstance == this) {
            activeInstance = null
        }
        VeyroLogger.i(TAG, "ON_DESTROY_COMPLETE serviceInstance=$instanceId")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Veyro VPN Tunnel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows ongoing Veyro VPN connection status"
                    setShowBadge(false)
                }
                val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                VeyroLogger.w(TAG, "Failed to create notification channel: ${e.message}")
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, VeyroVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Veyro VPN")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(0, "Disconnect", pendingDisconnect)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(contentText))
        } catch (e: Exception) {
            VeyroLogger.w(TAG, "Failed to update notification: ${e.message}")
        }
    }
}
