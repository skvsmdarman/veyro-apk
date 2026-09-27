package com.example.veyrobynovadevs.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val mandatory: Boolean = false,
    val title: String = "Update Available",
    val changelog: List<String> = emptyList()
)