package com.example.veyrobynovadevs.update

object UpdateConfig {
    // URL to the update.json manifest for Veyro public release distribution
    const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/skvsmdarman/veyro-apk/main/update.json"
    const val PREFS_NAME = "veyro_update_prefs"
    const val KEY_LAST_CHECK_TIME = "last_check_time"
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
}
