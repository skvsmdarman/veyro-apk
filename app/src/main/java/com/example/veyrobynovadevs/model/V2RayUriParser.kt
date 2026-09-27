package com.example.veyrobynovadevs.model

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

object V2RayUriParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun JsonObject.getStr(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
    private fun JsonObject.getObj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.getArr(key: String): JsonArray? = this[key] as? JsonArray

    /**
     * Entry point to parse text content into V2RayServers.
     */
    fun parseText(rawContent: String, isCustomImport: Boolean = false): List<V2RayServer> {
        return parseContent(rawContent, isCustomImport)
    }

    /**
     * Main entry point to parse any content:
     * - Remote/local JSON formatted with "servers" or "proxies" list and "vless", "url", "config", "link", "uri" fields
     * - Full Xray/V2Ray client JSON configs (with outbounds, vnext, streamSettings, realitySettings, etc.)
     * - Simplified JSON server objects or JSON arrays of servers
     * - Multiline URI configurations (vless://, vmess://, trojan://, ss://)
     * - Base64 encoded strings (subscriptions or config dumps)
     */
    fun parseContent(rawContent: String, isCustomImport: Boolean = false): List<V2RayServer> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Try parsing directly as JSON (Object or Array)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonServers = parseJsonContent(trimmed, isCustomImport)
            if (jsonServers.isNotEmpty()) {
                return jsonServers
            }
        }

        // 2. Try URI list (line by line)
        val serversFromLines = parseUriLines(trimmed, isCustomImport)
        if (serversFromLines.isNotEmpty()) {
            return serversFromLines
        }

        // 3. Try JSON parsing if not tried before
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            val jsonServers = parseJsonContent(trimmed, isCustomImport)
            if (jsonServers.isNotEmpty()) {
                return jsonServers
            }
        }

        // 4. Try Base64 decoding
        val decoded = decodeBase64Safe(trimmed)
        if (!decoded.isNullOrEmpty() && decoded != trimmed) {
            val decodedServers = parseContent(decoded, isCustomImport)
            if (decodedServers.isNotEmpty()) {
                return decodedServers
            }
        }

        return emptyList()
    }

    private fun parseUriLines(content: String, isCustomImport: Boolean): List<V2RayServer> {
        val servers = mutableListOf<V2RayServer>()
        val lines = content.lines()
        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isNotEmpty()) {
                val server = parseSingleUri(cleanLine, isCustomImport)
                if (server != null) {
                    servers.add(server)
                }
            }
        }
        return servers
    }

    /**
     * Parses a single URI string (VLESS, VMess, Trojan, SS).
     */
    fun parseSingleUri(uriString: String, isCustomImport: Boolean = false): V2RayServer? {
        val trimmed = uriString.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVlessUri(trimmed, isCustomImport)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmessUri(trimmed, isCustomImport)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojanUri(trimmed, isCustomImport)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocksUri(trimmed, isCustomImport)
            else -> null
        }
    }

    private fun parseVlessUri(uriString: String, isCustomImport: Boolean): V2RayServer? {
        return try {
            val withoutScheme = uriString.substring("vless://".length)
            val hashIndex = withoutScheme.indexOf('#')
            val rawRemark = if (hashIndex != -1) withoutScheme.substring(hashIndex + 1) else ""
            val remark = try { URLDecoder.decode(rawRemark, "UTF-8") } catch (e: Exception) { rawRemark }

            val mainPart = if (hashIndex != -1) withoutScheme.substring(0, hashIndex) else withoutScheme
            val atIndex = mainPart.indexOf('@')
            if (atIndex == -1) return null

            val uuid = mainPart.substring(0, atIndex)
            val rest = mainPart.substring(atIndex + 1)

            val queryIndex = rest.indexOf('?')
            val addressPortStr = if (queryIndex != -1) rest.substring(0, queryIndex) else rest
            val queryString = if (queryIndex != -1) rest.substring(queryIndex + 1) else ""

            val colonIndex = addressPortStr.lastIndexOf(':')
            if (colonIndex == -1) return null

            val address = addressPortStr.substring(0, colonIndex).removeSurrounding("[", "]")
            val port = addressPortStr.substring(colonIndex + 1).toIntOrNull() ?: 443

            val params = parseQueryParams(queryString)

            val type = params["type"] ?: params["net"] ?: "ws"
            val security = params["security"] ?: "tls"
            val path = params["path"] ?: ""
            val host = params["host"] ?: ""
            val sni = params["sni"] ?: params["peer"] ?: host
            val publicKey = params["pbk"] ?: params["pb"] ?: params["publicKey"] ?: ""
            val shortId = params["sid"] ?: params["shortId"] ?: ""
            val flow = params["flow"] ?: ""
            val encryption = params["encryption"] ?: "none"
            val alpn = params["alpn"] ?: ""
            val fingerprint = params["fp"] ?: params["fingerprint"] ?: ""

            V2RayServer(
                id = UUID.nameUUIDFromBytes("vless://$uuid@$address:$port".toByteArray()).toString(),
                name = remark.ifEmpty { "VLESS $address:$port" },
                address = address,
                port = port,
                protocol = "vless",
                uuid = uuid,
                security = security,
                path = path,
                host = host,
                tls = if (security != "none") security else "none",
                sni = sni,
                publicKey = publicKey,
                shortId = shortId,
                type = type,
                flow = flow,
                encryption = encryption,
                alpn = alpn,
                fingerprint = fingerprint,
                isCustom = isCustomImport
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVmessUri(uriString: String, isCustomImport: Boolean): V2RayServer? {
        return try {
            val base64Data = uriString.substring("vmess://".length)
            val jsonString = decodeBase64Safe(base64Data) ?: return null
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject

            val ps = jsonObject.getStr("ps") ?: "VMess Server"
            val add = jsonObject.getStr("add") ?: ""
            val port = jsonObject.getStr("port")?.toIntOrNull() ?: 443
            val id = jsonObject.getStr("id") ?: ""
            val net = jsonObject.getStr("net") ?: "ws"
            val tls = jsonObject.getStr("tls") ?: ""
            val host = jsonObject.getStr("host") ?: ""
            val path = jsonObject.getStr("path") ?: ""
            val sni = jsonObject.getStr("sni") ?: host

            if (add.isEmpty() || id.isEmpty()) return null

            V2RayServer(
                id = UUID.nameUUIDFromBytes("vmess://$id@$add:$port".toByteArray()).toString(),
                name = ps,
                address = add,
                port = port,
                protocol = "vmess",
                uuid = id,
                security = if (tls.isNotEmpty()) tls else "none",
                path = path,
                host = host,
                tls = if (tls.isNotEmpty()) tls else "none",
                sni = sni,
                type = net,
                isCustom = isCustomImport
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseTrojanUri(uriString: String, isCustomImport: Boolean): V2RayServer? {
        return try {
            val uri = URI.create(uriString)
            val password = uri.userInfo ?: ""
            val address = uri.host ?: ""
            val port = if (uri.port != -1) uri.port else 443
            val remark = try { URLDecoder.decode(uri.fragment ?: "", "UTF-8") } catch (e: Exception) { uri.fragment ?: "" }
            val params = parseQueryParams(uri.rawQuery ?: "")

            V2RayServer(
                id = UUID.nameUUIDFromBytes("trojan://$password@$address:$port".toByteArray()).toString(),
                name = remark.ifEmpty { "Trojan $address:$port" },
                address = address,
                port = port,
                protocol = "trojan",
                uuid = password,
                security = params["security"] ?: "tls",
                path = params["path"] ?: "",
                host = params["host"] ?: "",
                tls = "tls",
                sni = params["sni"] ?: params["peer"] ?: "",
                type = params["type"] ?: "tcp",
                isCustom = isCustomImport
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseShadowsocksUri(uriString: String, isCustomImport: Boolean): V2RayServer? {
        return try {
            val withoutScheme = uriString.substring("ss://".length)
            val hashIndex = withoutScheme.indexOf('#')
            val rawRemark = if (hashIndex != -1) withoutScheme.substring(hashIndex + 1) else ""
            val remark = try { URLDecoder.decode(rawRemark, "UTF-8") } catch (e: Exception) { rawRemark }

            val mainPart = if (hashIndex != -1) withoutScheme.substring(0, hashIndex) else withoutScheme
            val atIndex = mainPart.lastIndexOf('@')

            if (atIndex != -1) {
                val userInfoBase64 = mainPart.substring(0, atIndex)
                val serverInfo = mainPart.substring(atIndex + 1)
                val colonIndex = serverInfo.lastIndexOf(':')
                val address = serverInfo.substring(0, colonIndex)
                val port = serverInfo.substring(colonIndex + 1).toIntOrNull() ?: 8388

                V2RayServer(
                    id = UUID.nameUUIDFromBytes("ss://$userInfoBase64@$address:$port".toByteArray()).toString(),
                    name = remark.ifEmpty { "SS $address:$port" },
                    address = address,
                    port = port,
                    protocol = "ss",
                    uuid = userInfoBase64,
                    security = "none",
                    isCustom = isCustomImport
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseJsonContent(content: String, isCustomImport: Boolean): List<V2RayServer> {
        val result = mutableListOf<V2RayServer>()
        try {
            val jsonElement = json.parseToJsonElement(content)

            if (jsonElement is JsonArray) {
                for (item in jsonElement) {
                    if (item is JsonObject) {
                        result.addAll(parseJsonObjectToServers(item, isCustomImport))
                    } else if (item is kotlinx.serialization.json.JsonPrimitive && item.isString) {
                        val server = parseSingleUri(item.content, isCustomImport)
                        if (server != null) result.add(server)
                    }
                }
            } else if (jsonElement is JsonObject) {
                result.addAll(parseJsonObjectToServers(jsonElement, isCustomImport))
            }
        } catch (e: Exception) {
            // Not valid JSON
        }
        return result
    }

    private fun parseJsonObjectToServers(obj: JsonObject, isCustomImport: Boolean): List<V2RayServer> {
        val servers = mutableListOf<V2RayServer>()

        val topRemarks = obj.getStr("remarks")
            ?: obj.getStr("remark")
            ?: obj.getStr("name")
            ?: obj.getStr("ps")

        // 1. Check if obj contains "servers" array or "proxies" array
        val serverListArr = obj.getArr("servers") ?: obj.getArr("proxies")
        if (serverListArr != null && serverListArr.isNotEmpty()) {
            for (item in serverListArr) {
                if (item is JsonObject) {
                    val itemServers = parseSingleServerJsonObject(item, isCustomImport)
                    servers.addAll(itemServers)
                } else if (item is kotlinx.serialization.json.JsonPrimitive && item.isString) {
                    val srv = parseSingleUri(item.content, isCustomImport)
                    if (srv != null) servers.add(srv)
                }
            }
            if (servers.isNotEmpty()) {
                return servers
            }
        }

        // 2. Check if obj itself contains uri/config fields ("vless", "url", "config", "link", "uri")
        val directUriServers = parseUriOrConfigFields(obj, topRemarks, isCustomImport)
        if (directUriServers.isNotEmpty()) {
            return directUriServers
        }

        // 3. Check if full V2Ray / Xray client configuration containing outbounds array or outbound object
        val outboundsArr = obj.getArr("outbounds") ?: obj.getArr("outbound")
        val singleOutboundObj = obj.getObj("outbound")

        val outboundList = mutableListOf<JsonObject>()
        if (outboundsArr != null) {
            for (item in outboundsArr) {
                if (item is JsonObject) {
                    outboundList.add(item)
                }
            }
        } else if (singleOutboundObj != null) {
            outboundList.add(singleOutboundObj)
        }

        if (outboundList.isNotEmpty()) {
            for (outbound in outboundList) {
                val server = parseOutboundObject(outbound, topRemarks, isCustomImport)
                if (server != null) {
                    servers.add(server)
                }
            }
            if (servers.isNotEmpty()) {
                return servers
            }
        }

        // 4. Check if single outbound object itself
        val singleOutboundServer = parseOutboundObject(obj, topRemarks, isCustomImport)
        if (singleOutboundServer != null) {
            return listOf(singleOutboundServer)
        }

        // 5. Fallback: Check if flattened server JSON object (e.g., {"address": "...", "port": 443, ...})
        val flattenedServer = parseFlattenedJsonObject(obj, topRemarks, isCustomImport)
        if (flattenedServer != null) {
            return listOf(flattenedServer)
        }

        return emptyList()
    }

    private fun parseSingleServerJsonObject(obj: JsonObject, isCustomImport: Boolean): List<V2RayServer> {
        val remarks = obj.getStr("name")
            ?: obj.getStr("remarks")
            ?: obj.getStr("remark")
            ?: obj.getStr("ps")

        val uriServers = parseUriOrConfigFields(obj, remarks, isCustomImport)
        if (uriServers.isNotEmpty()) {
            return uriServers
        }

        val outboundServer = parseOutboundObject(obj, remarks, isCustomImport)
        if (outboundServer != null) {
            return listOf(outboundServer)
        }

        val flattenedServer = parseFlattenedJsonObject(obj, remarks, isCustomImport)
        if (flattenedServer != null) {
            return listOf(flattenedServer)
        }

        return emptyList()
    }

    private fun parseUriOrConfigFields(obj: JsonObject, customRemark: String?, isCustomImport: Boolean): List<V2RayServer> {
        val uriKeys = listOf("vless", "url", "config", "link", "uri")
        for (key in uriKeys) {
            val uriVal = obj.getStr(key)
            if (!uriVal.isNullOrBlank()) {
                val parsedUriServers = parseUriLines(uriVal, isCustomImport)
                if (parsedUriServers.isNotEmpty()) {
                    return parsedUriServers.map { srv ->
                        if (!customRemark.isNullOrBlank()) srv.copy(name = customRemark) else srv
                    }
                }
                val parsedSingle = parseSingleUri(uriVal, isCustomImport)
                if (parsedSingle != null) {
                    val finalServer = if (!customRemark.isNullOrBlank()) parsedSingle.copy(name = customRemark) else parsedSingle
                    return listOf(finalServer)
                }
            }
        }
        return emptyList()
    }

    private fun parseOutboundObject(
        outbound: JsonObject,
        topRemarks: String?,
        isCustomImport: Boolean
    ): V2RayServer? {
        val protocol = outbound.getStr("protocol")?.lowercase() ?: return null

        // Ignore non-proxy protocols
        val nonProxyProtocols = setOf("freedom", "blackhole", "dokodemo-door", "dns", "loopback")
        if (nonProxyProtocols.contains(protocol)) return null

        val outboundTag = outbound.getStr("tag")
        val outboundRemark = outbound.getStr("remarks")
            ?: outbound.getStr("remark")
            ?: outbound.getStr("name")
            ?: outboundTag

        val finalRemark = if (!topRemarks.isNullOrBlank()) topRemarks else if (!outboundRemark.isNullOrBlank()) outboundRemark else null

        val settings = outbound.getObj("settings") ?: outbound

        var address = ""
        var port = 443
        var uuid = ""
        var flow = ""
        var encryption = "none"

        if (protocol == "vless" || protocol == "vmess") {
            val vnext = settings.getArr("vnext")
            if (vnext == null || vnext.isEmpty()) return null

            val vnextObj = vnext[0] as? JsonObject ?: return null
            address = vnextObj.getStr("address") ?: vnextObj.getStr("add") ?: ""
            port = vnextObj.getStr("port")?.toIntOrNull()
                ?: (vnextObj["port"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                ?: 443

            val users = vnextObj.getArr("users")
            if (users != null && users.isNotEmpty()) {
                val userObj = users[0] as? JsonObject
                if (userObj != null) {
                    uuid = userObj.getStr("id") ?: userObj.getStr("uuid") ?: ""
                    flow = userObj.getStr("flow") ?: ""
                    encryption = userObj.getStr("encryption") ?: userObj.getStr("security") ?: "none"
                }
            }
        } else if (protocol == "trojan" || protocol == "shadowsocks" || protocol == "ss") {
            val servers = settings.getArr("servers")
            if (servers == null || servers.isEmpty()) return null

            val srvObj = servers[0] as? JsonObject ?: return null
            address = srvObj.getStr("address") ?: srvObj.getStr("add") ?: ""
            port = srvObj.getStr("port")?.toIntOrNull()
                ?: (srvObj["port"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                ?: 443
            uuid = srvObj.getStr("password") ?: srvObj.getStr("id") ?: ""
            encryption = srvObj.getStr("method") ?: "none"
        } else {
            return null
        }

        if (address.isEmpty()) return null

        val streamSettings = outbound.getObj("streamSettings")
        val network = streamSettings?.getStr("network")
            ?: streamSettings?.getStr("net")
            ?: streamSettings?.getStr("type")
            ?: "tcp"
        val security = streamSettings?.getStr("security") ?: "none"

        // realitySettings
        val realitySettings = streamSettings?.getObj("realitySettings")
            ?: streamSettings?.getObj("realitysettings")
        val realitySni = realitySettings?.getStr("serverName")
            ?: realitySettings?.getStr("sni") ?: ""
        val publicKey = realitySettings?.getStr("publicKey")
            ?: realitySettings?.getStr("pb") ?: ""
        val shortId = realitySettings?.getStr("shortId")
            ?: realitySettings?.getStr("sid") ?: ""

        // tlsSettings
        val tlsSettings = streamSettings?.getObj("tlsSettings")
            ?: streamSettings?.getObj("tlssettings")
        val tlsSni = tlsSettings?.getStr("serverName")
            ?: tlsSettings?.getStr("sni") ?: ""

        val rawServerName = streamSettings?.getStr("serverName") ?: ""
        val sni = if (realitySni.isNotEmpty()) realitySni else if (tlsSni.isNotEmpty()) tlsSni else rawServerName

        // wsSettings
        val wsSettings = streamSettings?.getObj("wsSettings")
            ?: streamSettings?.getObj("wssettings")
        val wsPath = wsSettings?.getStr("path") ?: ""
        val wsHeaders = wsSettings?.getObj("headers")
        val wsHost = wsHeaders?.getStr("Host") ?: wsHeaders?.getStr("host") ?: ""

        // grpcSettings
        val grpcSettings = streamSettings?.getObj("grpcSettings")
            ?: streamSettings?.getObj("grpcsettings")
        val grpcPath = grpcSettings?.getStr("serviceName") ?: grpcSettings?.getStr("path") ?: ""
        val grpcHost = grpcSettings?.getStr("authority") ?: ""

        // httpSettings / h2Settings
        val httpSettings = streamSettings?.getObj("httpSettings")
            ?: streamSettings?.getObj("httpsettings")
            ?: streamSettings?.getObj("h2Settings")
        val httpPath = httpSettings?.getStr("path") ?: ""

        val path = if (wsPath.isNotEmpty()) wsPath else if (grpcPath.isNotEmpty()) grpcPath else httpPath
        val host = if (wsHost.isNotEmpty()) wsHost else if (grpcHost.isNotEmpty()) grpcHost else sni

        val name = finalRemark ?: "$protocol $address:$port"
        val serverId = UUID.nameUUIDFromBytes("$protocol://$uuid@$address:$port".toByteArray()).toString()

        return V2RayServer(
            id = serverId,
            name = name,
            address = address,
            port = port,
            protocol = protocol,
            uuid = uuid,
            security = security,
            path = path,
            host = host,
            tls = if (security != "none") security else "none",
            sni = sni,
            publicKey = publicKey,
            shortId = shortId,
            type = network,
            flow = flow,
            encryption = encryption,
            isCustom = isCustomImport
        )
    }

    private fun parseFlattenedJsonObject(
        obj: JsonObject,
        topRemarks: String?,
        isCustomImport: Boolean
    ): V2RayServer? {
        val address = obj.getStr("address")
            ?: obj.getStr("add")
            ?: obj.getStr("host")
            ?: return null

        val port = obj.getStr("port")?.toIntOrNull()
            ?: (obj["port"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            ?: 443

        val name = topRemarks
            ?: obj.getStr("name")
            ?: obj.getStr("ps")
            ?: obj.getStr("remark")
            ?: obj.getStr("remarks")
            ?: "Server $address"

        val protocol = obj.getStr("protocol") ?: "vless"
        val uuid = obj.getStr("uuid")
            ?: obj.getStr("id")
            ?: obj.getStr("password")
            ?: ""

        val security = obj.getStr("security") ?: "tls"
        val path = obj.getStr("path") ?: ""
        val host = obj.getStr("host") ?: ""
        val tls = obj.getStr("tls") ?: "tls"
        val sni = obj.getStr("sni") ?: host
        val publicKey = obj.getStr("publicKey") ?: obj.getStr("pb") ?: ""
        val shortId = obj.getStr("shortId") ?: obj.getStr("sid") ?: ""
        val type = obj.getStr("type") ?: obj.getStr("net") ?: "ws"
        val flow = obj.getStr("flow") ?: ""
        val encryption = obj.getStr("encryption") ?: "none"

        return V2RayServer(
            id = obj.getStr("id") ?: UUID.nameUUIDFromBytes("$protocol://$uuid@$address:$port".toByteArray()).toString(),
            name = name,
            address = address,
            port = port,
            protocol = protocol,
            uuid = uuid,
            security = security,
            path = path,
            host = host,
            tls = tls,
            sni = sni,
            publicKey = publicKey,
            shortId = shortId,
            type = type,
            flow = flow,
            encryption = encryption,
            isCustom = isCustomImport
        )
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isEmpty()) return map
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                val decodedVal = try { URLDecoder.decode(value, "UTF-8") } catch (e: Exception) { value }
                map[key] = decodedVal
            }
        }
        return map
    }

    private fun decodeBase64Safe(input: String): String? {
        val clean = input.trim().replace("\r", "").replace("\n", "").replace(" ", "")
        if (clean.length < 4) return null
        return try {
            val bytes = Base64.decode(
                clean,
                Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP
            )
            String(bytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            try {
                val decoderClass = Class.forName("java.util.Base64")
                val getDecoder = decoderClass.getMethod("getDecoder")
                val decoder = getDecoder.invoke(null)
                val decodeMethod = decoderClass.declaredClasses.first { it.simpleName == "Decoder" }.getMethod("decode", String::class.java)
                val bytes = decodeMethod.invoke(decoder, clean) as ByteArray
                String(bytes, Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
