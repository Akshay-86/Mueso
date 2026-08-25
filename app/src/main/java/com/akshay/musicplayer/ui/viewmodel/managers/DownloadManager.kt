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
    private val getVideoDownloadFolder: () -> String = { "Movies/Mueso" },
    private val getDefaultVideoResolution: () -> String = { "1080p (FHD)" },
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
                val downloadUrl = if (videoId != null) onlineRepository.getStreamUrl(videoId) else track.filePath
                
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
        var totalBytesExpected = -1L

        // Step 1: Probe file size via Range: bytes=0-0
        try {
            val probeReq = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
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

        Log.d("MUESO_DOWNLOAD", "[PROBE] trackId=$trackId totalSize=${if (totalBytesExpected > 0) "${totalBytesExpected / (1024*1024)}MB ($totalBytesExpected bytes)" else "unknown"}")

        // For small files (< 15MB) or unknown size, download sequentially
        val chunkSize = 5 * 1024 * 1024L // 5MB chunk size per worker
        if (totalBytesExpected <= 15 * 1024 * 1024L) {
            return@withContext downloadSequential(client, url, destFile, trackId, totalBytesExpected, onProgress, maxRetriesPerChunk)
        }

        // Pre-allocate destination file for concurrent random-access writes
        try {
            val raf = java.io.RandomAccessFile(destFile, "rw")
            raf.setLength(totalBytesExpected)
            raf.close()
        } catch (e: Exception) {
            Log.e("MUESO_DOWNLOAD", "Failed to preallocate file of size $totalBytesExpected", e)
            return@withContext downloadSequential(client, url, destFile, trackId, totalBytesExpected, onProgress, maxRetriesPerChunk)
        }

        // Build list of chunks
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
        Log.d("MUESO_DOWNLOAD", "[PARALLEL_START] trackId=$trackId totalSize=${totalBytesExpected / (1024*1024)}MB split into $totalChunks chunks with $concurrency concurrent workers")

        val chunkQueue = kotlinx.coroutines.channels.Channel<Chunk>(totalChunks)
        chunks.forEach { chunkQueue.trySend(it) }
        chunkQueue.close()

        val totalBytesDownloaded = java.util.concurrent.atomic.AtomicLong(0L)
        val startTime = System.currentTimeMillis()
        val hasFailed = java.util.concurrent.atomic.AtomicBoolean(false)

        coroutineScope {
            val workers = (0 until concurrency).map { workerId ->
                async(Dispatchers.IO) {
                    val buffer = ByteArray(64 * 1024)
                    for (chunk in chunkQueue) {
                        if (hasFailed.get()) break

                        var chunkSuccess = false
                        var attempt = 0
                        while (attempt < maxRetriesPerChunk && !chunkSuccess && !hasFailed.get()) {
                            attempt++
                            val callKey = "${trackId}_w${workerId}_c${chunk.index}"
                            val chunkStartTime = System.currentTimeMillis()
                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                .header("Range", "bytes=${chunk.start}-${chunk.end}")
                                .build()

                            try {
                                val call = client.newCall(req)
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
                                var chunkBytes = 0L

                                try {
                                    while (inStream.read(buffer).also { read = it } != -1) {
                                        if (hasFailed.get()) break
                                        chunkRaf.write(buffer, 0, read)
                                        chunkBytes += read
                                        val totalReadSoFar = totalBytesDownloaded.addAndGet(read.toLong())
                                        onProgress(totalReadSoFar, totalBytesExpected)
                                    }
                                    chunkSuccess = true
                                    val durationMs = (System.currentTimeMillis() - chunkStartTime).coerceAtLeast(1)
                                    val speedMBs = (chunkBytes.toDouble() / (1024.0 * 1024.0)) / (durationMs.toDouble() / 1000.0)
                                    Log.d("MUESO_DOWNLOAD", "[WORKER_$workerId] Chunk #${chunk.index}/${totalChunks} (${chunkBytes/(1024*1024)}MB) done in ${durationMs}ms @ ${String.format("%.2f", speedMBs)} MB/s -> Total: ${totalBytesDownloaded.get()/(1024*1024)}MB / ${totalBytesExpected/(1024*1024)}MB")
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
        Log.d("MUESO_DOWNLOAD", "[PARALLEL_DONE] trackId=$trackId finalSize=${finalSize / (1024*1024)}MB in ${totalDurationMs/1000}s -> OVERALL AGGREGATE SPEED: ${String.format("%.2f", avgSpeedMBs)} MB/s")

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
        var totalBytesExpected = totalBytesExpectedInit
        var attempt = 0
        val buffer = ByteArray(64 * 1024)

        while (attempt < maxRetries) {
            attempt++
            val currentBytes = if (destFile.exists()) destFile.length() else 0L
            if (totalBytesExpected > 0 && currentBytes >= totalBytesExpected) {
                return@withContext true
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")

            if (currentBytes > 0) {
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

                val isPartial = (response.code == 206)
                val append = isPartial && currentBytes > 0
                if (!isPartial && currentBytes > 0) {
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

    fun downloadOnlineVideo(
        context: Context,
        track: TrackEntity,
        resolution: String? = null,
        customFolder: String? = null
    ) {
        if (_downloadStates.value[track.id]?.isDownloading == true || _downloadStates.value[track.id]?.isDownloaded == true) return

        lastContext = context.applicationContext
        val targetRes = resolution ?: getDefaultVideoResolution()

        val job = coroutineScope.launch(Dispatchers.IO) {
            _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.01f))
            val currentActiveCount = activeJobs.size
            NotificationHelper.showDownloadProgress(
                context = context.applicationContext,
                trackId = track.id,
                trackTitle = "[Video] ${track.title}",
                artist = track.artist,
                completedCount = 1,
                totalCount = currentActiveCount.coerceAtLeast(1),
                progress = 0.05f
            )

            var tempVideoFile: File? = null
            var tempAudioFile: File? = null
            var tempMuxedFile: File? = null
            var savedDestFile: File? = null

            try {
                val videoId = onlineRepository.extractVideoId(track)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fetching video stream ($targetRes)...", Toast.LENGTH_SHORT).show()
                }

                val videoInfo = onlineRepository.getVideoStreamInfo(videoId, targetRes)
                val downloadUrl = videoInfo?.streamUrl
                val audioUrl = videoInfo?.audioUrl

                if (downloadUrl.isNullOrBlank() || !downloadUrl.startsWith("http")) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Video stream unavailable"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to get video stream for download", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val needsMuxing = !audioUrl.isNullOrBlank()
                val actualRes = videoInfo.resolution
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val videoFile = File(context.cacheDir, "temp_vid_${track.id}.mp4")
                tempVideoFile = videoFile
                activeTempFiles[track.id] = videoFile
                if (videoFile.exists()) videoFile.delete()

                var videoProgress = 0f
                var audioProgress = 0f

                val updateCombinedProgress = {
                    val combinedProg = if (needsMuxing) {
                        (videoProgress * 0.88f + audioProgress * 0.02f).coerceIn(0.01f, 0.90f)
                    } else {
                        videoProgress.coerceIn(0.01f, 0.95f)
                    }
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = combinedProg))
                    NotificationHelper.showDownloadProgress(
                        context = context.applicationContext,
                        trackId = track.id,
                        trackTitle = "[Video] ${track.title} ($actualRes)",
                        artist = track.artist,
                        completedCount = totalDownloadedInBatch + 1,
                        totalCount = activeJobs.size.coerceAtLeast(1),
                        progress = combinedProg
                    )
                }

                // --- Download video and audio in parallel for speed and to prevent stream timeout ---
                var audioFile: File? = null
                val (videoSuccess, audioSuccess) = coroutineScope {
                    val videoDeferred = async(Dispatchers.IO) {
                        downloadStreamWithResume(
                            client = client,
                            url = downloadUrl,
                            destFile = videoFile,
                            trackId = track.id,
                            onProgress = { downloaded, total ->
                                if (total > 0) {
                                    videoProgress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    updateCombinedProgress()
                                }
                            }
                        )
                    }

                    val audioDeferred = if (needsMuxing) {
                        val aFile = File(context.cacheDir, "temp_aud_${track.id}.m4a")
                        tempAudioFile = aFile
                        audioFile = aFile
                        if (aFile.exists()) aFile.delete()

                        async(Dispatchers.IO) {
                            downloadStreamWithResume(
                                client = client,
                                url = audioUrl!!,
                                destFile = aFile,
                                trackId = -track.id,
                                onProgress = { downloaded, total ->
                                    if (total > 0) {
                                        audioProgress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                        updateCombinedProgress()
                                    }
                                }
                            )
                        }
                    } else null

                    Pair(videoDeferred.await(), audioDeferred?.await() ?: true)
                }

                if (!videoSuccess || !videoFile.exists() || videoFile.length() == 0L) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Video download failed"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Video download failed", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // --- Mux video + audio using FFmpeg ---
                if (needsMuxing && audioSuccess && audioFile != null && audioFile!!.exists() && audioFile!!.length() > 0) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.92f))
                    NotificationHelper.showDownloadProgress(
                        context = context.applicationContext,
                        trackId = track.id,
                        trackTitle = "[Video] ${track.title} - Merging audio+video...",
                        artist = track.artist,
                        completedCount = totalDownloadedInBatch + 1,
                        totalCount = activeJobs.size.coerceAtLeast(1),
                        progress = 0.92f
                    )

                    val muxedFile = File(context.cacheDir, "temp_muxed_${track.id}.mp4")
                    tempMuxedFile = muxedFile
                    if (muxedFile.exists()) muxedFile.delete()

                    try {
                        val cmd = "-i \"${videoFile.absolutePath}\" -i \"${audioFile!!.absolutePath}\" -c copy -shortest \"${muxedFile.absolutePath}\""
                        Log.d("MUESO_DOWNLOAD", "FFmpeg mux command: $cmd")
                        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                        val returnCode = session.returnCode

                        if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                            videoFile.delete()
                            audioFile!!.delete()
                            muxedFile.renameTo(videoFile)
                            tempMuxedFile = null
                            Log.d("MUESO_DOWNLOAD", "Successfully muxed video+audio for ${track.title}")
                        } else {
                            Log.w("MUESO_DOWNLOAD", "FFmpeg mux failed (rc=${returnCode}), saving video-only. Output: ${session.output}")
                            try { muxedFile.delete() } catch (_: Exception) {}
                        }
                    } catch (muxErr: Exception) {
                        Log.e("MUESO_DOWNLOAD", "FFmpeg mux error for ${track.title}, saving video-only", muxErr)
                        try { tempMuxedFile?.delete() } catch (_: Exception) {}
                        tempMuxedFile = null
                    }
                }

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.99f))

                val folderSetting = customFolder ?: getVideoDownloadFolder()
                val targetDir = when {
                    folderSetting == "Downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    folderSetting == "DCIM/Mueso" -> {
                        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        File(dcimDir, "Mueso")
                    }
                    folderSetting == "Internal App Storage" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                    folderSetting.startsWith("/") -> File(folderSetting)
                    else -> {
                        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        File(moviesDir, folderSetting.removePrefix("Movies/"))
                    }
                }
                if (!targetDir.exists()) targetDir.mkdirs()

                val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                val ext = ".mp4"
                val destFile = File(targetDir, "$sanitizedTitle ($actualRes)$ext")
                videoFile.copyTo(destFile, overwrite = true)
                videoFile.delete()
                try { tempAudioFile?.delete() } catch (_: Exception) {}
                activeTempFiles.remove(track.id)
                tempVideoFile = null
                tempAudioFile = null
                savedDestFile = destFile

                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                totalDownloadedInBatch++
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved video \"${track.title}\" ($actualRes) to ${targetDir.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                Log.d("MUESO_DOWNLOAD", "Video download cancelled for ${track.title}")
                try { tempVideoFile?.delete() } catch (_: Exception) {}
                try { tempAudioFile?.delete() } catch (_: Exception) {}
                try { tempMuxedFile?.delete() } catch (_: Exception) {}
                activeTempFiles.remove(track.id)?.let { try { it.delete() } catch (_: Exception) {} }
                _downloadStates.value = _downloadStates.value - track.id
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Video download cancelled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MUESO_DOWNLOAD", "Error downloading video ${track.title}", e)
                try { tempVideoFile?.delete() } catch (_: Exception) {}
                try { tempAudioFile?.delete() } catch (_: Exception) {}
                try { tempMuxedFile?.delete() } catch (_: Exception) {}
                activeTempFiles.remove(track.id)?.let { try { it.delete() } catch (_: Exception) {} }
                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = e.message))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Video download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                activeJobs.remove(track.id)
                val prefix = "${track.id}"
                val negPrefix = "-${track.id}"
                activeCalls.keys.filter { it == prefix || it == negPrefix || it.startsWith("${prefix}_") || it.startsWith("${negPrefix}_") }.forEach { activeCalls.remove(it) }
                activeTempFiles.remove(track.id)
                if (activeJobs.isEmpty()) {
                    if (totalDownloadedInBatch > 0) {
                        NotificationHelper.showDownloadComplete(
                            context = context.applicationContext,
                            totalDownloaded = totalDownloadedInBatch.coerceAtLeast(1),
                            lastTitle = "[Video] ${track.title}",
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
}
