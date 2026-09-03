package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
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
                val videoId = if (track.filePath.startsWith("online:")) track.filePath.removePrefix("online:") else null
                val downloadUrl = if (videoId != null) onlineRepository.getStreamUrl(videoId, context) else track.filePath
                
                if (!downloadUrl.startsWith("http")) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Stream URL unavailable"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to get audio stream for download", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val client = OkHttpClient.Builder()
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
                onlineRepository.embedMetadata(createdTempFile.absolutePath, track.title, track.artist, "Mueso Downloads", track.artworkUrl)

                val folderSetting = getDownloadFolder()
                val targetDir = when {
                    folderSetting == "Downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    folderSetting == "Internal App Storage" -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                    folderSetting.startsWith("/") -> File(folderSetting)
                    else -> {
                        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        File(musicDir, folderSetting.removePrefix("Music/"))
                    }
                }
                if (!targetDir.exists()) targetDir.mkdirs()

                val destFile = File(targetDir, "$sanitizedTitle$ext")
                createdTempFile.copyTo(destFile, overwrite = true)
                createdTempFile.delete()
                activeTempFiles.remove(track.id)
                tempFile = null
                savedDestFile = destFile

                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                totalDownloadedInBatch++
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved \"${track.title}\" to ${targetDir.name}", Toast.LENGTH_SHORT).show()
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
        concurrency: Int = 4
    ): Boolean = withContext(Dispatchers.IO) {
        val isGoogleVideo = url.contains("googlevideo.com")
        var totalBytesExpected = if (isGoogleVideo) {
            try {
                android.net.Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() ?: -1L
            } catch (_: Exception) {
                -1L
            }
        } else {
            -1L
        }

        // Only probe non-googlevideo URLs with Range header
        if (!isGoogleVideo && totalBytesExpected <= 0) {
            try {
                val probeReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Range", "bytes=0-0")
                    .build()
                val probeCall = client.newCall(probeReq)
                activeCalls["${trackId}_probe"] = probeCall
                val probeResp = probeCall.execute()
                val contentRange = probeResp.header("Content-Range")
                if (!contentRange.isNullOrBlank()) {
                    val totalStr = contentRange.substringAfterLast('/')
                    totalBytesExpected = totalStr.toLongOrNull() ?: -1L
                }
                if (totalBytesExpected <= 0 && probeResp.code == 200) {
                    totalBytesExpected = probeResp.body?.contentLength() ?: -1L
                }
                probeResp.close()
            } catch (e: Exception) {
                Log.w("MUESO_DOWNLOAD", "Probe failed for $trackId: ${e.message}")
            } finally {
                activeCalls.remove("${trackId}_probe")
            }
        }

        Log.d("MUESO_DOWNLOAD", "[PROBE] trackId=$trackId isGoogleVideo=$isGoogleVideo totalSize=${if (totalBytesExpected > 0) "${totalBytesExpected / (1024*1024)}MB ($totalBytesExpected bytes)" else "unknown"}")

        // For small files or unknown sizes, download sequentially
        val chunkSize = 2 * 1024 * 1024L // 2MB chunk size
        if (totalBytesExpected <= 6 * 1024 * 1024L || !isGoogleVideo) {
            return@withContext downloadSequential(client, url, destFile, trackId, totalBytesExpected, onProgress, maxRetriesPerChunk)
        }

        // Parallel ranged chunk downloading (inspired by Zuno's ranged googlevideo engine)
        try {
            val raf = java.io.RandomAccessFile(destFile, "rw")
            raf.setLength(totalBytesExpected)
            raf.close()
        } catch (e: Exception) {
            Log.e("MUESO_DOWNLOAD", "Failed to preallocate file of size $totalBytesExpected", e)
            return@withContext downloadSequential(client, url, destFile, trackId, totalBytesExpected, onProgress, maxRetriesPerChunk)
        }

        data class Chunk(val index: Int, val start: Long, val end: Long)
        val chunks = mutableListOf<Chunk>()
        var offset = 0L
        var idx = 0
        while (offset < totalBytesExpected) {
            val end = minOf(offset + chunkSize - 1, totalBytesExpected - 1)
            chunks.add(Chunk(idx++, offset, end))
            offset = end + 1
        }

        val totalChunks = chunks.size
        Log.d("MUESO_DOWNLOAD", "[PARALLEL_START] trackId=$trackId totalSize=${totalBytesExpected / (1024*1024)}MB split into $totalChunks chunks")

        val chunkQueue = kotlinx.coroutines.channels.Channel<Chunk>(totalChunks)
        chunks.forEach { chunkQueue.trySend(it) }
        chunkQueue.close()

        val totalBytesDownloaded = java.util.concurrent.atomic.AtomicLong(0L)
        val startTime = System.currentTimeMillis()
        val hasFailed = java.util.concurrent.atomic.AtomicBoolean(false)
        val cleanBaseUrl = url.replace(Regex("[?&]range=[0-9]+-[0-9]+"), "")
            .replace(Regex("[?&]rn=[0-9]+"), "")
            .replace(Regex("[?&]rbuf=[0-9]+"), "")

        coroutineScope {
            val workers = (0 until concurrency.coerceAtMost(totalChunks)).map { workerId ->
                async(Dispatchers.IO) {
                    val buffer = ByteArray(64 * 1024)
                    for (chunk in chunkQueue) {
                        if (hasFailed.get()) break

                        var chunkSuccess = false
                        var attempt = 0
                        while (attempt < maxRetriesPerChunk && !chunkSuccess && !hasFailed.get()) {
                            attempt++
                            val callKey = "${trackId}_w${workerId}_c${chunk.index}"
                            val sep = if (cleanBaseUrl.contains("?")) "&" else "?"
                            val chunkUrl = "$cleanBaseUrl${sep}range=${chunk.start}-${chunk.end}"

                            val req = Request.Builder()
                                .url(chunkUrl)

                            dressGoogleVideoRequest(req, chunkUrl)
                            val builtReq = req.build()

                            try {
                                val call = client.newCall(builtReq)
                                activeCalls[callKey] = call
                                val resp = call.execute()
                                val body = resp.body

                                if (!resp.isSuccessful || body == null) {
                                    Log.w("MUESO_DOWNLOAD", "[WORKER_$workerId] Chunk #${chunk.index} HTTP ${resp.code} (attempt $attempt)")
                                    if (attempt >= maxRetriesPerChunk) {
                                        hasFailed.set(true)
                                        return@async false
                                    }
                                    delay(1000L * attempt)
                                    continue
                                }

                                val chunkRaf = java.io.RandomAccessFile(destFile, "rw")
                                chunkRaf.seek(chunk.start)
                                val inStream = body.byteStream()
                                var read: Int

                                try {
                                    while (inStream.read(buffer).also { read = it } != -1) {
                                        if (hasFailed.get()) break
                                        chunkRaf.write(buffer, 0, read)
                                        val totalReadSoFar = totalBytesDownloaded.addAndGet(read.toLong())
                                        onProgress(totalReadSoFar, totalBytesExpected)
                                    }
                                    chunkSuccess = true
                                } finally {
                                    try { chunkRaf.close() } catch (_: Exception) {}
                                    try { inStream.close() } catch (_: Exception) {}
                                    try { body.close() } catch (_: Exception) {}
                                }
                            } catch (e: CancellationException) {
                                hasFailed.set(true)
                                throw e
                            } catch (e: Exception) {
                                Log.w("MUESO_DOWNLOAD", "[WORKER_$workerId] Chunk #${chunk.index} error: ${e.message}")
                                if (attempt >= maxRetriesPerChunk) {
                                    hasFailed.set(true)
                                    return@async false
                                }
                                delay(1000L * attempt)
                            } finally {
                                activeCalls.remove(callKey)
                            }
                        }

                        if (!chunkSuccess) {
                            hasFailed.set(true)
                            return@async false
                        }
                    }
                    true
                }
            }
            workers.forEach { it.await() }
        }

        val totalDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
        val finalSize = if (destFile.exists()) destFile.length() else 0L
        val avgSpeedMBs = (finalSize.toDouble() / (1024.0 * 1024.0)) / (totalDurationMs.toDouble() / 1000.0)
        Log.d("MUESO_DOWNLOAD", "[PARALLEL_DONE] trackId=$trackId finalSize=${finalSize / (1024*1024)}MB in ${totalDurationMs/1000}s -> SPEED: ${String.format("%.2f", avgSpeedMBs)} MB/s")

        !hasFailed.get() && finalSize >= totalBytesExpected
    }

    private suspend fun downloadSequential(
        client: OkHttpClient,
        url: String,
        destFile: File,
        trackId: Long,
        totalBytesExpectedInit: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        maxRetries: Int = 5
    ): Boolean = withContext(Dispatchers.IO) {
        val isGoogleVideo = url.contains("googlevideo.com")
        var totalBytesExpected = if (isGoogleVideo) {
            try {
                android.net.Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() ?: totalBytesExpectedInit
            } catch (_: Exception) {
                totalBytesExpectedInit
            }
        } else {
            totalBytesExpectedInit
        }
        var attempt = 0
        val buffer = ByteArray(64 * 1024)
        val cleanBaseUrl = url.replace(Regex("[?&]range=[0-9]+-[0-9]+"), "")
            .replace(Regex("[?&]rn=[0-9]+"), "")
            .replace(Regex("[?&]rbuf=[0-9]+"), "")

        while (attempt < maxRetries) {
            attempt++
            val currentBytes = if (destFile.exists()) destFile.length() else 0L
            if (totalBytesExpected > 0 && currentBytes >= totalBytesExpected) {
                return@withContext true
            }

            val sliceSize = 512 * 1024L // 512 KB per slice to prevent googlevideo range size rejection
            val end = if (totalBytesExpected > 0) minOf(currentBytes + sliceSize - 1, totalBytesExpected - 1) else currentBytes + sliceSize - 1

            val finalUrl = if (isGoogleVideo) {
                val sep = if (cleanBaseUrl.contains("?")) "&" else "?"
                "$cleanBaseUrl${sep}range=$currentBytes-$end"
            } else {
                url
            }

            val requestBuilder = Request.Builder()
                .url(finalUrl)

            dressGoogleVideoRequest(requestBuilder, finalUrl)

            if (!isGoogleVideo && currentBytes > 0) {
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
                    return@withContext true
                }

                if (!response.isSuccessful || body == null) {
                    Log.w("MUESO_DOWNLOAD", "[SEQ_ERR] HTTP ${response.code} for $trackId (attempt $attempt/$maxRetries)")
                    if (attempt >= maxRetries) return@withContext false
                    delay(1000L * attempt)
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

                val append = currentBytes > 0 && (response.code == 206 || (isGoogleVideo && finalUrl.contains("range=")))
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
                    return@withContext true
                } finally {
                    try { outputStream.close() } catch (_: Exception) {}
                    try { inputStream.close() } catch (_: Exception) {}
                    try { body.close() } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MUESO_DOWNLOAD", "[SEQ_EXCEPTION] for $trackId (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt >= maxRetries) return@withContext false
                delay(1000L * attempt)
            } finally {
                activeCalls.remove(callKey)
            }
        }
        destFile.exists() && destFile.length() > 0
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
                requestBuilder.header("Origin", "https://music.youtube.com")
                requestBuilder.header("Referer", "https://music.youtube.com/")
            }
        }
    }
}
