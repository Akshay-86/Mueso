package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.akshay.musicplayer.ui.state.PlaybackState

@Composable
fun PlayerControls(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(playbackState.currentPositionMs) {
        mutableFloatStateOf(playbackState.currentPositionMs.toFloat())
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play/Pause on the left
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Slider (Seekbar) taking the rest of the space
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
            },
            valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
    }
}

private fun formatTime(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
