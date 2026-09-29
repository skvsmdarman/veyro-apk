package com.example.veyrobynovadevs.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.veyrobynovadevs.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdates(manual: Boolean = false) = withContext(Dispatchers.IO) {
        if (!manual) {
            val lastCheck = prefs.getLong(UpdateConfig.KEY_LAST_CHECK_TIME, 0L)
            if (System.currentTimeMillis() - lastCheck < UpdateConfig.CHECK_INTERVAL_MS) {
                return@withContext
            }
        }

        _updateState.value = UpdateState.Checking

        try {
            val request = Request.Builder()
                .url(UpdateConfig.UPDATE_JSON_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (manual) _updateState.value = UpdateState.Error("Update check failed (HTTP ${response.code})")
                else _updateState.value = UpdateState.Idle
                return@withContext
            }

            val body = response.body?.string() ?: ""
            val updateInfo = json.decodeFromString<UpdateInfo>(body)

            prefs.edit().putLong(UpdateConfig.KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

            if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                _updateState.value = UpdateState.UpdateAvailable(updateInfo)
            } else {
                if (manual) _updateState.value = UpdateState.Error("You are already on the latest version.")
                else _updateState.value = UpdateState.Idle
            }

        } catch (e: Exception) {
            e.printStackTrace()
            if (manual) _updateState.value = UpdateState.Error("Error checking for updates: ${e.message}")
            else _updateState.value = UpdateState.Idle
        }
    }

    suspend fun downloadAndInstallUpdate(updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        val variant = updateInfo.getVariantForDevice()
        if (variant.apkUrl.isBlank()) {
            _updateState.value = UpdateState.Error("Invalid update configuration: Missing APK URL.")
            return@withContext
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "Veyro_update_${updateInfo.versionCode}.apk")

        if (apkFile.exists()) {
            apkFile.delete()
        }

        try {
            val request = Request.Builder().url(variant.apkUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                _updateState.value = UpdateState.Error("Failed to download update (HTTP ${response.code})")
                return@withContext
            }

            val totalBytes = response.body!!.contentLength()
            var downloadedBytes = 0L

            response.body!!.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        _updateState.value = UpdateState.Downloading(progress, downloadedBytes, totalBytes)
                    }
                }
            }

            // Size check if size is specified
            if (variant.size > 0 && apkFile.length() != variant.size) {
                apkFile.delete()
                _updateState.value = UpdateState.Error("Update verification failed: Size mismatch.")
                return@withContext
            }

            // Verify SHA-256
            if (variant.sha256.isNotBlank()) {
                _updateState.value = UpdateState.Verifying
                val calculatedHash = calculateSHA256(apkFile)

                if (!calculatedHash.equals(variant.sha256, ignoreCase = true)) {
                    apkFile.delete()
                    _updateState.value = UpdateState.Error("Update verification failed: SHA-256 mismatch. The file may be corrupted.")
                    return@withContext
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(apkFile)
            installApk(apkFile)

        } catch (e: Exception) {
            e.printStackTrace()
            apkFile.delete()
            _updateState.value = UpdateState.Error("Error downloading update: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun installApk(apkFile: File) {
        _updateState.value = UpdateState.Installing
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error("Failed to launch package installer: ${e.message}")
        }
    }
}
