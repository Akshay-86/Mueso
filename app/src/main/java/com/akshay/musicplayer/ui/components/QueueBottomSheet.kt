package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    onTrackClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Queue • ${tracks.size} tracks",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            itemsIndexed(tracks) { index, track ->
                val isCurrentTrack = track.id == currentTrackId
                QueueTrackItem(
                    track = track,
                    isPlaying = isCurrentTrack,
                    onClick = { onTrackClick(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueTrackItem(
    track: TrackEntity,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isPlaying) {
        Brush.horizontalGradient(
            listOf(Color(0x33FF512F), Color(0x33DD2476))
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color.Transparent, Color.Transparent)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPlaying) Color(0xFFFF512F).copy(alpha = 0.3f)
                    else Color.White.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) Color(0xFFFF512F) else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) Color(0xFFFF512F) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        val minutes = (track.duration / 1000) / 60
        val seconds = (track.duration / 1000) % 60
        Text(
            text = "%d:%02d".format(minutes, seconds),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}
