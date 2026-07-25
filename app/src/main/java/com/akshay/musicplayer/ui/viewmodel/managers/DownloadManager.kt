package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadManager(
    private val onlineRepository: OnlineMusicRepository,
    private val getDownloadFolder: () -> String,
    private val coroutineScope: CoroutineScope
) {
    private val _downloadStates = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadProgress>> = _downloadStates.asStateFlow()

    fun downloadOnlineTrack(context: Context, track: TrackEntity) {
        if (_downloadStates.value[track.id]?.isDownloading == true || _downloadStates.value[track.id]?.isDownloaded == true) return

        coroutineScope.launch(Dispatchers.IO) {
            _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.01f))
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

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "HTTP error ${response.code}"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed with HTTP ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val contentLength = body.contentLength()
                val ext = if (downloadUrl.contains("mime=audio%2Fmp4") || downloadUrl.contains(".m4a") || downloadUrl.contains("mime=video%2Fmp4")) ".m4a" else ".mp3"
                val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                
                val tempFile = File(context.cacheDir, "temp_dl_${track.id}$ext")
                if (tempFile.exists()) tempFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val prog = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0.01f, 0.95f)
                        _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = prog))
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.96f))
                onlineRepository.embedMetadata(tempFile.absolutePath, track.title, track.artist, "Mueso Downloads", track.artworkUrl)

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
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()

                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved \"${track.title}\" to ${targetDir.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MUESO_DOWNLOAD", "Error downloading track ${track.title}", e)
                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = e.message))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
