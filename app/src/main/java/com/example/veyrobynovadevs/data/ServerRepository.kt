package com.example.veyrobynovadevs.data

import android.content.Context
import android.content.SharedPreferences
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.model.V2RayUriParser
import com.example.veyrobynovadevs.ping.PingTester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ServerRepository private constructor(private val context: Context) {

    companion object {
        const val DEFAULT_SERVERS_URL = "https://skvsmdarman.github.io/vless-servers/servers.json"
        private const val PREFS_NAME = "veyro_server_prefs"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        private const val SERVERS_FILE_NAME = "cached_servers.json"

        val BUILTIN_DEFAULT_SERVERS = listOf(
            V2RayServer(
                id = "default_ebrasha_01",
                name = "By EbraSha 🦇",
                address = "167.17.69.171",
                port = 443,
                protocol = "vless",
                uuid = "00144519-7e0d-476b-8d28-435576af9262",
                flow = "xtls-rprx-vision",
                security = "reality",
                sni = "www.cloudflare.com",
                publicKey = "ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk",
                shortId = "0990d22f",
                type = "tcp",
                tls = "reality",
                isCustom = false
            )
        )

        @Volatile
        private var INSTANCE: ServerRepository? = null

        fun getInstance(context: Context): ServerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val _servers = MutableStateFlow<List<V2RayServer>>(emptyList())
    val servers: StateFlow<List<V2RayServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<V2RayServer?>(null)
    val selectedServer: StateFlow<V2RayServer?> = _selectedServer.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initializeAndFetchServers()
    }

    private fun initializeAndFetchServers() {
        repositoryScope.launch {
            val hasCachedFile = loadCachedServersInternal()
            if (!hasCachedFile) {
                fetchRemoteServersInternal()
            }
        }
    }

    private suspend fun loadCachedServersInternal(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, SERVERS_FILE_NAME)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotEmpty()) {
                    val loadedServers = V2RayUriParser.parseContent(content, isCustomImport = false)
                    val validServers = loadedServers.filter { s ->
                        val isReality = s.security == "reality" || s.tls == "reality"
                        !isReality || s.publicKey.isNotBlank()
                    }
                    val finalServers = if (validServers.isNotEmpty()) validServers else BUILTIN_DEFAULT_SERVERS
                    _servers.value = finalServers

                    val savedSelectedId = prefs.getString(KEY_SELECTED_SERVER_ID, null)
                    val selected = finalServers.find { it.id == savedSelectedId && (it.security != "reality" || it.publicKey.isNotBlank()) }
                        ?: finalServers.first()
                    _selectedServer.value = selected
                    return@withContext true
                }
            }

            // Fallback to builtin default servers if no cache exists
            val defaultList = BUILTIN_DEFAULT_SERVERS
            _servers.value = defaultList
            val selected = defaultList.first()
            _selectedServer.value = selected
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            _servers.value = BUILTIN_DEFAULT_SERVERS
            _selectedServer.value = BUILTIN_DEFAULT_SERVERS.first()
            return@withContext false
        }
    }

    private suspend fun saveServersLocally(serverList: List<V2RayServer>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, SERVERS_FILE_NAME)
            val jsonContent = json.encodeToString(serverList)
            file.writeText(jsonContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Fetches default servers from remote URL: https://skvsmdarman.github.io/vless-servers/servers.json
     */
    suspend fun fetchRemoteServers(url: String = DEFAULT_SERVERS_URL): Result<List<V2RayServer>> = withContext(Dispatchers.IO) {
        fetchRemoteServersInternal(url)
    }

    private suspend fun fetchRemoteServersInternal(url: String = DEFAULT_SERVERS_URL): Result<List<V2RayServer>> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VeyroVPN/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Failed to fetch servers: HTTP ${response.code}"
                handleRemoteFetchFailure(err)
                return@withContext Result.failure(Exception(err))
            }

            val bodyString = response.body?.string() ?: ""
            val newServers = V2RayUriParser.parseContent(bodyString, isCustomImport = false)

            if (newServers.isEmpty()) {
                val err = "No valid servers found at $url"
                handleRemoteFetchFailure(err)
                return@withContext Result.failure(Exception(err))
            }

            // Merge with existing custom servers and built-in defaults
            val currentCustomServers = _servers.value.filter { it.isCustom }
            val mergedList = mergeServers(BUILTIN_DEFAULT_SERVERS, mergeServers(newServers, currentCustomServers))

            _servers.value = mergedList
            saveServersLocally(mergedList)

            // Ensure selected server is non-null
            if (_selectedServer.value == null || mergedList.none { it.id == _selectedServer.value?.id }) {
                selectServer(mergedList.first())
            }

            _isLoading.value = false
            Result.success(mergedList)
        } catch (e: Exception) {
            e.printStackTrace()
            val err = "Error fetching servers: ${e.localizedMessage}"
            handleRemoteFetchFailure(err)
            Result.failure(e)
        }
    }

    private suspend fun handleRemoteFetchFailure(errorMessage: String) {
        _errorMessage.value = errorMessage
        _isLoading.value = false

        // Ensure state never remains empty
        if (_servers.value.isEmpty()) {
            val fallback = BUILTIN_DEFAULT_SERVERS
            _servers.value = fallback
            saveServersLocally(fallback)
            selectServer(fallback.first())
        } else if (_selectedServer.value == null) {
            selectServer(_servers.value.first())
        }
    }

    /**
     * Import servers from a custom URL.
     */
    suspend fun importFromUrl(customUrl: String): Result<List<V2RayServer>> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val request = Request.Builder()
                .url(customUrl)
                .header("User-Agent", "VeyroVPN/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Failed to import from URL: HTTP ${response.code}"
                _errorMessage.value = err
                _isLoading.value = false
                return@withContext Result.failure(Exception(err))
            }

            val bodyString = response.body?.string() ?: ""
            val importedServers = V2RayUriParser.parseContent(bodyString, isCustomImport = true)

            if (importedServers.isEmpty()) {
                val err = "No valid V2Ray/VLESS configs found at URL"
                _errorMessage.value = err
                _isLoading.value = false
                return@withContext Result.failure(Exception(err))
            }

            val mergedList = mergeServers(_servers.value, importedServers)
            _servers.value = mergedList
            saveServersLocally(mergedList)

            if (importedServers.isNotEmpty()) {
                selectServer(importedServers.first())
            }

            _isLoading.value = false
            Result.success(importedServers)
        } catch (e: Exception) {
            val err = "Import URL error: ${e.localizedMessage}"
            _errorMessage.value = err
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Import servers from direct JSON string content.
     */
    suspend fun importFromJson(jsonContent: String, isCustom: Boolean = true): Result<List<V2RayServer>> {
        return importFromText(jsonContent, isCustom = isCustom)
    }

    /**
     * Import servers from direct text content (clipboard or local file content).
     */
    suspend fun importFromText(text: String, isCustom: Boolean = true): Result<List<V2RayServer>> = withContext(Dispatchers.IO) {
        try {
            val imported = V2RayUriParser.parseContent(text, isCustomImport = isCustom)
            if (imported.isEmpty()) {
                val err = "Could not find any valid V2Ray/Xray configurations"
                _errorMessage.value = err
                return@withContext Result.failure(Exception(err))
            }

            val mergedList = mergeServers(_servers.value, imported)
            _servers.value = mergedList
            saveServersLocally(mergedList)

            // Automatically select the first newly imported server
            selectServer(imported.first())

            Result.success(imported)
        } catch (e: Exception) {
            val err = "Import error: ${e.localizedMessage}"
            _errorMessage.value = err
            Result.failure(e)
        }
    }

    fun selectServer(server: V2RayServer) {
        _selectedServer.value = server
        prefs.edit().putString(KEY_SELECTED_SERVER_ID, server.id).apply()
    }

    fun selectServerById(id: String) {
        val target = _servers.value.find { it.id == id }
        if (target != null) {
            selectServer(target)
        }
    }

    suspend fun deleteServer(serverId: String) = withContext(Dispatchers.IO) {
        val updated = _servers.value.filterNot { it.id == serverId }
        val finalServers = updated.ifEmpty { BUILTIN_DEFAULT_SERVERS }

        _servers.value = finalServers
        saveServersLocally(finalServers)

        if (_selectedServer.value?.id == serverId || _selectedServer.value == null) {
            selectServer(finalServers.first())
        }
    }

    /**
     * Deletes all dead/unreachable servers at once.
     */
    suspend fun deleteDeadServers() = withContext(Dispatchers.IO) {
        val currentList = _servers.value
        val liveServers = currentList.filter { it.isOnline || it.lastTestedAt == 0L }
        val finalServers = liveServers.ifEmpty { BUILTIN_DEFAULT_SERVERS }

        _servers.value = finalServers
        saveServersLocally(finalServers)

        if (_selectedServer.value == null || finalServers.none { it.id == _selectedServer.value?.id }) {
            selectServer(finalServers.first())
        }
    }

    /**
     * Updates a single server in the list and preserves state locally.
     */
    suspend fun updateServerInList(updatedServer: V2RayServer) = withContext(Dispatchers.IO) {
        val currentList = _servers.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedServer.id }
        if (index != -1) {
            currentList[index] = updatedServer
            _servers.value = currentList
            saveServersLocally(currentList)
            if (_selectedServer.value?.id == updatedServer.id) {
                _selectedServer.value = updatedServer
            }
        }
    }

    /**
     * Run ping test on all servers and sort them by lowest ping.
     */
    suspend fun testAndSortServers(onProgress: ((completed: Int, total: Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val currentList = _servers.value
        if (currentList.isEmpty()) return@withContext

        _isLoading.value = true
        val testedList = PingTester.testAllServersPing(currentList) { completed, total, _ ->
            onProgress?.invoke(completed, total)
        }

        val sortedList = PingTester.sortByLowestPing(testedList)
        _servers.value = sortedList
        saveServersLocally(sortedList)

        // Keep selected server updated with latest latency
        val currentSelectedId = _selectedServer.value?.id
        if (currentSelectedId != null) {
            val updatedSelected = sortedList.find { it.id == currentSelectedId }
            if (updatedSelected != null) {
                _selectedServer.value = updatedSelected
            }
        }

        _isLoading.value = false
    }

    private fun mergeServers(existing: List<V2RayServer>, newServers: List<V2RayServer>): List<V2RayServer> {
        val map = LinkedHashMap<String, V2RayServer>()
        for (server in existing) {
            map[server.id] = server
        }
        for (server in newServers) {
            map[server.id] = server
        }
        return map.values.toList()
    }
}
