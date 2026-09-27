package com.example.veyrobynovadevs.model

import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.util.UUID

@Serializable
data class V2RayServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Veyro Server",
    val address: String = "",
    val port: Int = 443,
    val protocol: String = "vless", // vless, vmess, trojan, ss
    val uuid: String = "",
    val security: String = "tls", // tls, reality, none, auto
    val path: String = "",
    val host: String = "",
    val tls: String = "tls", // tls, xtls, reality, none
    val sni: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val type: String = "ws", // ws, grpc, tcp, http
    val flow: String = "",
    val encryption: String = "none",
    val alpn: String = "",
    val fingerprint: String = "",
    val latencyMs: Long = -1L,
    val isOnline: Boolean = latencyMs in 0..9999,
    val lastTestedAt: Long = 0L,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toVlessUri(): String {
        val queryParams = mutableListOf<String>()
        if (type.isNotEmpty()) queryParams.add("type=$type")
        if (security.isNotEmpty()) queryParams.add("security=$security")
        if (path.isNotEmpty()) queryParams.add("path=${java.net.URLEncoder.encode(path, "UTF-8")}")
        if (host.isNotEmpty()) queryParams.add("host=${java.net.URLEncoder.encode(host, "UTF-8")}")
        if (sni.isNotEmpty()) queryParams.add("sni=${java.net.URLEncoder.encode(sni, "UTF-8")}")
        if (publicKey.isNotEmpty()) queryParams.add("pb=${URLEncoder.encode(publicKey, "UTF-8")}")
        if (shortId.isNotEmpty()) queryParams.add("sid=${URLEncoder.encode(shortId, "UTF-8")}")
        if (flow.isNotEmpty()) queryParams.add("flow=$flow")
        if (alpn.isNotEmpty()) queryParams.add("alpn=${URLEncoder.encode(alpn, "UTF-8")}")
        if (fingerprint.isNotEmpty()) queryParams.add("fp=${URLEncoder.encode(fingerprint, "UTF-8")}")
        if (encryption.isNotEmpty() && encryption != "none") queryParams.add("encryption=$encryption")

        val queryString = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
        val remark = java.net.URLEncoder.encode(name, "UTF-8")

        return "$protocol://$uuid@$address:$port$queryString#$remark"
    }

    val displayLatency: String
        get() = when {
            isOnline && latencyMs in 0..9999 -> "${latencyMs} ms"
            else -> "DEAD / UNREACHABLE"
        }
}
