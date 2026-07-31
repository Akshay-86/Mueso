package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    isPlaylistContext: Boolean = false,
    playlistTrackCount: Int = 0,
    isDarkMode: Boolean = true,
    onTrackClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetBg = if (isDarkMode) Color(0xFF1A1A2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

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
        containerColor = sheetBg,
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
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.width(70.dp))

                    Text(
                        text = "Upcoming Queue",
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (upcomingTracks.isNotEmpty()) {
                        TextButton(
                            onClick = onClearQueue,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Queue",
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = AccentOrange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(modifier = Modifier.width(70.dp))
                    }
                }
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
                    color = textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            var localUpcomingTracks by remember(upcomingTracks) { mutableStateOf(upcomingTracks) }
            var initialDragLocalIndex by remember { mutableStateOf<Int?>(null) }
            var draggedIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val itemHeightPx = with(LocalDensity.current) { 64.dp.toPx() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                itemsIndexed(localUpcomingTracks, key = { _, track -> track.id }) { localIndex, track ->
                    val isDragging = draggedIndex == localIndex
                    // Map local index back to full tracks list index
                    val globalIndex = upcomingStartIndex + localIndex

                    Column {
                        QueueTrackItem(
                            track = track,
                            isPlaying = false,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffset else 0f,
                            isDarkMode = isDarkMode,
                            onClick = { onTrackClick(globalIndex) },
                            dragModifier = Modifier.pointerInput(track.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        initialDragLocalIndex = localIndex
                                        draggedIndex = localIndex
                                        dragOffset = 0f
                                        android.util.Log.d("MUESO_DRAG", "[Queue] Started dragging '${track.title}' from local index $localIndex (global $globalIndex)")
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        var current = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                        while (dragOffset > itemHeightPx * 0.5f && current < localUpcomingTracks.size - 1) {
                                            val next = current + 1
                                            val list = localUpcomingTracks.toMutableList()
                                            val item = list.removeAt(current)
                                            list.add(next, item)
                                            localUpcomingTracks = list
                                            current = next
                                            draggedIndex = next
                                            dragOffset -= itemHeightPx
                                            android.util.Log.d("MUESO_DRAG", "[Queue] Locally moved '${track.title}' DOWN to index $next")
                                        }
                                        while (dragOffset < -itemHeightPx * 0.5f && current > 0) {
                                            val prev = current - 1
                                            val list = localUpcomingTracks.toMutableList()
                                            val item = list.removeAt(current)
                                            list.add(prev, item)
                                            localUpcomingTracks = list
                                            current = prev
                                            draggedIndex = prev
                                            dragOffset += itemHeightPx
                                            android.util.Log.d("MUESO_DRAG", "[Queue] Locally moved '${track.title}' UP to index $prev")
                                        }
                                    },
                                    onDragEnd = {
                                        val startLocal = initialDragLocalIndex
                                        val endLocal = draggedIndex
                                        if (startLocal != null && endLocal != null && startLocal != endLocal) {
                                            val globalFrom = upcomingStartIndex + startLocal
                                            val globalTo = upcomingStartIndex + endLocal
                                            onMove(globalFrom, globalTo)
                                            android.util.Log.d("MUESO_DRAG", "[Queue] Drag finished! Committed queue move from global index $globalFrom -> $globalTo")
                                        }
                                        draggedIndex = null
                                        initialDragLocalIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        val startLocal = initialDragLocalIndex
                                        val endLocal = draggedIndex
                                        if (startLocal != null && endLocal != null && startLocal != endLocal) {
                                            val globalFrom = upcomingStartIndex + startLocal
                                            val globalTo = upcomingStartIndex + endLocal
                                            onMove(globalFrom, globalTo)
                                            android.util.Log.d("MUESO_DRAG", "[Queue] Drag end/cancel! Committed queue move from global index $globalFrom -> $globalTo")
                                        }
                                        draggedIndex = null
                                        initialDragLocalIndex = null
                                        dragOffset = 0f
                                    }
                                )
                            }
                        )

                        // Divider after the last song of playlist
                        if (isPlaylistContext && playlistTrackCount > 0 && globalIndex == playlistTrackCount - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF512F).copy(alpha = 0.25f),
                                                Color(0xFF8E2DE2).copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "END OF PLAYLIST  •  RADIO RECOMMENDATIONS NEXT",
                                    color = AccentOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
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
    isDarkMode: Boolean = true,
    onClick: () -> Unit,
    dragModifier: Modifier
) {
    val bgColor = when {
        isDragging -> Brush.horizontalGradient(
            listOf(
                if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
            )
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
            .then(dragModifier)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val artModel = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}"
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            SmartArtworkImage(
                artworkUrl = artModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }


        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val minutes = (track.duration / 1000) / 60
        val seconds = (track.duration / 1000) % 60
        Text(
            text = "%d:%02d".format(minutes, seconds),
            color = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73),
            fontSize = 12.sp
        )

    }
}
