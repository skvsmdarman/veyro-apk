package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.model.SingBoxConfigBuilder
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.model.V2RayUriParser
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
            fingerprint = "firefox",
            flow = "xtls-rprx-vision"
        )

        val configStr = SingBoxConfigBuilder.buildConfig(server)
        assertNotNull(configStr)

        val root = JSONObject(configStr)
        val outbounds = root.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)

        assertEquals("vless", proxy.getString("type"))
        assertEquals("123.45.67.89", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", proxy.getString("uuid"))
        assertEquals("xtls-rprx-vision", proxy.getString("flow"))

        val tls = proxy.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("example.com", tls.getString("server_name"))

        val utls = tls.getJSONObject("utls")
        assertEquals("firefox", utls.getString("fingerprint"))

        val reality = tls.getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals("valid_public_key_12345", reality.getString("public_key"))
        assertEquals("12345678", reality.getString("short_id"))
    }

    @Test
    fun testVlessUriParsing_RealityTcpVision_PreservesAllParameters() {
        val uri = "vless://00144519-7e0d-476b-8d28-435576af9262@167.17.69.171:443?type=tcp&security=reality&pbk=ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk&sid=0990d22f&sni=www.cloudflare.com&fp=chrome&flow=xtls-rprx-vision#Server%201%20Iran%20Special%20%5BDE%5D"

        val server = V2RayUriParser.parseSingleUri(uri)
        assertNotNull(server)
        assertEquals("167.17.69.171", server!!.address)
        assertEquals(443, server.port)
        assertEquals("00144519-7e0d-476b-8d28-435576af9262", server.uuid)
        assertEquals("reality", server.security)
        assertEquals("ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk", server.publicKey)
        assertEquals("0990d22f", server.shortId)
        assertEquals("www.cloudflare.com", server.sni)
        assertEquals("chrome", server.fingerprint)
        assertEquals("xtls-rprx-vision", server.flow)

        val configStr = SingBoxConfigBuilder.buildConfig(server)
        val root = JSONObject(configStr)
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)

        assertEquals("167.17.69.171", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("00144519-7e0d-476b-8d28-435576af9262", proxy.getString("uuid"))
        assertEquals("xtls-rprx-vision", proxy.getString("flow"))

        val reality = proxy.getJSONObject("tls").getJSONObject("reality")
        assertEquals("ALT8hIGeZEhi0sQFbXP_ntBg8Xo-v7i0YCLrqTnBxRk", reality.getString("public_key"))
        assertEquals("0990d22f", reality.getString("short_id"))
    }

    @Test
    fun testVlessGrpcUri_PreservesServiceNameAndTransport() {
        val uri = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@1.2.3.4:443?type=grpc&security=reality&pbk=valid_pbk_key&sid=1234&serviceName=my-grpc-service&sni=grpc.example.com#GrpcServer"

        val server = V2RayUriParser.parseSingleUri(uri)
        assertNotNull(server)
        assertEquals("grpc", server!!.type)
        assertEquals("my-grpc-service", server.serviceName)

        val configStr = SingBoxConfigBuilder.buildConfig(server)
        val root = JSONObject(configStr)
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)

        assertTrue(proxy.has("transport"))
        val transport = proxy.getJSONObject("transport")
        assertEquals("grpc", transport.getString("type"))
        assertEquals("my-grpc-service", transport.getString("service_name"))
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
