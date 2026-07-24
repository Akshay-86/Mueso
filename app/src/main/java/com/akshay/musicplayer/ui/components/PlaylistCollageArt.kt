package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akshay.musicplayer.domain.models.TrackEntity

@Composable
fun PlaylistCollageArt(
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    fallbackGradient: List<Color> = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    thumbnailQuality: String = "Highest (1080p Maxres)"
) {
    val artworkUrls = tracks.mapNotNull { track ->
        track.artworkUrl ?: if (track.albumId > 0) "content://media/external/audio/albumart/${track.albumId}" else null
    }.take(4)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(fallbackGradient)),
        contentAlignment = Alignment.Center
    ) {
        when {
            artworkUrls.size >= 4 -> {
                // Spotify-style 2x2 Grid Collage
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        SmartArtworkImage(
                            artworkUrl = artworkUrls[0],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = thumbnailQuality
                        )
                        SmartArtworkImage(
                            artworkUrl = artworkUrls[1],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = thumbnailQuality
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        SmartArtworkImage(
                            artworkUrl = artworkUrls[2],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = thumbnailQuality
                        )
                        SmartArtworkImage(
                            artworkUrl = artworkUrls[3],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = thumbnailQuality
                        )
                    }
                }
            }
            artworkUrls.isNotEmpty() -> {
                // Single artwork cover
                SmartArtworkImage(
                    artworkUrl = artworkUrls.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnailQuality = thumbnailQuality
                )
            }
            else -> {
                // Default gradient playlist icon
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
