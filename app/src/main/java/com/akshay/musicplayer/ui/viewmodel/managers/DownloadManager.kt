package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

import com.akshay.musicplayer.media.notification.NotificationHelper

class DownloadManager(
    private val onlineRepository: OnlineMusicRepository,
    private val getDownloadFolder: () -> String,
    private val getCurrentTrack: () -> TrackEntity? = { null },
    private val getSavedLyrics: (Long) -> LyricsData? = { null },
    private val coroutineScope: CoroutineScope
) {
    private val _downloadStates = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadProgress>> = _downloadStates.asStateFlow()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val activeTempFiles = ConcurrentHashMap<Long, File>()
    private var totalDownloadedInBatch = 0
    private var lastContext: Context? = null

    init {
        NotificationHelper.onCancelDownloadRequested = { trackId ->
            cancelDownload(trackId)
        }
    }

    fun cancelDownload(trackId: Long) {
        Log.d("MUESO_DOWNLOAD", "cancelDownload requested for trackId=$trackId")
        try {
            val prefix = "$trackId"
            val negPrefix = "-$trackId"
            activeCalls.keys.filter { it == prefix || it == negPrefix || it.startsWith("${prefix}_") || it.startsWith("${negPrefix}_") }.forEach { key ->
                try { activeCalls.remove(key)?.cancel() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            activeTempFiles.remove(trackId)?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("MUESO_DOWNLOAD", "Deleted partial download temp file for $trackId: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("MUESO_DOWNLOAD", "Error deleting partial download file for trackId=$trackId", e)
        }

        val job = activeJobs.remove(trackId)
        job?.cancel(CancellationException("Download cancelled by user"))

        _downloadStates.value = _downloadStates.value - trackId

        lastContext?.let { ctx ->
            if (activeJobs.isEmpty()) {
                NotificationHelper.dismissDownloadNotification(ctx.applicationContext)
            }
        }
    }

    fun downloadOnlineTrack(context: Context, track: TrackEntity) {
        if (_downloadStates.value[track.id]?.isDownloading == true || _downloadStates.value[track.id]?.isDownloaded == true) return

        lastContext = context.applicationContext

        val job = coroutineScope.launch(Dispatchers.IO) {
            _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.01f))
            val currentActiveCount = activeJobs.size
            NotificationHelper.showDownloadProgress(
                context = context.applicationContext,
                trackId = track.id,
                trackTitle = track.title,
                artist = track.artist,
                completedCount = 1,
                totalCount = currentActiveCount.coerceAtLeast(1),
                progress = 0.05f
            )

            var tempFile: File? = null
            var savedDestFile: File? = null

            try {
                val dlQuality = context.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
                    .getString("download_quality", "Standard (256 kbps)")
                val videoId = if (track.filePath.startsWith("online:")) track.filePath.removePrefix("online:") else null
                val downloadUrl = if (videoId != null) onlineRepository.getStreamUrl(videoId, context, audioQuality = dlQuality) else track.filePath
                
                if (!downloadUrl.startsWith("http")) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Stream URL unavailable"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to get audio stream for download", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val ext = if (downloadUrl.contains("mime=audio%2Fmp4") || downloadUrl.contains(".m4a") || downloadUrl.contains("mime=video%2Fmp4")) ".m4a" else ".mp3"
                val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                
                val createdTempFile = File(context.cacheDir, "temp_dl_${track.id}$ext")
                tempFile = createdTempFile
                activeTempFiles[track.id] = createdTempFile
                if (createdTempFile.exists()) createdTempFile.delete()

                val downloadSuccess = downloadStreamWithResume(
                    client = client,
                    url = downloadUrl,
                    destFile = createdTempFile,
                    trackId = track.id,
                    onProgress = { downloaded, total ->
                        if (total > 0) {
                            val prog = (downloaded.toFloat() / total.toFloat()).coerceIn(0.01f, 0.95f)
                            _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = prog))
                            NotificationHelper.showDownloadProgress(
                                context = context.applicationContext,
                                trackId = track.id,
                                trackTitle = track.title,
                                artist = track.artist,
                                completedCount = totalDownloadedInBatch + 1,
                                totalCount = activeJobs.size.coerceAtLeast(1),
                                progress = prog
                            )
                        }
                    },
                    onRefreshUrl = {
                        if (videoId != null) {
                            onlineRepository.getStreamUrl(videoId, context, forceRefresh = true, audioQuality = dlQuality)
                        } else null
                    }
                )

                if (!downloadSuccess || !createdTempFile.exists() || createdTempFile.length() == 0L) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Download failed"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.96f))

                val fileToSave = createdTempFile

                // Resolve active lyrics (currently playing track, track itself, or user-selected custom lyrics)
                val shouldEmbedLyrics = context.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
                    .getBoolean("embed_lyrics_in_download", true)
                val currentPlayingTrack = getCurrentTrack()
                val activeLyrics = if (track.id == currentPlayingTrack?.id && currentPlayingTrack.lyrics != null) {
                    currentPlayingTrack.lyrics
                } else {
                    track.lyrics ?: getSavedLyrics(track.id)
                }
                val lrcContent = if (shouldEmbedLyrics) activeLyrics?.toLrcString()?.ifBlank { null } else null

                val albumName = if (track.album.isNotBlank() && track.album != "Unknown Album") track.album else "Mueso Downloads"
                val embedSuccess = onlineRepository.embedMetadata(
                    filePath = fileToSave.absolutePath,
                    title = track.title,
                    artist = track.artist,
                    album = albumName,
                    artworkUrl = track.artworkUrl,
                    lyricsText = lrcContent
                )
                Log.d("MUESO_DOWNLOAD", "Metadata embedded for '${track.title}' (success=$embedSuccess, lyrics=${!lrcContent.isNullOrBlank()})")

                val folderSetting = getDownloadFolder()
                val targetDir = when {
                    folderSetting == "Internal App Storage" -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                    folderSetting == "Downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    folderSetting.startsWith("/") -> File(folderSetting)
                    folderSetting.startsWith("Music/") -> {
                        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        File(musicDir, folderSetting.removePrefix("Music/"))
                    }
                    folderSetting.equals("Music", ignoreCase = true) -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    else -> {
                        val externalDir = Environment.getExternalStorageDirectory()
                        val customDir = File(externalDir, folderSetting)
                        if (customDir.exists() || folderSetting.contains("/")) {
                            customDir
                        } else {
                            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                            File(musicDir, folderSetting)
                        }
                    }
                }
                if (!targetDir.exists()) targetDir.mkdirs()
                val actualDir = if (targetDir.exists()) targetDir else (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)

                val destFile = File(actualDir, "$sanitizedTitle$ext")
                fileToSave.copyTo(destFile, overwrite = true)
                fileToSave.delete()
                activeTempFiles.remove(track.id)
                tempFile = null
                savedDestFile = destFile

                // Also save companion .lrc file next to the song for external players (e.g. Realme Music) if enabled
                if (shouldEmbedLyrics && !lrcContent.isNullOrBlank()) {
                    try {
                        val lrcFile = File(actualDir, "$sanitizedTitle.lrc")
                        lrcFile.writeText(lrcContent)
                        Log.d("MUESO_DOWNLOAD", "Saved companion LRC file: ${lrcFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.w("MUESO_DOWNLOAD", "Failed to write companion LRC file: ${e.message}")
                    }
                }

                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null) { path, uri ->
                    Log.d("MUESO_DOWNLOAD", "MediaScanner scanned $path -> $uri")
                }

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                totalDownloadedInBatch++
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved \"${track.title}\" to ${actualDir.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                Log.d("MUESO_DOWNLOAD", "Download cancelled for track ${track.title}")
                try { tempFile?.delete() } catch (_: Exception) {}
                activeTempFiles.remove(track.id)?.let { try { it.delete() } catch (_: Exception) {} }
                _downloadStates.value = _downloadStates.value - track.id
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MUESO_DOWNLOAD", "Error downloading track ${track.title}", e)
                try { tempFile?.delete() } catch (_: Exception) {}
                activeTempFiles.remove(track.id)?.let { try { it.delete() } catch (_: Exception) {} }
                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = e.message))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                activeJobs.remove(track.id)
                val prefix = "${track.id}"
                activeCalls.keys.filter { it == prefix || it.startsWith("${prefix}_") }.forEach { activeCalls.remove(it) }
                activeTempFiles.remove(track.id)
                if (activeJobs.isEmpty()) {
                    if (totalDownloadedInBatch > 0) {
                        NotificationHelper.showDownloadComplete(
                            context = context.applicationContext,
                            totalDownloaded = totalDownloadedInBatch.coerceAtLeast(1),
                            lastTitle = track.title,
                            lastArtist = track.artist,
                            lastFilePath = savedDestFile?.absolutePath
                        )
                        totalDownloadedInBatch = 0
                    } else {
                        NotificationHelper.dismissDownloadNotification(context.applicationContext)
                    }
                }
            }
        }
        activeJobs[track.id] = job
    }

    private suspend fun downloadStreamWithResume(
        client: OkHttpClient,
        url: String,
        destFile: File,
        trackId: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        maxRetriesPerChunk: Int = 5,
        concurrency: Int = 1,
        onRefreshUrl: (suspend () -> String?)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val isGoogleVideo = url.contains("googlevideo.com")
        val totalBytesExpected = if (isGoogleVideo) {
            try {
                android.net.Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() ?: -1L
            } catch (_: Exception) {
                -1L
            }
        } else {
            -1L
        }

        Log.d("MUESO_DOWNLOAD", "[START_DOWNLOAD] trackId=$trackId isGoogleVideo=$isGoogleVideo totalSize=${if (totalBytesExpected > 0) "${totalBytesExpected / (1024*1024)}MB ($totalBytesExpected bytes)" else "unknown"}")
        downloadSequential(client, url, destFile, trackId, totalBytesExpected, onProgress, maxRetriesPerChunk, onRefreshUrl)
    }

    private suspend fun downloadSequential(
        client: OkHttpClient,
        initialUrl: String,
        destFile: File,
        trackId: Long,
        totalBytesExpectedInit: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        maxRetries: Int = 5,
        onRefreshUrl: (suspend () -> String?)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl
        var isGoogleVideo = currentUrl.contains("googlevideo.com")
        var totalBytesExpected = if (isGoogleVideo) {
            try {
                android.net.Uri.parse(currentUrl).getQueryParameter("clen")?.toLongOrNull() ?: totalBytesExpectedInit
            } catch (_: Exception) {
                totalBytesExpectedInit
            }
        } else {
            totalBytesExpectedInit
        }
        var consecutiveFailures = 0
        val buffer = ByteArray(128 * 1024)

        while (consecutiveFailures < maxRetries) {
            val currentBytes = if (destFile.exists()) destFile.length() else 0L
            if (totalBytesExpected > 0 && currentBytes >= totalBytesExpected) {
                return@withContext true
            }

            val cleanBaseUrl = currentUrl.replace(Regex("[?&]range=[0-9]+-[0-9]+"), "")
                .replace(Regex("[?&]rn=[0-9]+"), "")
                .replace(Regex("[?&]rbuf=[0-9]+"), "")

            val end = if (totalBytesExpected > 0) totalBytesExpected - 1 else -1L
            val finalUrl = if (isGoogleVideo && end > 0) {
                val sep = if (cleanBaseUrl.contains("?")) "&" else "?"
                "$cleanBaseUrl${sep}range=$currentBytes-$end"
            } else {
                currentUrl
            }

            val requestBuilder = Request.Builder().url(finalUrl)
            if (isGoogleVideo) {
                dressGoogleVideoRequest(requestBuilder, finalUrl)
            } else if (currentBytes > 0) {
                requestBuilder.header("Range", "bytes=$currentBytes-")
            }

            var call: okhttp3.Call? = null
            val callKey = "${trackId}_seq"
            try {
                call = client.newCall(requestBuilder.build())
                activeCalls[callKey] = call
                val response = call.execute()
                val body = response.body

                if (response.code == 416) {
                    return@withContext destFile.exists() && destFile.length() > 0
                }

                if (response.code == 403) {
                    Log.w("MUESO_DOWNLOAD", "[SEQ_ERR] HTTP 403 for $trackId, attempting stream URL refresh")
                    val refreshed = onRefreshUrl?.invoke()
                    if (!refreshed.isNullOrBlank() && refreshed != currentUrl) {
                        Log.d("MUESO_DOWNLOAD", "[REFRESH_SUCCESS] Obtained fresh stream URL for $trackId")
                        currentUrl = refreshed
                        isGoogleVideo = currentUrl.contains("googlevideo.com")
                        consecutiveFailures = 0
                        continue
                    }
                }

                if (!response.isSuccessful || body == null) {
                    Log.w("MUESO_DOWNLOAD", "[SEQ_ERR] HTTP ${response.code} for $trackId")
                    consecutiveFailures++
                    delay(1000L * consecutiveFailures)
                    continue
                }

                if (totalBytesExpected <= 0) {
                    val contentRange = response.header("Content-Range")
                    if (!contentRange.isNullOrBlank()) {
                        totalBytesExpected = contentRange.substringAfterLast('/').toLongOrNull() ?: -1L
                    }
                    if (totalBytesExpected <= 0 && response.code == 200) {
                        totalBytesExpected = body.contentLength()
                    }
                }

                val append = currentBytes > 0 && (response.code == 206 || (isGoogleVideo && (finalUrl.contains("range=") || finalUrl.contains("&range="))))
                if (!append && currentBytes > 0) {
                    destFile.delete()
                }

                val outputStream = java.io.FileOutputStream(destFile, append)
                val inputStream = body.byteStream()
                var bytesRead: Int
                var fileBytes = if (append) currentBytes else 0L

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        fileBytes += bytesRead
                        onProgress(fileBytes, totalBytesExpected)
                    }
                    outputStream.flush()
                    consecutiveFailures = 0

                    if (totalBytesExpected <= 0 || fileBytes >= totalBytesExpected) {
                        return@withContext true
                    }
                } finally {
                    try { outputStream.close() } catch (_: Exception) {}
                    try { inputStream.close() } catch (_: Exception) {}
                    try { body.close() } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MUESO_DOWNLOAD", "[SEQ_EXCEPTION] for $trackId: ${e.message}")
                consecutiveFailures++
                delay(1000L * consecutiveFailures)
            } finally {
                activeCalls.remove(callKey)
            }
        }
        return@withContext destFile.exists() && destFile.length() > 0 && (totalBytesExpected <= 0 || destFile.length() >= totalBytesExpected)
    }

    private fun dressGoogleVideoRequest(requestBuilder: Request.Builder, url: String) {
        val isIos = url.contains("c=IOS") || url.contains("cver=20.11.6") || url.contains("cver=19.")
        val isAndroid = url.contains("c=ANDROID")
        when {
            isIos -> {
                requestBuilder.header("User-Agent", "com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)")
                requestBuilder.header("Accept", "*/*")
                requestBuilder.header("Accept-Encoding", "identity;q=1, *;q=0")
            }
            isAndroid -> {
                requestBuilder.header("User-Agent", "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip")
                requestBuilder.header("Accept", "*/*")
                requestBuilder.header("Accept-Encoding", "identity;q=1, *;q=0")
            }
            else -> {
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                requestBuilder.header("Accept", "*/*")
                requestBuilder.header("Accept-Encoding", "identity;q=1, *;q=0")
                requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
                requestBuilder.header("Origin", "https://music.youtube.com")
                requestBuilder.header("Referer", "https://music.youtube.com/")
            }
        }
    }
}
