package com.example.veyrobynovadevs

import com.example.veyrobynovadevs.update.UpdateConfig
import com.example.veyrobynovadevs.update.UpdateInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testUpdateConfigUrl() {
        assertEquals(
            "https://raw.githubusercontent.com/skvsmdarman/veyro-apk/main/update.json",
            UpdateConfig.UPDATE_JSON_URL
        )
    }

    @Test
    fun testUpdateInfoParsing() {
        val jsonString = """
            {
              "versionCode": 2,
              "versionName": "1.0.0",
              "apkUrl": "https://github.com/skvsmdarman/veyro-apk/releases/download/v1.0.0/Veyro-1.0.0.apk",
              "size": 18400000,
              "sha256": "abcdef123456",
              "mandatory": false,
              "title": "Veyro Update",
              "changelog": ["Improved VPN stability"]
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val updateInfo = json.decodeFromString<UpdateInfo>(jsonString)

        assertNotNull(updateInfo)
        assertEquals(2, updateInfo.versionCode)
        assertEquals("1.0.0", updateInfo.versionName)
        assertEquals(18400000L, updateInfo.size)
        assertEquals("abcdef123456", updateInfo.sha256)
        assertEquals(false, updateInfo.mandatory)
        assertEquals(1, updateInfo.changelog.size)
    }
}
