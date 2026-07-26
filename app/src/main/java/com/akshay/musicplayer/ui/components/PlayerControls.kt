package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.ui.state.PlaybackState

@Composable
fun PlayerControls(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    isResolvingTrack: Boolean = false,
    playButtonPosition: String = "Left",
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { androidx.compose.runtime.mutableStateOf(false) }
    var sliderPosition by remember(playbackState.currentPositionMs) {
        isSeeking = false
        mutableFloatStateOf(playbackState.currentPositionMs.toFloat())
    }

    val showLoading = isSeeking || isResolvingTrack

    val playButton: @Composable () -> Unit = {
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(48.dp)
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color(0xFFFF512F),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }
    }

    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        Slider(
            value = sliderPosition,
            onValueChange = {
                isSeeking = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
            },
            valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
            modifier = sliderModifier,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when (playButtonPosition) {
            "Right" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    slider(Modifier.weight(1f))
                    playButton()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 60.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(playbackState.currentPositionMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(playbackState.durationMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                }
            }
            "Center" -> {
                slider(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(playbackState.currentPositionMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(playbackState.durationMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    playButton()
                }
            }
            else -> { // "Left" (Default)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    playButton()
                    slider(Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(playbackState.currentPositionMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(playbackState.durationMs), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun formatTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
