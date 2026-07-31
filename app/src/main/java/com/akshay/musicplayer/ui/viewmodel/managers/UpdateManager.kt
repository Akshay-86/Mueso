@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String?,
    val apkUrl: String,
    val isNewVersionAvailable: Boolean
)

class UpdateManager(private val coroutineScope: CoroutineScope) {

    companion object {
        private const val TAG = "MUESO_UPDATE"
        private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/Akshay-86/Mueso/releases/latest"
        const val CURRENT_VERSION = "v1.0.0"
    }

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun checkForUpdates(context: Context, showToastIfLatest: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            _isChecking.value = true
            _statusMessage.value = "Checking for updates..."

            try {
                // Dynamically fetch installed version info
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionName = packageInfo.versionName ?: "v1.0.0"
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                Log.d(TAG, "Fetching latest release info from GitHub API... Current: $currentVersionName (code: $currentVersionCode)")
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_URL)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Log.e(TAG, "GitHub API HTTP error ${response.code}")
                    _statusMessage.value = "Could not check for updates"
                    _isChecking.value = false
                    return@launch
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "").takeIf { it.isNotBlank() }

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val downloadUrl = asset.optString("browser_download_url", "")
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                Log.d(TAG, "Latest release tag: \"$tagName\", current: \"$currentVersionName\", apkUrl: $apkUrl")

                val isNew = isVersionNewer(tagName, currentVersionName)

                if (apkUrl != null && isNew) {
                    val info = UpdateInfo(
                        tagName = tagName,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        isNewVersionAvailable = true
                    )
                    _updateInfo.value = info
                    _statusMessage.value = "New version available: $tagName"
                } else {
                    _updateInfo.value = UpdateInfo(
                        tagName = tagName.ifBlank { currentVersionName },
                        releaseName = releaseName.ifBlank { currentVersionName },
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl ?: "",
                        isNewVersionAvailable = false
                    )
                    _statusMessage.value = "Mueso is up to date ($currentVersionName)"
                    if (showToastIfLatest) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Mueso is up to date ($currentVersionName)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking updates", e)
                _statusMessage.value = "Error checking updates: ${e.message}"
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun downloadAndInstallApk(context: Context) {
        val info = _updateInfo.value ?: return
        if (info.apkUrl.isBlank()) return

        coroutineScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0.01f
            _statusMessage.value = "Downloading update ${info.tagName}..."

            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    _statusMessage.value = "Download failed (HTTP ${response.code})"
                    _downloadProgress.value = null
                    return@launch
                }

                val contentLength = body.contentLength()
                val apkFile = File(context.cacheDir, "mueso_update_${info.tagName}.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val prog = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0.01f, 0.99f)
                        _downloadProgress.value = prog
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadProgress.value = 1.0f
                _statusMessage.value = "Download complete. Starting installation..."

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                _statusMessage.value = "Failed to download update: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    var pendingApkFile: File? = null
        private set

    fun resetUpdateState() {
        _updateInfo.value = null
        _downloadProgress.value = null
        _statusMessage.value = null
        _isChecking.value = false
    }

    fun checkAndResumePendingInstall(context: Context) {
        val apk = pendingApkFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                pendingApkFile = null
                installApk(context, apk)
            }
        } else {
            pendingApkFile = null
            installApk(context, apk)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        // Step 1: Check Install Unknown Apps permission on Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                pendingApkFile = apkFile
                Log.w(TAG, "Install unknown apps permission not granted. Requesting from user...")
                Toast.makeText(context, "Please allow Mueso to install unknown apps. Installation will continue automatically when you return.", Toast.LENGTH_LONG).show()
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        // Step 2: Launch APK Installer via FileProvider
        try {
            pendingApkFile = null
            Log.d(TAG, "Launching APK installer for ${apkFile.absolutePath}...")
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer", e)
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isVersionNewer(latestTag: String, currentTag: String): Boolean {
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentTag.trim().removePrefix("v").removePrefix("V")

        if (cleanLatest.isBlank()) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun installPreBuildRelease(context: Context) {
        coroutineScope.launch(Dispatchers.IO) {
            _isChecking.value = true
            _statusMessage.value = "Fetching Pre_Builds release from GitHub..."
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Checking GitHub tag: Pre_Builds...", Toast.LENGTH_SHORT).show()
            }

            try {
                val url = "https://api.github.com/repos/Akshay-86/Mueso/releases/tags/Pre_Builds"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Pre_Builds tag not found on GitHub yet", Toast.LENGTH_LONG).show()
                    }
                    _statusMessage.value = "Pre_Builds release not found"
                    _isChecking.value = false
                    return@launch
                }

                val json = JSONObject(body)
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val downloadUrl = asset.optString("browser_download_url", "")
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No APK file found in Pre_Builds release assets", Toast.LENGTH_LONG).show()
                    }
                    _statusMessage.value = "No APK asset in Pre_Builds release"
                    _isChecking.value = false
                    return@launch
                }

                _updateInfo.value = UpdateInfo(
                    tagName = "Pre_Builds",
                    releaseName = "Pre-Build Release",
                    releaseNotes = json.optString("body", ""),
                    apkUrl = apkUrl,
                    isNewVersionAvailable = true
                )

                _statusMessage.value = "Downloading Pre_Builds release..."
                downloadAndInstallApk(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Pre_Builds release", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to fetch Pre_Builds: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isChecking.value = false
            }
        }
    }
}
