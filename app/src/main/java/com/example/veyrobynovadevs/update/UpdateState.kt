package com.example.veyrobynovadevs.update

import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}