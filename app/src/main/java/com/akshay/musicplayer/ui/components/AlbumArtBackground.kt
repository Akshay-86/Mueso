package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AlbumArtBackground(
    albumArtUri: String?,
    contentDescription: String = "Album Art",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageModel = remember(albumArtUri) {
        val raw = albumArtUri ?: ""
        val fallback = if (raw.contains("maxresdefault.jpg")) {
            raw.replace("maxresdefault.jpg", "hqdefault.jpg")
        } else if (raw.contains("sddefault.jpg")) {
            raw.replace("sddefault.jpg", "hqdefault.jpg")
        } else if (raw.contains("hq720.jpg")) {
            raw.replace("hq720.jpg", "hqdefault.jpg")
        } else raw

        ImageRequest.Builder(context)
            .data(if (raw.isNotBlank()) raw else fallback)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!albumArtUri.isNullOrEmpty()) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )
    }
}
