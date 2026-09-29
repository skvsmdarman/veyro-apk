package com.example.veyrobynovadevs.update

import android.os.Build
import kotlinx.serialization.Serializable

@Serializable
data class ApkVariant(
    val apkUrl: String,
    val size: Long,
    val sha256: String
)

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String = "",
    val size: Long = 0L,
    val sha256: String = "",
    val variants: Map<String, ApkVariant> = emptyMap(),
    val mandatory: Boolean = false,
    val title: String = "Veyro Update",
    val changelog: List<String> = emptyList()
) {
    fun getVariantForDevice(): ApkVariant {
        if (variants.isNotEmpty()) {
            val supportedAbis = try { Build.SUPPORTED_ABIS } catch (_: Throwable) { null }
            if (supportedAbis != null) {
                for (abi in supportedAbis) {
                    val variant = variants[abi]
                    if (variant != null && variant.apkUrl.isNotBlank()) {
                        return variant
                    }
                }
            }
            val firstVariant = variants["arm64-v8a"] ?: variants.values.firstOrNull()
            if (firstVariant != null && firstVariant.apkUrl.isNotBlank()) {
                return firstVariant
            }
        }
        return ApkVariant(
            apkUrl = apkUrl,
            size = size,
            sha256 = sha256
        )
    }
}
