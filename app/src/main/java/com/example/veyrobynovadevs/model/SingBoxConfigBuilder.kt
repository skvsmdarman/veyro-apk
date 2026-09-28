package com.example.veyrobynovadevs.model

import com.example.veyrobynovadevs.vpn.VeyroLogger
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {

    private const val TAG = "SingBoxConfigBuilder"

    enum class CapabilityState {
        SUPPORTED,
        UNSUPPORTED_TRANSPORT,
        INVALID_URI,
        INVALID_REALITY_PARAMETERS
    }

    fun getCapabilityState(server: V2RayServer): CapabilityState {
        if (!server.protocol.equals("vless", ignoreCase = true)) {
            return CapabilityState.INVALID_URI
        }
        if (server.address.isBlank() || server.port !in 1..65535 || server.uuid.isBlank()) {
            return CapabilityState.INVALID_URI
        }

        val isReality = server.security == "reality" || server.tls == "reality" || server.publicKey.isNotEmpty()
        if (isReality && server.publicKey.isBlank()) {
            return CapabilityState.INVALID_REALITY_PARAMETERS
        }

        val supportedTransports = setOf("tcp", "ws", "grpc", "http", "h2", "xhttp")
        if (server.type.isNotEmpty() && !supportedTransports.contains(server.type.lowercase())) {
            return CapabilityState.UNSUPPORTED_TRANSPORT
        }

        return CapabilityState.SUPPORTED
    }

    fun validateServer(server: V2RayServer) {
        require(server.protocol.equals("vless", ignoreCase = true)) {
            "UNSUPPORTED_PROTOCOL: ${server.protocol.ifBlank { "Unknown" }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
        }
        require(server.address.isNotBlank()) {
            "Invalid server configuration: Address cannot be empty"
        }
        require(server.port in 1..65535) {
            "Invalid server configuration: Invalid port ${server.port}"
        }
        require(server.uuid.isNotBlank()) {
            "Invalid server configuration: UUID cannot be empty"
        }

        val isReality = server.security == "reality" || server.tls == "reality" || server.publicKey.isNotEmpty()
        if (isReality) {
            require(server.publicKey.isNotBlank()) {
                "Invalid server configuration: Reality public_key (pbk) is required and cannot be empty for server '${server.name}'"
            }
        }
    }

    fun buildConfig(server: V2RayServer): String {
        validateServer(server)

        val isReality = server.security == "reality" || server.tls == "reality" || server.publicKey.isNotEmpty()
        val isTls = server.security == "tls" || isReality || server.tls == "tls"

        val pbkMasked = if (server.publicKey.isNotEmpty()) "${server.publicKey.take(6)}***" else "none"
        VeyroLogger.i(
            TAG,
            "SERVER_PARSE: type=${server.type} security=${server.security} server=${server.address} port=${server.port} sni=${server.sni} pbk_present=${server.publicKey.isNotEmpty()} pbk=$pbkMasked sid_present=${server.shortId.isNotEmpty()} fp=${server.fingerprint} flow=${server.flow} path=${server.path} host=${server.host} serviceName=${server.serviceName}"
        )

        val supportedFps = setOf("chrome", "firefox", "edge", "safari", "qq", "360", "ios", "android", "random", "randomized")
        val rawFp = server.fingerprint.lowercase().trim()
        val validFp = if (supportedFps.contains(rawFp)) rawFp else "chrome"

        VeyroLogger.i(
            TAG,
            "CONFIG_MAP: reality_enabled=$isReality server_name=${server.sni} public_key_present=${server.publicKey.isNotEmpty()} short_id_present=${server.shortId.isNotEmpty()} utls_fingerprint=$validFp flow=${server.flow}"
        )

        val root = JSONObject()

        // Log config
        val log = JSONObject().apply {
            put("level", "debug")
        }
        root.put("log", log)

        // DNS config (sing-box >= 1.14 format)
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                val remoteDns = JSONObject().apply {
                    put("type", "udp")
                    put("tag", "dns-remote")
                    put("server", "1.1.1.1")
                    put("server_port", 53)
                }
                put(remoteDns)
            }
            put("servers", servers)
            put("final", "dns-remote")
        }
        root.put("dns", dns)

        // Inbounds - TUN
        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "tun0")
            put("address", JSONArray().apply {
                put("172.19.0.1/30")
            })
            put("auto_route", false)
            put("strict_route", true)
            put("stack", "gvisor")
            put("gso", false)
        }
        root.put("inbounds", JSONArray().apply { put(tunInbound) })

        // Outbounds - VLESS
        val vlessOutbound = JSONObject().apply {
            put("type", "vless")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("uuid", server.uuid)

            if (server.flow.isNotEmpty()) {
                put("flow", server.flow)
            }

            val transportType = server.type.lowercase()
            if (transportType == "ws") {
                val transport = JSONObject().apply {
                    put("type", "ws")
                    if (server.path.isNotEmpty()) {
                        put("path", server.path)
                    }
                    if (server.host.isNotEmpty()) {
                        val headers = JSONObject().apply {
                            put("Host", server.host)
                        }
                        put("headers", headers)
                    }
                }
                put("transport", transport)
            } else if (transportType == "grpc") {
                val transport = JSONObject().apply {
                    put("type", "grpc")
                    val serviceName = server.serviceName.ifEmpty { server.path }
                    if (serviceName.isNotEmpty()) {
                        put("service_name", serviceName)
                    }
                    put("idle_timeout", "15s")
                    put("ping_timeout", "15s")
                    put("permit_without_stream", true)
                }
                put("transport", transport)
            } else if (transportType == "http" || transportType == "h2" || transportType == "xhttp") {
                val transport = JSONObject().apply {
                    put("type", if (transportType == "xhttp") "xhttp" else "http")
                    if (server.path.isNotEmpty()) {
                        put("path", server.path)
                    }
                    if (server.host.isNotEmpty()) {
                        put("host", JSONArray().apply { put(server.host) })
                    }
                }
                put("transport", transport)
            }

            val tls = JSONObject().apply {
                put("enabled", isTls)
                if (server.sni.isNotEmpty()) {
                    put("server_name", server.sni)
                }

                val utls = JSONObject().apply {
                    put("enabled", true)
                    put("fingerprint", validFp)
                }
                put("utls", utls)

                if (isReality) {
                    val reality = JSONObject().apply {
                        put("enabled", true)
                        put("public_key", server.publicKey)
                        put("short_id", server.shortId)
                    }
                    put("reality", reality)
                }
            }
            put("tls", tls)

            val multiplex = JSONObject().apply {
                put("enabled", false)
            }
            put("multiplex", multiplex)
        }

        val directOutbound = JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        }

        root.put("outbounds", JSONArray().apply {
            put(vlessOutbound)
            put(directOutbound)
        })

        // Route
        val route = JSONObject().apply {
            put("auto_detect_interface", false)
            put("final", "proxy")
        }
        root.put("route", route)

        // Experimental
        val experimental = JSONObject().apply {
            val cacheFile = JSONObject().apply {
                put("enabled", true)
                put("path", "cache.db")
                put("store_fakeip", true)
            }
            put("cache_file", cacheFile)
        }
        root.put("experimental", experimental)

        return root.toString(2)
    }
}
