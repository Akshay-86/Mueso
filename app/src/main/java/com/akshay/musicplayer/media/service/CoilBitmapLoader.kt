package com.akshay.musicplayer.media.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import coil.ImageLoader
import coil.request.ImageRequest
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CoilBitmapLoader(private val context: Context) : BitmapLoader {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val repo = OnlineMusicRepository()
    private val imageLoader = ImageLoader.Builder(context)
        .crossfade(false)
        .allowHardware(false) // CRITICAL: System Notifications and MediaSession do not support hardware bitmaps
        .build()

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Failed to decode bitmap from byte array (${data.size} bytes)"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return loadBitmap(uri, null)
    }

    override fun loadBitmap(uri: Uri, options: BitmapFactory.Options?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bitmap = loadBitmapInternal(uri)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Failed to load bitmap from uri: $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }
        if (metadata.artworkUri != null) {
            return loadBitmap(metadata.artworkUri!!)
        }
        return null
    }

    private suspend fun loadBitmapInternal(uri: Uri): Bitmap? {
        val uriString = uri.toString()

        // 1. If it's a web URL (e.g. YouTube CDN, Google usercontent, Spotify CDN)
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            val candidates = repo.getYouTubeArtworkFallbackList(uriString)
            for (candidate in candidates) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(candidate)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    val bm = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bm != null) return bm
                } catch (_: Exception) {}
            }
        }

        // 2. If it's a MediaStore content URI (e.g. content://media/external/audio/media/123)
        if (uri.scheme == "content") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uriString.contains("audio/media")) {
                try {
                    val bm = context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
                    return bm
                } catch (_: Exception) {}
            }
            // Try extracting embedded picture via MediaMetadataRetriever
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val rawBytes = retriever.embeddedPicture
                retriever.release()
                if (rawBytes != null) {
                    val bm = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    if (bm != null) return bm
                }
            } catch (_: Exception) {}
        }

        // 3. If it's a local audio file or file URI (e.g. /storage/... or file://...)
        val filePath = if (uri.scheme == "file") uri.path else if (!uriString.contains("://")) uriString else null
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                // Try extracting embedded audio metadata
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val rawBytes = retriever.embeddedPicture
                    retriever.release()
                    if (rawBytes != null) {
                        val bm = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        if (bm != null) return bm
                    }
                } catch (_: Exception) {}

                // Try decoding directly as image file
                try {
                    val bm = BitmapFactory.decodeFile(file.absolutePath)
                    if (bm != null) return bm
                } catch (_: Exception) {}
            }
        }

        // 4. Default Coil fallback for any other URI scheme
        return try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }
}
