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
    val isNewVersionAvailable: Boolean,
    val apkName: String? = null,
    val targetAbi: String? = null,
    val apkSizeBytes: Long = 0L,
    val apkSizeString: String? = null
)

data class MatchedApk(
    val name: String,
    val url: String,
    val abi: String?,
    val sizeBytes: Long,
    val sizeString: String?
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

    private val _isUpdateDownloaded = MutableStateFlow(false)
    val isUpdateDownloaded: StateFlow<Boolean> = _isUpdateDownloaded.asStateFlow()

    // Pre-Build state flows
    private val _preBuildUpdateInfo = MutableStateFlow<UpdateInfo?>(null)
    val preBuildUpdateInfo: StateFlow<UpdateInfo?> = _preBuildUpdateInfo.asStateFlow()

    private val _isCheckingPreBuild = MutableStateFlow(false)
    val isCheckingPreBuild: StateFlow<Boolean> = _isCheckingPreBuild.asStateFlow()

    private val _preBuildDownloadProgress = MutableStateFlow<Float?>(null)
    val preBuildDownloadProgress: StateFlow<Float?> = _preBuildDownloadProgress.asStateFlow()

    private val _preBuildStatusMessage = MutableStateFlow<String?>(null)
    val preBuildStatusMessage: StateFlow<String?> = _preBuildStatusMessage.asStateFlow()

    private val _isPreBuildDownloaded = MutableStateFlow(false)
    val isPreBuildDownloaded: StateFlow<Boolean> = _isPreBuildDownloaded.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun getDownloadedApkFile(context: Context, info: UpdateInfo?): File? {
        if (info == null) return null
        val safeTag = info.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeAbi = info.targetAbi?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "pkg"
        val apkFile = File(context.cacheDir, "mueso_update_${safeTag}_$safeAbi.apk")
        if (apkFile.exists() && apkFile.length() > 0) {
            if (info.apkSizeBytes > 0 && apkFile.length() == info.apkSizeBytes) {
                return apkFile
            } else if (apkFile.length() > 5_000_000L) {
                return apkFile
            }
        }
        return null
    }

    fun checkForUpdates(context: Context, showToastIfLatest: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            _isChecking.value = true

            if (!isNetworkAvailable(context)) {
                _statusMessage.value = "No internet connection"
                _isChecking.value = false
                if (showToastIfLatest) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

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

                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "GitHub API HTTP error ${response.code}")
                        null
                    } else {
                        response.body?.string()
                    }
                }

                if (body.isNullOrBlank()) {
                    _statusMessage.value = "Could not check for updates"
                    _isChecking.value = false
                    return@launch
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "").takeIf { it.isNotBlank() }

                val assets = json.optJSONArray("assets")
                val matchedApk = findBestMatchingApk(assets)
                val apkUrl = matchedApk?.url

                Log.d(TAG, "Latest release tag: \"$tagName\", current: \"$currentVersionName\", matched: ${matchedApk?.name} (${matchedApk?.abi})")

                val isNew = isVersionNewer(tagName, currentVersionName)

                if (apkUrl != null && isNew) {
                    val info = UpdateInfo(
                        tagName = tagName,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        isNewVersionAvailable = true,
                        apkName = matchedApk.name,
                        targetAbi = matchedApk.abi,
                        apkSizeBytes = matchedApk.sizeBytes,
                        apkSizeString = matchedApk.sizeString
                    )
                    _updateInfo.value = info
                    val downloaded = getDownloadedApkFile(context, info) != null
                    _isUpdateDownloaded.value = downloaded
                    val abiLabel = matchedApk.abi ?: "universal"
                    val sizeLabel = matchedApk.sizeString?.let { " • $it" } ?: ""
                    if (downloaded) {
                        _statusMessage.value = "Update $tagName is downloaded and ready to install"
                    } else {
                        _statusMessage.value = "New version available: $tagName ($abiLabel$sizeLabel)"
                    }
                } else {
                    val info = UpdateInfo(
                        tagName = tagName.ifBlank { currentVersionName },
                        releaseName = releaseName.ifBlank { currentVersionName },
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl ?: "",
                        isNewVersionAvailable = false,
                        apkName = matchedApk?.name,
                        targetAbi = matchedApk?.abi,
                        apkSizeBytes = matchedApk?.sizeBytes ?: 0L,
                        apkSizeString = matchedApk?.sizeString
                    )
                    _updateInfo.value = info
                    val downloaded = getDownloadedApkFile(context, info) != null
                    _isUpdateDownloaded.value = downloaded
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

    fun downloadApk(context: Context, autoInstall: Boolean = true) {
        val info = _updateInfo.value ?: return
        if (info.apkUrl.isBlank()) return

        val existingApk = getDownloadedApkFile(context, info)
        if (existingApk != null) {
            _isUpdateDownloaded.value = true
            _downloadProgress.value = null
            _statusMessage.value = "Update ${info.tagName} is ready to install"
            if (autoInstall) {
                installApk(context, existingApk)
            }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0.01f
            val abiText = info.targetAbi?.let { " ($it)" } ?: ""
            _statusMessage.value = "Downloading update ${info.tagName}$abiText..."

            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .get()
                    .build()

                val safeTag = info.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val safeAbi = info.targetAbi?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "pkg"
                val apkFile = File(context.cacheDir, "mueso_update_${safeTag}_$safeAbi.apk")
                if (apkFile.exists()) apkFile.delete()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        _statusMessage.value = "Download failed (HTTP ${response.code})"
                        _downloadProgress.value = null
                        return@launch
                    }

                    val contentLength = body.contentLength()
                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile).use { outputStream ->
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
                        }
                    }
                }

                _downloadProgress.value = null
                _isUpdateDownloaded.value = true
                _statusMessage.value = "Download complete. Ready to install."

                withContext(Dispatchers.Main) {
                    if (autoInstall) {
                        installApk(context, apkFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                _statusMessage.value = "Failed to download update: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val file = getDownloadedApkFile(context, _updateInfo.value) ?: pendingApkFile
        if (file != null && file.exists()) {
            installApk(context, file)
        } else {
            downloadApk(context, autoInstall = true)
        }
    }

    fun downloadAndInstallApk(context: Context) {
        val file = getDownloadedApkFile(context, _updateInfo.value)
        if (file != null) {
            installApk(context, file)
        } else {
            downloadApk(context, autoInstall = true)
        }
    }

    fun handleUpdateAction(context: Context) {
        val info = _updateInfo.value
        if (info == null || !info.isNewVersionAvailable) {
            checkForUpdates(context, showToastIfLatest = true)
        } else if (_isUpdateDownloaded.value || getDownloadedApkFile(context, info) != null) {
            installDownloadedApk(context)
        } else if (_downloadProgress.value == null) {
            downloadApk(context, autoInstall = true)
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

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return true
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    private fun extractCommitSha(body: String, title: String): String? {
        val bodyRegex = Regex("commit\\s+([a-fA-F0-9]{7,40})")
        bodyRegex.find(body)?.let { return it.groupValues[1].take(7) }

        val titleRegex = Regex("\\(([a-fA-F0-9]{7,40})\\)")
        titleRegex.find(title)?.let { return it.groupValues[1].take(7) }

        return null
    }

    private fun isPreBuildNewer(
        releaseSha: String?,
        installedSha: String
    ): Boolean {
        if (!releaseSha.isNullOrBlank() && installedSha.isNotBlank() && installedSha != "dev") {
            val matches = releaseSha.equals(installedSha, ignoreCase = true) ||
                    releaseSha.startsWith(installedSha, ignoreCase = true) ||
                    installedSha.startsWith(releaseSha, ignoreCase = true)
            if (matches) {
                Log.d(TAG, "Pre-Build matches installed commit SHA: $installedSha. Up to date.")
                return false
            } else {
                Log.d(TAG, "Pre-Build SHA ($releaseSha) != installed SHA ($installedSha). New update available.")
                return true
            }
        }
        return true
    }

    fun checkPreBuildRelease(context: Context, showToast: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            _isCheckingPreBuild.value = true

            // 1. Check network connectivity
            if (!isNetworkAvailable(context)) {
                _preBuildStatusMessage.value = "No internet connection"
                _isCheckingPreBuild.value = false
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No internet connection. Please check your network.", Toast.LENGTH_LONG).show()
                    }
                }
                return@launch
            }

            _preBuildStatusMessage.value = "Checking GitHub tag: Pre_Builds..."

            try {
                val url = "https://api.github.com/repos/Akshay-86/Mueso/releases/tags/Pre_Builds"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        response.body?.string()
                    }
                }

                if (body.isNullOrBlank()) {
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Pre_Builds tag not found on GitHub yet", Toast.LENGTH_LONG).show()
                        }
                    }
                    _preBuildStatusMessage.value = "Pre_Builds release not found"
                    _isCheckingPreBuild.value = false
                    return@launch
                }

                val json = JSONObject(body)
                val releaseName = json.optString("name", "Pre_Builds")
                val releaseBody = json.optString("body", "")
                val releaseSha = extractCommitSha(releaseBody, releaseName)

                val assets = json.optJSONArray("assets")
                val matchedApk = findBestMatchingApk(assets)
                val apkUrl = matchedApk?.url

                if (apkUrl.isNullOrBlank()) {
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No compatible APK file found in Pre_Builds release assets", Toast.LENGTH_LONG).show()
                        }
                    }
                    _preBuildStatusMessage.value = "No compatible APK asset in Pre_Builds release"
                    _isCheckingPreBuild.value = false
                    return@launch
                }

                // Retrieve current app version & commit sha
                val installedSha = com.akshay.musicplayer.BuildConfig.GIT_COMMIT_SHA
                val isNew = isPreBuildNewer(
                    releaseSha = releaseSha,
                    installedSha = installedSha
                )

                val displayTag = if (!releaseSha.isNullOrBlank()) "Pre_Builds ($releaseSha)" else "Pre_Builds"

                Log.d(TAG, "Pre_Build check: releaseSha=$releaseSha, installedSha=$installedSha, isNew=$isNew, matched=${matchedApk.name} (${matchedApk.abi})")

                val info = UpdateInfo(
                    tagName = displayTag,
                    releaseName = releaseName,
                    releaseNotes = releaseBody.takeIf { it.isNotBlank() },
                    apkUrl = apkUrl,
                    isNewVersionAvailable = isNew,
                    apkName = matchedApk.name,
                    targetAbi = matchedApk.abi,
                    apkSizeBytes = matchedApk.sizeBytes,
                    apkSizeString = matchedApk.sizeString
                )
                _preBuildUpdateInfo.value = info

                val downloaded = getDownloadedApkFile(context, info) != null
                _isPreBuildDownloaded.value = downloaded

                val abiLabel = matchedApk.abi ?: "universal"
                val sizeLabel = matchedApk.sizeString?.let { " • $it" } ?: ""

                if (downloaded) {
                    _preBuildStatusMessage.value = "Pre-Build ($displayTag) is downloaded and ready to install"
                } else if (isNew) {
                    _preBuildStatusMessage.value = "New Pre-Build available: $displayTag ($abiLabel$sizeLabel)"
                } else {
                    _preBuildStatusMessage.value = "You are already on the latest Pre-Build ($installedSha)"
                }

                if (showToast) {
                    withContext(Dispatchers.Main) {
                        val toastMsg = if (downloaded) {
                            "Pre-Build ready to install ($displayTag)"
                        } else if (isNew) {
                            "New Pre-Build found ($displayTag)!"
                        } else {
                            "You are already on the latest Pre-Build ($installedSha)"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Pre_Builds release", e)
                val isNetworkIssue = e is java.net.UnknownHostException || e is java.io.IOException || !isNetworkAvailable(context)
                val errorMsg = if (isNetworkIssue) "No internet connection or network error" else "Failed to fetch Pre_Builds: ${e.message}"
                _preBuildStatusMessage.value = errorMsg
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isCheckingPreBuild.value = false
            }
        }
    }

    fun downloadPreBuildRelease(context: Context, autoInstall: Boolean = true) {
        val info = _preBuildUpdateInfo.value ?: return
        if (info.apkUrl.isBlank()) return

        val existingApk = getDownloadedApkFile(context, info)
        if (existingApk != null) {
            _isPreBuildDownloaded.value = true
            _preBuildDownloadProgress.value = null
            _preBuildStatusMessage.value = "Pre-Build (${info.tagName}) is ready to install"
            if (autoInstall) {
                installApk(context, existingApk)
            }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            _preBuildDownloadProgress.value = 0.01f
            val abiText = info.targetAbi?.let { " ($it)" } ?: ""
            _preBuildStatusMessage.value = "Downloading Pre-Build ${info.tagName}$abiText..."

            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "MuesoMusicPlayerApp")
                    .get()
                    .build()

                val safeTag = info.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val safeAbi = info.targetAbi?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "pkg"
                val apkFile = File(context.cacheDir, "mueso_update_${safeTag}_$safeAbi.apk")
                if (apkFile.exists()) apkFile.delete()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        _preBuildStatusMessage.value = "Download failed (HTTP ${response.code})"
                        _preBuildDownloadProgress.value = null
                        return@launch
                    }

                    val contentLength = body.contentLength()
                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile).use { outputStream ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val prog = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0.01f, 0.99f)
                                    _preBuildDownloadProgress.value = prog
                                }
                            }
                            outputStream.flush()
                        }
                    }
                }

                _preBuildDownloadProgress.value = null
                _isPreBuildDownloaded.value = true
                _preBuildStatusMessage.value = "Pre-Build downloaded. Ready to install."

                withContext(Dispatchers.Main) {
                    if (autoInstall) {
                        installApk(context, apkFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading Pre-Build APK", e)
                _preBuildStatusMessage.value = "Failed to download Pre-Build: ${e.message}"
                _preBuildDownloadProgress.value = null
            }
        }
    }

    fun installPreBuildApk(context: Context) {
        val file = getDownloadedApkFile(context, _preBuildUpdateInfo.value) ?: pendingApkFile
        if (file != null && file.exists()) {
            installApk(context, file)
        } else {
            downloadPreBuildRelease(context, autoInstall = true)
        }
    }

    fun handlePreBuildAction(context: Context) {
        val info = _preBuildUpdateInfo.value
        if (info == null) {
            checkPreBuildRelease(context, showToast = true)
        } else if (_isPreBuildDownloaded.value || getDownloadedApkFile(context, info) != null) {
            installPreBuildApk(context)
        } else if (_preBuildDownloadProgress.value == null) {
            downloadPreBuildRelease(context, autoInstall = true)
        }
    }

    fun installPreBuildRelease(context: Context, forceDownload: Boolean = false) {
        val info = _preBuildUpdateInfo.value
        if (info != null && !forceDownload && (_isPreBuildDownloaded.value || getDownloadedApkFile(context, info) != null)) {
            installPreBuildApk(context)
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                checkPreBuildRelease(context, showToast = false)
                val updatedInfo = _preBuildUpdateInfo.value
                if (updatedInfo != null && (updatedInfo.isNewVersionAvailable || forceDownload)) {
                    val downloaded = getDownloadedApkFile(context, updatedInfo)
                    if (downloaded != null && !forceDownload) {
                        withContext(Dispatchers.Main) { installApk(context, downloaded) }
                    } else {
                        downloadPreBuildRelease(context, autoInstall = true)
                    }
                }
            }
        }
    }

    private fun findBestMatchingApk(assetsJson: org.json.JSONArray?): MatchedApk? {
        if (assetsJson == null || assetsJson.length() == 0) return null

        data class ApkCandidate(val name: String, val url: String, val size: Long)
        val apkList = mutableListOf<ApkCandidate>()
        for (i in 0 until assetsJson.length()) {
            val obj = assetsJson.optJSONObject(i) ?: continue
            val url = obj.optString("browser_download_url", "")
            val name = obj.optString("name", "").ifBlank { url.substringAfterLast('/') }
            val size = obj.optLong("size", 0L)
            if (name.endsWith(".apk", ignoreCase = true) || url.endsWith(".apk", ignoreCase = true)) {
                apkList.add(ApkCandidate(name, url, size))
            }
        }

        if (apkList.isEmpty()) return null

        // Detect supported ABIs in order of preference (e.g. ["arm64-v8a", "armeabi-v7a", "x86_64"])
        val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }

        Log.d(TAG, "Device supported ABIs in order of preference: $supportedAbis. Available release APKs: ${apkList.map { it.name }}")

        fun formatSize(bytes: Long): String? {
            if (bytes <= 0) return null
            val mb = bytes / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }

        // 1. Try to find an exact ABI match based on device supported ABIs in priority order
        for (abi in supportedAbis) {
            val candidate = apkList.firstOrNull { apk ->
                val lower = apk.name.lowercase()
                when (abi) {
                    "arm64-v8a" -> lower.contains("arm64-v8a") || lower.contains("arm64") || lower.contains("aarch64") || lower.contains("arm-v8a")
                    "armeabi-v7a" -> (lower.contains("armeabi-v7a") || lower.contains("armv7a") || lower.contains("armv7") || lower.contains("armeabi")) && !lower.contains("arm64")
                    "x86_64" -> lower.contains("x86_64") || lower.contains("x86-64") || lower.contains("x64")
                    "x86" -> lower.contains("x86") && !lower.contains("x86_64") && !lower.contains("x86-64")
                    else -> lower.contains(abi)
                }
            }
            if (candidate != null) {
                Log.d(TAG, "Selected architecture-matched APK for '$abi': ${candidate.name} (${candidate.size} bytes)")
                return MatchedApk(
                    name = candidate.name,
                    url = candidate.url,
                    abi = abi,
                    sizeBytes = candidate.size,
                    sizeString = formatSize(candidate.size)
                )
            }
        }

        // 2. Fallback to universal APK (e.g. app-universal-release.apk)
        val universalMatch = apkList.firstOrNull { it.name.lowercase().contains("universal") || it.name.lowercase().contains("fat") }
        if (universalMatch != null) {
            Log.d(TAG, "Selected universal APK: ${universalMatch.name}")
            return MatchedApk(
                name = universalMatch.name,
                url = universalMatch.url,
                abi = "universal",
                sizeBytes = universalMatch.size,
                sizeString = formatSize(universalMatch.size)
            )
        }

        // 3. Fallback to generic release APK or first available APK
        val genericMatch = apkList.firstOrNull { it.name.lowercase().contains("release") } ?: apkList.first()
        Log.d(TAG, "Selected fallback APK: ${genericMatch.name}")
        return MatchedApk(
            name = genericMatch.name,
            url = genericMatch.url,
            abi = null,
            sizeBytes = genericMatch.size,
            sizeString = formatSize(genericMatch.size)
        )
    }
}
