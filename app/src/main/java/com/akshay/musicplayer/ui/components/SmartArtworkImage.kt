package com.akshay.musicplayer.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SmartArtworkImage(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailQuality: String? = null
) {
    val context = LocalContext.current
    val effectiveQuality = thumbnailQuality ?: remember(context) {
        context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            .getString("thumbnail_quality", "Medium (480p)") ?: "Medium (480p)"
    }
    val repo = remember { OnlineMusicRepository() }
    val isOnline = remember(artworkUrl) {
        artworkUrl != null && (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))
    }

    val fallbackList = remember(artworkUrl, effectiveQuality, isOnline) {
        if (isOnline) {
            repo.getYouTubeArtworkFallbackList(artworkUrl, effectiveQuality)
        } else {
            emptyList()
        }
    }
    var currentIndex by remember(artworkUrl) { mutableIntStateOf(0) }

    // For local files or content URIs, extract the embedded thumbnail / picture bytes
    var localArtworkData by remember(artworkUrl) { mutableStateOf<Any?>(null) }
    var isLocalLoading by remember(artworkUrl) { mutableStateOf(!isOnline && !artworkUrl.isNullOrBlank()) }

    LaunchedEffect(artworkUrl, isOnline) {
        if (artworkUrl.isNullOrBlank() || isOnline) {
            localArtworkData = null
            isLocalLoading = false
            return@LaunchedEffect
        }
        isLocalLoading = true
        withContext(Dispatchers.IO) {
            val extracted = extractArtworkData(context, artworkUrl)
            localArtworkData = extracted ?: artworkUrl
            isLocalLoading = false
        }
    }

    val currentUrl = if (isOnline) {
        if (fallbackList.isNotEmpty() && currentIndex in fallbackList.indices) {
            fallbackList[currentIndex]
        } else {
            artworkUrl
        }
    } else {
        null
    }

    val requestModel = if (isOnline) currentUrl else localArtworkData

    val request = remember(requestModel) {
        if (requestModel == null) null else {
            val dataModel: Any = if (requestModel is String && !requestModel.startsWith("http") && !requestModel.startsWith("content://") && !requestModel.startsWith("android.resource://")) {
                File(requestModel)
            } else {
                requestModel
            }
            ImageRequest.Builder(context)
                .data(dataModel)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .crossfade(true)
                .build()
        }
    }

    if (request != null) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            alignment = alignment,
            modifier = modifier,
            contentScale = contentScale,
            onError = {
                if (isOnline && currentIndex < fallbackList.size - 1) {
                    currentIndex++
                }
            }
        )
    }
}

private fun extractArtworkData(context: Context, rawUriOrPath: String): Any? {
    try {
        // 1. Content URI (e.g. content://media/external/audio/media/123)
        if (rawUriOrPath.startsWith("content://")) {
            val uri = Uri.parse(rawUriOrPath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rawUriOrPath.contains("audio/media")) {
                try {
                    val bm = context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
                    if (bm != null) return bm
                } catch (e: Exception) {
                    Log.d("MUESO_ARTWORK", "extractArtworkData: loadThumbnail failed on contentUri: ${e.message}")
                }
            }
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val rawBytes = retriever.embeddedPicture
                retriever.release()
                if (rawBytes != null) return rawBytes
            } catch (e: Exception) {
                Log.d("MUESO_ARTWORK", "extractArtworkData: MediaMetadataRetriever failed on contentUri: ${e.message}")
            }
        }

        // 2. Local audio file path (e.g. /storage/emulated/0/Music/song.mp3 or file:///storage/...)
        val filePath = if (rawUriOrPath.startsWith("file://")) rawUriOrPath.removePrefix("file://") else rawUriOrPath
        val file = File(filePath)
        if (file.exists()) {
            val ext = file.extension.lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "webp", "bmp")) {
                return file
            }
            // Audio file with embedded ID3 art
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val rawBytes = retriever.embeddedPicture
                retriever.release()
                if (rawBytes != null) return rawBytes
            } catch (e: Exception) {
                Log.d("MUESO_ARTWORK", "extractArtworkData: MediaMetadataRetriever failed on file: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.w("MUESO_ARTWORK", "extractArtworkData: unexpected error for $rawUriOrPath", e)
    }
    return null
}
