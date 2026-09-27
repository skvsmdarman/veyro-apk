package com.example.veyrobynovadevs.utils

import com.example.veyrobynovadevs.model.V2RayServer

object CountryUtils {

    enum class Region(val displayName: String) {
        ALL("All Servers 🌐"),
        AMERICAS("Americas 🌎"),
        EUROPE("Europe 🇪🇺"),
        ASIA_PACIFIC("Asia / Pacific 🌏")
    }

    data class CountryInfo(
        val code: String,
        val name: String,
        val flagEmoji: String,
        val region: Region
    )

    private val countryNameMap = mapOf(
        "US" to "United States",
        "DE" to "Germany",
        "FR" to "France",
        "NL" to "Netherlands",
        "GB" to "United Kingdom",
        "UK" to "United Kingdom",
        "CA" to "Canada",
        "JP" to "Japan",
        "SG" to "Singapore",
        "FI" to "Finland",
        "SE" to "Sweden",
        "TR" to "Turkey",
        "IR" to "Iran",
        "IN" to "India",
        "KR" to "South Korea",
        "AU" to "Australia",
        "BR" to "Brazil",
        "RU" to "Russia",
        "CH" to "Switzerland",
        "PL" to "Poland",
        "ES" to "Spain",
        "IT" to "Italy",
        "AE" to "UAE"
    )

    private val countryRegionMap = mapOf(
        "US" to Region.AMERICAS,
        "CA" to Region.AMERICAS,
        "BR" to Region.AMERICAS,

        "DE" to Region.EUROPE,
        "FR" to Region.EUROPE,
        "NL" to Region.EUROPE,
        "GB" to Region.EUROPE,
        "UK" to Region.EUROPE,
        "FI" to Region.EUROPE,
        "SE" to Region.EUROPE,
        "RU" to Region.EUROPE,
        "CH" to Region.EUROPE,
        "PL" to Region.EUROPE,
        "ES" to Region.EUROPE,
        "IT" to Region.EUROPE,

        "JP" to Region.ASIA_PACIFIC,
        "SG" to Region.ASIA_PACIFIC,
        "IN" to Region.ASIA_PACIFIC,
        "KR" to Region.ASIA_PACIFIC,
        "AU" to Region.ASIA_PACIFIC,
        "TR" to Region.ASIA_PACIFIC,
        "IR" to Region.ASIA_PACIFIC,
        "AE" to Region.ASIA_PACIFIC
    )

    fun getCountryInfo(server: V2RayServer): CountryInfo {
        // 1. Check if name contains existing flag emoji
        val existingFlag = extractFlagEmoji(server.name)

        // 2. Infer country code from name or address
        val code = detectCountryCode(server.name, server.address)

        val name = countryNameMap[code] ?: if (code != "UNKNOWN") code else "Global Proxy"
        val flag = if (existingFlag != null) existingFlag else if (code != "UNKNOWN") countryCodeToEmoji(code) else "🌐"
        val region = countryRegionMap[code] ?: Region.ALL

        return CountryInfo(
            code = code,
            name = name,
            flagEmoji = flag,
            region = region
        )
    }

    fun countryCodeToEmoji(code: String): String {
        if (code.length != 2) return "🌐"
        val uppercaseCode = code.uppercase()
        val firstChar = Character.toChars(0x1F1E6 + (uppercaseCode[0] - 'A'))
        val secondChar = Character.toChars(0x1F1E6 + (uppercaseCode[1] - 'A'))
        return String(firstChar) + String(secondChar)
    }

    private fun extractFlagEmoji(text: String): String? {
        val regex = Regex("[\\uD83C][\\uDDE6-\\uDDFF]{2}")
        return regex.find(text)?.value
    }

    private fun detectCountryCode(name: String, address: String): String {
        val lowerName = name.lowercase()

        return when {
            lowerName.contains("[us]") || lowerName.contains("us") || lowerName.contains("usa") || lowerName.contains("united states") || lowerName.contains("america") -> "US"
            lowerName.contains("[de]") || lowerName.contains("de") || lowerName.contains("germany") || lowerName.contains("deutschland") -> "DE"
            lowerName.contains("[fr]") || lowerName.contains("fr") || lowerName.contains("france") -> "FR"
            lowerName.contains("[nl]") || lowerName.contains("nl") || lowerName.contains("netherlands") || lowerName.contains("dutch") -> "NL"
            lowerName.contains("[gb]") || lowerName.contains("[uk]") || lowerName.contains("uk") || lowerName.contains("london") || lowerName.contains("britain") -> "GB"
            lowerName.contains("[fi]") || lowerName.contains("fi") || lowerName.contains("finland") -> "FI"
            lowerName.contains("[se]") || lowerName.contains("se") || lowerName.contains("sweden") -> "SE"
            lowerName.contains("[ca]") || lowerName.contains("ca") || lowerName.contains("canada") -> "CA"
            lowerName.contains("[jp]") || lowerName.contains("jp") || lowerName.contains("japan") || lowerName.contains("tokyo") -> "JP"
            lowerName.contains("[sg]") || lowerName.contains("sg") || lowerName.contains("singapore") -> "SG"
            lowerName.contains("[tr]") || lowerName.contains("tr") || lowerName.contains("turkey") || lowerName.contains("istanbul") -> "TR"
            lowerName.contains("[ir]") || lowerName.contains("ir") || lowerName.contains("iran") -> "IR"
            lowerName.contains("[in]") || lowerName.contains("in") || lowerName.contains("india") -> "IN"
            lowerName.contains("[kr]") || lowerName.contains("kr") || lowerName.contains("korea") || lowerName.contains("seoul") -> "KR"
            lowerName.contains("[ae]") || lowerName.contains("uae") || lowerName.contains("dubai") -> "AE"
            lowerName.contains("[ch]") || lowerName.contains("switzerland") -> "CH"
            lowerName.contains("[pl]") || lowerName.contains("poland") -> "PL"
            else -> detectFromAddress(address)
        }
    }

    private fun detectFromAddress(address: String): String {
        val lowerAddr = address.lowercase()
        return when {
            lowerAddr.endsWith(".us") || lowerAddr.contains("us-") -> "US"
            lowerAddr.endsWith(".de") || lowerAddr.contains("de-") -> "DE"
            lowerAddr.endsWith(".fr") || lowerAddr.contains("fr-") -> "FR"
            lowerAddr.endsWith(".nl") || lowerAddr.contains("nl-") -> "NL"
            lowerAddr.endsWith(".uk") || lowerAddr.contains("uk-") -> "GB"
            lowerAddr.endsWith(".fi") || lowerAddr.contains("fi-") -> "FI"
            lowerAddr.endsWith(".se") || lowerAddr.contains("se-") -> "SE"
            lowerAddr.endsWith(".ca") || lowerAddr.contains("ca-") -> "CA"
            lowerAddr.endsWith(".jp") || lowerAddr.contains("jp-") -> "JP"
            lowerAddr.endsWith(".sg") || lowerAddr.contains("sg-") -> "SG"
            lowerAddr.endsWith(".tr") || lowerAddr.contains("tr-") -> "TR"
            lowerAddr.endsWith(".ir") || lowerAddr.contains("ir-") -> "IR"
            address.startsWith("167.17.") || address.startsWith("15.") -> "US"
            address.startsWith("188.42.") || address.startsWith("82.24.") -> "NL"
            address.startsWith("89.35.") -> "DE"
            else -> "US"
        }
    }
}
