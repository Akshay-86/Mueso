package com.akshay.musicplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.data.remote.OnlineMusicRepository

@Composable
fun SmartArtworkImage(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailQuality: String = "Highest (1080p Maxres)"
) {
    val context = LocalContext.current
    val repo = remember { OnlineMusicRepository() }
    val fallbackList = remember(artworkUrl, thumbnailQuality) {
        repo.getYouTubeArtworkFallbackList(artworkUrl, thumbnailQuality)
    }
    var currentIndex by remember(artworkUrl) { mutableIntStateOf(0) }

    val currentUrl = if (fallbackList.isNotEmpty() && currentIndex in fallbackList.indices) {
        fallbackList[currentIndex]
    } else {
        artworkUrl
    }

    val request = remember(currentUrl) {
        ImageRequest.Builder(context)
            .data(currentUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        alignment = alignment,
        modifier = modifier,
        contentScale = contentScale,
        onError = {
            if (currentIndex < fallbackList.size - 1) {
                currentIndex++
            }
        }
    )
}
