package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.model.V2RayUriParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RayUriParserTest {

    @Test
    fun testParseVlessUri() {
        val vlessUri = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@192.168.1.1:443?type=ws&security=tls&path=%2Fvless&host=example.com&sni=example.com&flow=xtls-rprx-vision&pb=ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk&sid=0990d22f#US%20Fast%20Server"
        val server = V2RayUriParser.parseSingleUri(vlessUri, isCustomImport = true)

        assertNotNull(server)
        assertEquals("vless", server?.protocol)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", server?.uuid)
        assertEquals("192.168.1.1", server?.address)
        assertEquals(443, server?.port)
        assertEquals("ws", server?.type)
        assertEquals("tls", server?.security)
        assertEquals("/vless", server?.path)
        assertEquals("example.com", server?.host)
        assertEquals("example.com", server?.sni)
        assertEquals("xtls-rprx-vision", server?.flow)
        assertEquals("ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk", server?.publicKey)
        assertEquals("0990d22f", server?.shortId)
        assertEquals("US Fast Server", server?.name)
        assertTrue(server?.isCustom == true)
    }

    @Test
    fun testParseFullXrayJsonConfig() {
        val jsonConfig = """
            {
              "remarks": "By EbraSha 🦇",
              "log": {
                "loglevel": "warning"
              },
              "inbounds": [
                {
                  "port": 10808,
                  "protocol": "socks"
                }
              ],
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "167.17.69.171",
                        "port": 443,
                        "users": [
                          {
                            "id": "00144519-7e0d-476b-8d28-435576af9262",
                            "flow": "xtls-rprx-vision",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "tcp",
                    "security": "reality",
                    "realitySettings": {
                      "serverName": "www.cloudflare.com",
                      "publicKey": "ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk",
                      "shortId": "0990d22f",
                      "fingerprint": "chrome"
                    }
                  }
                },
                {
                  "protocol": "freedom",
                  "tag": "direct"
                }
              ]
            }
        """.trimIndent()

        val servers = V2RayUriParser.parseContent(jsonConfig, isCustomImport = true)

        assertEquals(1, servers.size)
        val server = servers[0]
        assertEquals("By EbraSha 🦇", server.name)
        assertEquals("167.17.69.171", server.address)
        assertEquals(443, server.port)
        assertEquals("vless", server.protocol)
        assertEquals("00144519-7e0d-476b-8d28-435576af9262", server.uuid)
        assertEquals("xtls-rprx-vision", server.flow)
        assertEquals("none", server.encryption)
        assertEquals("tcp", server.type)
        assertEquals("reality", server.security)
        assertEquals("www.cloudflare.com", server.sni)
        assertEquals("ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk", server.publicKey)
        assertEquals("0990d22f", server.shortId)
        assertTrue(server.isCustom)
    }

    @Test
    fun testParseJsonArray() {
        val jsonArrayStr = """
            [
                {
                    "id": "srv-1",
                    "name": "Server One",
                    "address": "1.1.1.1",
                    "port": 8443,
                    "protocol": "vless",
                    "uuid": "uuid-1",
                    "security": "tls",
                    "type": "grpc"
                },
                {
                    "id": "srv-2",
                    "name": "Server Two",
                    "address": "2.2.2.2",
                    "port": 443,
                    "protocol": "vless",
                    "uuid": "uuid-2",
                    "security": "reality",
                    "type": "ws"
                }
            ]
        """.trimIndent()

        val servers = V2RayUriParser.parseContent(jsonArrayStr, isCustomImport = false)

        assertEquals(2, servers.size)
        assertEquals("Server One", servers[0].name)
        assertEquals("1.1.1.1", servers[0].address)
        assertEquals(8443, servers[0].port)
        assertEquals("grpc", servers[0].type)

        assertEquals("Server Two", servers[1].name)
        assertEquals("2.2.2.2", servers[1].address)
        assertEquals("reality", servers[1].security)
    }

    @Test
    fun testParseMultiLineUris() {
        val content = """
            vless://uuid-101@10.0.0.1:443?type=ws&security=tls#Server%20A
            vless://uuid-102@10.0.0.2:8080?type=tcp&security=none#Server%20B
        """.trimIndent()

        val servers = V2RayUriParser.parseContent(content, isCustomImport = true)

        assertEquals(2, servers.size)
        assertEquals("Server A", servers[0].name)
        assertEquals("10.0.0.1", servers[0].address)

        assertEquals("Server B", servers[1].name)
        assertEquals("10.0.0.2", servers[1].address)
        assertEquals(8080, servers[1].port)
    }

    @Test
    fun testParseServersJsonFormat() {
        val json = """
            {
              "servers": [
                {
                  "name": "Server 1",
                  "vless": "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@192.168.1.1:443?type=ws&security=tls#Old%20Name"
                },
                {
                  "name": "Server 2",
                  "vless": "vless://b2c3d4e5-f6a7-8901-bcde-f12345678901@192.168.1.2:8080?type=tcp&security=none"
                }
              ]
            }
        """.trimIndent()

        val servers = V2RayUriParser.parseText(json, isCustomImport = true)

        assertEquals(2, servers.size)
        assertEquals("Server 1", servers[0].name)
        assertEquals("192.168.1.1", servers[0].address)
        assertEquals(443, servers[0].port)
        assertEquals("vless", servers[0].protocol)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", servers[0].uuid)

        assertEquals("Server 2", servers[1].name)
        assertEquals("192.168.1.2", servers[1].address)
        assertEquals(8080, servers[1].port)
        assertEquals("vless", servers[1].protocol)
    }

    @Test
    fun testParseProxiesWithUrlAndConfigFields() {
        val json = """
            {
              "proxies": [
                {
                  "name": "Custom Proxy 1",
                  "url": "vless://c3d4e5f6-7890-1234-5678-90abcdef1234@10.0.0.1:443?type=grpc&security=reality&pb=testpb&sid=testsid"
                },
                {
                  "name": "Custom Proxy 2",
                  "config": "vless://d4e5f6a7-8901-2345-6789-0abcdef12345@10.0.0.2:8443?type=ws&security=tls"
                }
              ]
            }
        """.trimIndent()

        val servers = V2RayUriParser.parseText(json, isCustomImport = true)

        assertEquals(2, servers.size)
        assertEquals("Custom Proxy 1", servers[0].name)
        assertEquals("10.0.0.1", servers[0].address)
        assertEquals("grpc", servers[0].type)
        assertEquals("reality", servers[0].security)

        assertEquals("Custom Proxy 2", servers[1].name)
        assertEquals("10.0.0.2", servers[1].address)
        assertEquals(8443, servers[1].port)
    }
}
