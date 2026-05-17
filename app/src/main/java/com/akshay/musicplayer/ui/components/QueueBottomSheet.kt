package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.akshay.musicplayer.domain.models.TrackEntity

private val AccentOrange = Color(0xFFFF512F)
private val SurfaceDark = Color(0xFF1A1A2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    onTrackClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Only show tracks AFTER the currently playing one (upcoming queue)
    val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }
    val upcomingStartIndex = if (currentIndex >= 0) currentIndex + 1 else 0
    val upcomingTracks = if (upcomingStartIndex < tracks.size) {
        tracks.subList(upcomingStartIndex, tracks.size)
    } else {
        emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
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
                    text = "Up Next • ${upcomingTracks.size} tracks",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        if (upcomingTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No more tracks in queue",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp
                )
            }
        } else {
            var draggedIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val itemHeightPx = with(LocalDensity.current) { 64.dp.toPx() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                itemsIndexed(upcomingTracks, key = { _, track -> track.id }) { localIndex, track ->
                    val isDragging = draggedIndex == localIndex
                    // Map local index back to full tracks list index
                    val globalIndex = upcomingStartIndex + localIndex

                    QueueTrackItem(
                        track = track,
                        isPlaying = false,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffset else 0f,
                        onClick = { onTrackClick(globalIndex) },
                        dragModifier = Modifier.pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = localIndex
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val currentDragIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    val globalFrom = upcomingStartIndex + currentDragIdx

                                    // Swap down
                                    if (dragOffset > itemHeightPx * 0.6f && currentDragIdx < upcomingTracks.size - 1) {
                                        onMove(globalFrom, globalFrom + 1)
                                        draggedIndex = currentDragIdx + 1
                                        dragOffset -= itemHeightPx
                                    }
                                    // Swap up
                                    if (dragOffset < -itemHeightPx * 0.6f && currentDragIdx > 0) {
                                        onMove(globalFrom, globalFrom - 1)
                                        draggedIndex = currentDragIdx - 1
                                        dragOffset += itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTrackItem(
    track: TrackEntity,
    isPlaying: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    dragModifier: Modifier
) {
    val bgColor = when {
        isDragging -> Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
        )
        isPlaying -> Brush.horizontalGradient(
            listOf(Color(0x33FF512F), Color(0x33DD2476))
        )
        else -> Brush.horizontalGradient(
            listOf(Color.Transparent, Color.Transparent)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
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

        val minutes = (track.duration / 1000) / 60
        val seconds = (track.duration / 1000) % 60
        Text(
            text = "%d:%02d".format(minutes, seconds),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )

        // Drag handle — long press + drag to reorder
        Box(
            modifier = dragModifier
                .size(40.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Hold to reorder",
                tint = if (isDragging) AccentOrange else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
