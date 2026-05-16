package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun AlbumArtBackground(
    albumArtUri: String?,
    contentDescription: String = "Album Art",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Sharp full-screen album art background
        if (!albumArtUri.isNullOrEmpty()) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Deepening gradient overlay for high-end look and readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f), // Darker at top for lyrics
                            Color.Black.copy(alpha = 0.2f), // Clearer in the middle for the artwork
                            Color.Black.copy(alpha = 0.6f), // Darkening for info section
                            Color.Black.copy(alpha = 0.9f)  // Very dark at the bottom for controls
                        )
                    )
                )
        )
    }
}
