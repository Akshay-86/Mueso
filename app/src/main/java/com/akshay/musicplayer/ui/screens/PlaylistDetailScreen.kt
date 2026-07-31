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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
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
private val SurfaceDark = Color(0xFF1A1A2E)

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val tracks by viewModel.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList<TrackEntity>())
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val dropdownBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)

    if (showRenameDialog) {
        RenamePlaylistDialog(
            initialName = playlist.name,
            isDarkMode = isDarkMode,
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                showRenameDialog = false
                onBack()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    // Drag state
    var displayTracks by remember(tracks) { mutableStateOf(tracks) }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 68.dp.toPx() }

    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                    color = textSecondary,
                    fontSize = 13.sp
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = textPrimary
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = dropdownBg,
                    shadowElevation = 8.dp
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = textPrimary) },
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

        // Play All & Add to Queue
        if (tracks.isNotEmpty()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.touchPlaylist(playlist.id)
                        viewModel.playQueue(tracks)
                        onNavigateToPlayer()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        viewModel.addTracksToQueue(tracks)
                        android.widget.Toast.makeText(context, "Added ${tracks.size} track(s) to Queue", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        contentColor = textPrimary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Queue", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
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
                            .background(AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            tint = AccentOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This playlist is empty", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add songs from the library", color = textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(displayTracks, key = { _, track -> track.id }) { index, track ->
                    val isDragging = draggedIndex == index

                    PlaylistTrackItem(
                        track = track,
                        isDarkMode = isDarkMode,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffset else 0f,
                        onClick = {
                            viewModel.touchPlaylist(playlist.id)
                            viewModel.playQueue(displayTracks, index)
                            onNavigateToPlayer()
                        },
                        onRemove = { viewModel.removeTrackFromPlaylist(playlist.id, track.id) },
                        dragModifier = Modifier.pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    initialDragIndex = index
                                    draggedIndex = index
                                    dragOffset = 0f
                                    android.util.Log.d("MUESO_DRAG", "[LocalPlaylist] Started dragging '${track.title}' from index $index")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    var current = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                    while (dragOffset > itemHeightPx * 0.5f && current < displayTracks.size - 1) {
                                        val next = current + 1
                                        val list = displayTracks.toMutableList()
                                        val item = list.removeAt(current)
                                        list.add(next, item)
                                        displayTracks = list
                                        current = next
                                        draggedIndex = next
                                        dragOffset -= itemHeightPx
                                        android.util.Log.d("MUESO_DRAG", "[LocalPlaylist] Locally moved '${track.title}' DOWN to index $next")
                                    }
                                    while (dragOffset < -itemHeightPx * 0.5f && current > 0) {
                                        val prev = current - 1
                                        val list = displayTracks.toMutableList()
                                        val item = list.removeAt(current)
                                        list.add(prev, item)
                                        displayTracks = list
                                        current = prev
                                        draggedIndex = prev
                                        dragOffset += itemHeightPx
                                        android.util.Log.d("MUESO_DRAG", "[LocalPlaylist] Locally moved '${track.title}' UP to index $prev")
                                    }
                                },
                                onDragEnd = {
                                    val start = initialDragIndex
                                    val finalIdx = draggedIndex
                                    if (start != null && finalIdx != null && start != finalIdx) {
                                        viewModel.moveTrackInPlaylist(playlist.id, start, finalIdx)
                                        android.util.Log.d("MUESO_DRAG", "[LocalPlaylist] Drag finished! Committed move from $start -> $finalIdx")
                                    }
                                    draggedIndex = null
                                    initialDragIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    val start = initialDragIndex
                                    val finalIdx = draggedIndex
                                    if (start != null && finalIdx != null && start != finalIdx) {
                                        viewModel.moveTrackInPlaylist(playlist.id, start, finalIdx)
                                        android.util.Log.d("MUESO_DRAG", "[LocalPlaylist] Drag end/cancel! Committed move from $start -> $finalIdx")
                                    }
                                    draggedIndex = null
                                    initialDragIndex = null
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
private fun RenamePlaylistDialog(
    initialName: String,
    isDarkMode: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val dialogBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Text(
                "Rename Playlist",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Playlist name", color = textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
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
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

@Composable
private fun PlaylistTrackItem(
    track: TrackEntity,
    isDarkMode: Boolean = true,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val minutes = (track.duration / 1000) / 60
    val seconds = (track.duration / 1000) % 60

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.45f) else Color(0xFF6E6E73)
    val iconTint = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF3A3A3C)
    val dropdownBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)

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
            .then(dragModifier)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDarkMode)
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
                    else
                        Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.03f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote, null,
                tint = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = textPrimary,
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
                    color = textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("·", color = textSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
                Text(
                    "%d:%02d".format(minutes, seconds),
                    color = textSecondary.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }

        // Options menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = iconTint
                )
            }

            // Menu on tap
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset((-16).dp, 0.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = dropdownBg,
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
