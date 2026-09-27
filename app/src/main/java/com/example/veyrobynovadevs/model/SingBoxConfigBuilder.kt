package com.example.veyrobynovadevs.model

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {

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

        val isReality = server.security == "reality" || server.tls == "reality"
        if (isReality || (server.security.isEmpty() && server.publicKey.isNotEmpty())) {
            require(server.publicKey.isNotBlank()) {
                "Invalid server configuration: Reality public_key is required and cannot be empty for server '${server.name}'"
            }
        }
    }

    fun buildConfig(server: V2RayServer): String {
        validateServer(server)

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

        // Outbounds - VLESS + Reality
        val vlessOutbound = JSONObject().apply {
            put("type", "vless")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("uuid", server.uuid)

            if (server.flow.isNotEmpty()) {
                put("flow", server.flow)
            }

            if (server.type == "ws" || server.type == "grpc" || server.type == "http") {
                val transport = JSONObject().apply {
                    put("type", server.type)
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
            }

            val tls = JSONObject().apply {
                val isReality = server.security == "reality" || server.tls == "reality" || server.publicKey.isNotEmpty()
                val isTls = server.security == "tls" || isReality || server.tls == "tls"

                put("enabled", isTls)
                if (server.sni.isNotEmpty()) {
                    put("server_name", server.sni)
                }

                val utls = JSONObject().apply {
                    put("enabled", true)
                    val fp = server.fingerprint.ifEmpty { "chrome" }
                    val validFp = if (fp == "unsafe" || fp.isBlank()) "chrome" else fp
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
