package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange = Color(0xFFFF512F)
private val BgDark = Color(0xFF0F0F0F)
private val SurfaceDark = Color(0xFF1A1A2E)

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val tracks by viewModel.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList<TrackEntity>())
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            initialName = playlist.name,
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                showRenameDialog = false
                // Note: The UI won't immediately reflect the new name here unless playlist is updated
                // since `playlist` is passed as a static entity. But the user can see it updated 
                // when going back. Ideally we would observe the playlist object.
                onBack() // Going back is easier to see the updated name immediately in the list
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 68.dp.toPx() }

    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = SurfaceDark,
                    shadowElevation = 8.dp
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = Color.White) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEF5350)) },
                        onClick = {
                            showMenu = false
                            viewModel.deletePlaylist(playlist.id)
                            onBack()
                        }
                    )
                }
            }
        }

        // Play All
        if (tracks.isNotEmpty()) {
            Button(
                onClick = {
                    viewModel.playQueue(tracks)
                    onNavigateToPlayer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All", fontWeight = FontWeight.SemiBold)
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentOrange.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            tint = AccentOrange.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This playlist is empty", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    Text("Add songs from the library", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    val isDragging = draggedIndex == index

                    PlaylistTrackItem(
                        track = track,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffset else 0f,
                        onClick = {
                            viewModel.playQueue(tracks, index)
                            onNavigateToPlayer()
                        },
                        onRemove = { viewModel.removeTrackFromPlaylist(playlist.id, track.id) },
                        dragModifier = Modifier.pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                    if (dragOffset > itemHeightPx * 0.6f && currentIdx < tracks.size - 1) {
                                        viewModel.moveTrackInPlaylist(playlist.id, currentIdx, currentIdx + 1)
                                        draggedIndex = currentIdx + 1
                                        dragOffset -= itemHeightPx
                                    }
                                    if (dragOffset < -itemHeightPx * 0.6f && currentIdx > 0) {
                                        viewModel.moveTrackInPlaylist(playlist.id, currentIdx, currentIdx - 1)
                                        draggedIndex = currentIdx - 1
                                        dragOffset += itemHeightPx
                                    }
                                },
                                onDragEnd = { draggedIndex = null; dragOffset = 0f },
                                onDragCancel = { draggedIndex = null; dragOffset = 0f }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = SurfaceDark,
        title = {
            Text(
                "Rename Playlist",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AccentOrange
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                }
            ) {
                Text("Rename", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

@Composable
private fun PlaylistTrackItem(
    track: TrackEntity,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val minutes = (track.duration / 1000) / 60
    val seconds = (track.duration / 1000) % 60

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
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote, null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("·", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                Text(
                    "%d:%02d".format(minutes, seconds),
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp
                )
            }
        }

        // Drag handle: tap → menu, long press → drag
        Box {
            Box(
                modifier = dragModifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showMenu = true }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Options / Hold to reorder",
                    tint = if (isDragging) AccentOrange else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Menu on tap
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset((-16).dp, 0.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = SurfaceDark,
                shadowElevation = 16.dp
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Remove from Playlist", color = Color(0xFFEF5350))
                        }
                    },
                    onClick = {
                        showMenu = false
                        onRemove()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
