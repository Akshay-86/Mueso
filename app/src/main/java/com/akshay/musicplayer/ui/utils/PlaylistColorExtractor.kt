package com.akshay.musicplayer.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistColorExtractor {

    val DeterministicPalettes = listOf(
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), // Royal Purple -> Deep Violet
        listOf(Color(0xFFFF512F), Color(0xFFDD2476)), // Sunset Orange -> Berry Pink
        listOf(Color(0xFF00B4DB), Color(0xFF0083B0)), // Cyan Blue -> Deep Ocean
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)), // Emerald -> Mint Green
        listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)), // Coral Red -> Flame Orange
        listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)), // Lavender -> Soft Rose
        listOf(Color(0xFFF2994A), Color(0xFFF2C94C)), // Amber Orange -> Warm Yellow
        listOf(Color(0xFF3A6073), Color(0xFF16222F)), // Steel Blue -> Midnight Slate
        listOf(Color(0xFF7F00FF), Color(0xFFE100FF)), // Electric Violet -> Neon Pink
        listOf(Color(0xFF134E5E), Color(0xFF71B280)), // Deep Teal -> Sage Green
        listOf(Color(0xFFD31027), Color(0xFFEA384D)), // Crimson Flame -> Ruby Red
        listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)), // Dark Slate -> Aqua Blue
        listOf(Color(0xFF614385), Color(0xFF516395)), // Deep Plum -> Twilight Blue
        listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)), // Peach -> Apricot
        listOf(Color(0xFF0F2027), Color(0xFF203A43)), // Charcoal -> Dark Pine
        listOf(Color(0xFFE55D87), Color(0xFF5FC3E4))  // Magenta Rose -> Sky Cyan
    )

    fun getDeterministicGradient(seed: String): List<Color> {
        val index = kotlin.math.abs(seed.hashCode()) % DeterministicPalettes.size
        return DeterministicPalettes[index]
    }

    suspend fun extractGradientColors(context: Context, tracks: List<TrackEntity>): List<Color>? = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext null

        // Find the first track with available artwork (checking up to first 4 tracks)
        for (track in tracks.take(4)) {
            val bitmap = loadThumbnailBitmap(context, track)
            if (bitmap != null) {
                val extracted = extractColorsFromBitmap(bitmap)
                if (extracted != null) return@withContext extracted
            }
        }
        null
    }

    private suspend fun loadThumbnailBitmap(context: Context, track: TrackEntity): Bitmap? {
        val artworkUrl = track.artworkUrl
        val isOnline = !artworkUrl.isNullOrBlank() && (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))

        if (isOnline) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(64, 64)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bm = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bm != null) return bm
                }
            } catch (_: Exception) {}
        }

        // Online track videoId thumbnail fallback
        if (track.filePath.startsWith("online:")) {
            val videoId = track.filePath.removePrefix("online:")
            if (videoId.isNotBlank()) {
                val ytThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                try {
                    val request = ImageRequest.Builder(context)
                        .data(ytThumb)
                        .size(64, 64)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bm = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bm != null) return bm
                    }
                } catch (_: Exception) {}
            }
        }

        // Local content URI via albumId
        if (track.albumId > 0) {
            try {
                val albumUri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
                val request = ImageRequest.Builder(context)
                    .data(albumUri)
                    .size(64, 64)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bm = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bm != null) return bm
                }
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val contentUri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
                    val bm = context.contentResolver.loadThumbnail(contentUri, Size(64, 64), null)
                    if (bm != null) return bm
                } catch (_: Exception) {}
            }
        }

        // Local file path (embedded picture)
        if (track.filePath.isNotBlank() && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http")) {
            val path = if (track.filePath.startsWith("file://")) track.filePath.removePrefix("file://") else track.filePath
            val file = File(path)
            if (file.exists()) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val rawBytes = retriever.embeddedPicture
                    retriever.release()
                    if (rawBytes != null) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        return BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun extractColorsFromBitmap(bitmap: Bitmap): List<Color>? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val stepX = maxOf(1, width / 16)
        val stepY = maxOf(1, height / 16)

        val scoredColors = mutableListOf<Pair<Int, Float>>()
        val hsv = FloatArray(3)

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha < 128) continue

                android.graphics.Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // Filter out near-blacks, near-whites, and dull grays
                if (value < 0.15f || value > 0.92f) continue
                if (sat < 0.18f) continue

                // Score favoring vibrant colors with moderate lightness
                val score = sat * 2.0f + (1f - kotlin.math.abs(value - 0.55f))
                scoredColors.add(pixel to score)
            }
        }

        if (scoredColors.isEmpty()) return null

        val sorted = scoredColors.sortedByDescending { it.second }
        val primary = sorted.first().first

        val primaryHsv = FloatArray(3)
        android.graphics.Color.colorToHSV(primary, primaryHsv)

        val secondary = sorted.firstOrNull { (color, _) ->
            val candidateHsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, candidateHsv)
            val hueDiff = kotlin.math.abs(candidateHsv[0] - primaryHsv[0])
            val circularDiff = minOf(hueDiff, 360f - hueDiff)
            circularDiff >= 30f
        }?.first ?: run {
            val darkHsv = floatArrayOf(
                primaryHsv[0],
                (primaryHsv[1] * 1.15f).coerceIn(0f, 1f),
                (primaryHsv[2] * 0.50f).coerceIn(0.12f, 0.9f)
            )
            android.graphics.Color.HSVToColor(darkHsv)
        }

        return listOf(Color(primary), Color(secondary))
    }
}

@Composable
fun rememberPlaylistGradient(
    tracks: List<TrackEntity>,
    title: String,
    fallbackGradient: List<Color>? = null
): List<Color> {
    val context = LocalContext.current
    val initial = remember(title, fallbackGradient) {
        fallbackGradient ?: PlaylistColorExtractor.getDeterministicGradient(title)
    }
    var currentGradient by remember(title) { mutableStateOf(initial) }

    LaunchedEffect(tracks, title) {
        if (tracks.isEmpty()) {
            currentGradient = fallbackGradient ?: PlaylistColorExtractor.getDeterministicGradient(title)
            return@LaunchedEffect
        }
        val extracted = PlaylistColorExtractor.extractGradientColors(context, tracks)
        if (extracted != null && extracted.size >= 2) {
            currentGradient = extracted
        }
    }

    val animatedColor1 by animateColorAsState(
        targetValue = currentGradient.getOrElse(0) { initial[0] },
        animationSpec = tween(durationMillis = 400),
        label = "grad1"
    )
    val animatedColor2 by animateColorAsState(
        targetValue = currentGradient.getOrElse(1) { initial[1] },
        animationSpec = tween(durationMillis = 400),
        label = "grad2"
    )

    return listOf(animatedColor1, animatedColor2)
}
