package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.model.SingBoxConfigBuilder
import com.example.veyrobynovadevs.model.V2RayServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SingBoxConfigBuilderTest {

    @Test
    fun testBuildConfig_ValidRealityServer_Succeeds() {
        val server = V2RayServer(
            id = "server-1",
            name = "My Reality Server",
            address = "123.45.67.89",
            port = 443,
            protocol = "vless",
            uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            security = "reality",
            sni = "example.com",
            publicKey = "valid_public_key_12345",
            shortId = "12345678",
            fingerprint = "chrome"
        )

        val configStr = SingBoxConfigBuilder.buildConfig(server)
        assertNotNull(configStr)

        // Ensure config does NOT contain legacy DNS format 'udp://1.1.1.1'
        assertFalse("Config must NOT contain legacy DNS format 'udp://1.1.1.1'", configStr.contains("udp://1.1.1.1"))

        val root = JSONObject(configStr)
        assertTrue(root.has("dns"))
        assertTrue(root.has("inbounds"))
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("route"))

        // Test DNS format (sing-box >= 1.14)
        val dns = root.getJSONObject("dns")
        assertEquals("dns-remote", dns.getString("final"))

        val dnsServers = dns.getJSONArray("servers")
        val remoteServer = dnsServers.getJSONObject(0)

        // Verify modern DNS fields
        assertEquals("udp", remoteServer.getString("type"))
        assertEquals("dns-remote", remoteServer.getString("tag"))
        assertEquals("1.1.1.1", remoteServer.getString("server"))
        assertEquals(53, remoteServer.getInt("server_port"))

        // Verify legacy 'address' field is NOT present in DNS server object
        assertFalse("DNS server object must NOT contain legacy 'address' field", remoteServer.has("address"))

        // Test Outbounds
        val outbounds = root.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)

        assertEquals("vless", proxy.getString("type"))
        assertEquals("123.45.67.89", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", proxy.getString("uuid"))

        val tls = proxy.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("example.com", tls.getString("server_name"))

        val reality = tls.getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals("valid_public_key_12345", reality.getString("public_key"))
        assertEquals("12345678", reality.getString("short_id"))
    }

    @Test
    fun testBuildConfig_EmptyRealityPublicKey_ThrowsIllegalArgumentException() {
        val server = V2RayServer(
            id = "server-invalid-reality",
            name = "Invalid Reality Server",
            address = "123.45.67.89",
            port = 443,
            protocol = "vless",
            uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            security = "reality",
            sni = "example.com",
            publicKey = "", // Empty public key
            shortId = "12345678"
        )

        try {
            SingBoxConfigBuilder.buildConfig(server)
            fail("buildConfig must throw IllegalArgumentException when Reality public_key is empty")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message must mention public_key error, got: ${e.message}",
                e.message?.contains("public_key") == true
            )
        }
    }

    @Test
    fun testBuildConfig_TrojanProtocol_ThrowsIllegalArgumentException() {
        val server = V2RayServer(
            id = "server-trojan",
            name = "Trojan Server",
            address = "123.45.67.89",
            port = 443,
            protocol = "trojan",
            uuid = "password123"
        )

        try {
            SingBoxConfigBuilder.buildConfig(server)
            fail("buildConfig must throw IllegalArgumentException for Trojan protocol")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message must mention UNSUPPORTED_PROTOCOL: Trojan, got: ${e.message}",
                e.message?.contains("UNSUPPORTED_PROTOCOL: Trojan") == true
            )
        }
    }
}
