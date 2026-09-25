@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.domain.models.ActiveAudioFormat
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack

@Composable
fun DockedMiniPlayer(
    track: TrackEntity?,
    playbackState: PlaybackState,
    audioFormat: ActiveAudioFormat? = null,
    isDarkMode: Boolean = true,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val isPureBlack = LocalIsPureBlack.current
    val bgColor = if (isPureBlack) Color(0xFF0F0F0F) else if (isDarkMode) Color(0xFF1E1E2C) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)

    val progress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val miniPlayerShape = RoundedCornerShape(18.dp)
    val boxModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(miniPlayerShape)
        .background(bgColor)
        .clickable(onClick = onExpandClick)

    Box(
        modifier = boxModifier
    ) {
        Column {
            // Tiny progress indicator line on top
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Artwork thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl ?: android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), track.albumId))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkMode) Color.DarkGray else Color.LightGray)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Title, Artist, and Mini Quality Badge
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.artist,
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Quality badge
                        if (audioFormat != null) {
                            val badgeColor = when {
                                audioFormat.isHiRes -> Color(0xFFFFB300)
                                audioFormat.isLossless -> Color(0xFF00E5FF)
                                else -> accent
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (audioFormat.isLossless) "FLAC" else "HQ",
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause & Next Buttons

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f))
                    ) {
                        if (playbackState.isBuffering && playbackState.isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
