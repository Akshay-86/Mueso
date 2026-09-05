package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.akshay.musicplayer.ui.viewmodel.DownloadProgress
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import com.akshay.musicplayer.ui.components.AddToPlaylistBottomSheet

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)
private val TextSecondary = Color(0xFF8E8E93)

@Composable
fun OnlinePlaylistDetailScreen(
    title: String,
    subtitle: String?,
    description: String? = null,
    gradientColors: List<Color>,
    tracks: List<TrackEntity>,
    isLoading: Boolean,
    isCustomUserPlaylist: Boolean = false,
    isDarkMode: Boolean = true,
    viewModel: PlayerViewModel? = null,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: (() -> Unit)? = null,
    onPlayNextClick: (() -> Unit)? = null,
    onAddToQueueClick: (() -> Unit)? = null,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: ((TrackEntity) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onDownloadTrack: ((TrackEntity) -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    onRenamePlaylist: ((String) -> Unit)? = null,
    onEditPlaylistDetails: ((name: String, description: String) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)
    val context = LocalContext.current
    var displayTracks by remember(tracks) { mutableStateOf(tracks) }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 68.dp.toPx() }

    val downloadStates by viewModel?.downloadStates?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }
    val activeDownloads = remember(downloadStates, displayTracks) {
        displayTracks.mapNotNull { track -> downloadStates[track.id] }.filter { it.isDownloading }
    }

    val filteredTracks = remember(searchQuery, displayTracks) {
        if (searchQuery.isBlank()) displayTracks
        else {
            val q = searchQuery.trim().lowercase()
            displayTracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    val totalDurationMs = remember(tracks) { tracks.sumOf { it.duration } }
    val durationFormatted = remember(totalDurationMs) {
        val totalSec = totalDurationMs / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        when {
            hours > 0 && mins > 0 -> "${hours} hr ${mins} min"
            hours > 0 -> "${hours} hr"
            mins > 0 -> "${mins} min"
            else -> "${totalSec} sec"
        }
    }

    if (showAddToPlaylist && viewModel != null) {
        AddToPlaylistBottomSheet(
            tracks = displayTracks,
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showRenameDialog && (onEditPlaylistDetails != null || onRenamePlaylist != null)) {
        EditPlaylistDetailsDialog(
            initialName = title,
            initialDescription = description ?: "",
            isDarkMode = isDarkMode,
            onConfirm = { newName, newDesc ->
                if (onEditPlaylistDetails != null) {
                    onEditPlaylistDetails(newName, newDesc)
                } else if (onRenamePlaylist != null) {
                    onRenamePlaylist(newName)
                }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirmDialog && onDeletePlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"$title\"?", color = textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Hero Banner Gradient starting from y=0
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    gradientColors.firstOrNull() ?: AccentOrange,
                                    gradientColors.lastOrNull() ?: Color(0xFF1A1A2E),
                                    if (isDarkMode) BgDark else MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(bottom = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header with Back Button, Title, Search Toggle, and 3-Dots Menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            // Search button
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search in playlist",
                                    tint = if (showSearch || searchQuery.isNotEmpty()) AccentOrange else Color.White
                                )
                            }

                            // Top Bar 3-dots Menu for Playlist Management / Library / Share
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Playlist Management",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color.White,
                                    shadowElevation = 16.dp
                                ) {
                                    if (onEditPlaylistDetails != null || onRenamePlaylist != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                                    Text("Edit playlist", color = textPrimary)
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                showRenameDialog = true
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                                Text(if (isCustomUserPlaylist) "Add to another playlist" else "Save to My Music", color = textPrimary)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            showAddToPlaylist = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Share, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                                Text("Share playlist", color = textPrimary)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            val shareText = buildString {
                                                append("Check out \"$title\" playlist on Mueso:\n")
                                                displayTracks.take(15).forEachIndexed { i, t ->
                                                    append("${i + 1}. ${t.title} - ${t.artist}\n")
                                                }
                                                if (displayTracks.size > 15) append("... and ${displayTracks.size - 15} more songs")
                                            }
                                            val sendIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Playlist"))
                                        }
                                    )

                                    if (onDeletePlaylist != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                                                    Text("Delete playlist", color = Color(0xFFFF453A))
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                showDeleteConfirmDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Search Bar if toggled
                        AnimatedVisibility(
                            visible = showSearch,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Find in playlist...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                                )
                            )
                        }

                        // Centered Hero 2x2 Collage Cover Art Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            com.akshay.musicplayer.ui.components.PlaylistCollageArt(
                                tracks = tracks,
                                modifier = Modifier.size(160.dp),
                                cornerRadius = 20.dp,
                                fallbackGradient = gradientColors
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!description.isNullOrBlank()) {
                                    Text(
                                        text = description,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                } else if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (totalDurationMs > 0) "${tracks.size} tracks  •  $durationFormatted" else "${tracks.size} tracks",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Action Controls Bar (Play All, Shuffle, and 3-Dots Menu on the same line)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play All Button
                        Button(
                            onClick = onPlayAllClick,
                            enabled = tracks.isNotEmpty() && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }

                        // Shuffle Button
                        Button(
                            onClick = {
                                if (onShuffleClick != null) onShuffleClick()
                                else viewModel?.playOnlineShuffle(filteredTracks)
                            },
                            enabled = tracks.isNotEmpty() && !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                contentColor = textPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }

                        // 3-Dots Action Row Menu Button
                        var showActionMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showActionMenu = true },
                                enabled = tracks.isNotEmpty() && !isLoading,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showActionMenu,
                                onDismissRequest = { showActionMenu = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color.White,
                                shadowElevation = 16.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                            Text("Start mix", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        if (tracks.isNotEmpty()) {
                                            viewModel?.startMix(filteredTracks)
                                            android.widget.Toast.makeText(context, "Starting Mix...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                            Text("Play next", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        if (onPlayNextClick != null) {
                                            onPlayNextClick()
                                        } else {
                                            viewModel?.playNextTracks(filteredTracks)
                                            android.widget.Toast.makeText(context, "Playing next after current song", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                            Text("Add to queue", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        if (onAddToQueueClick != null) {
                                            onAddToQueueClick()
                                        } else {
                                            viewModel?.addTracksToQueue(filteredTracks)
                                            android.widget.Toast.makeText(context, "Added ${filteredTracks.size} track(s) to Queue", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                            Text("Download playlist", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        showDownloadDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                            Text("Save to playlist", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        showAddToPlaylist = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                            Text("Share", color = textPrimary)
                                        }
                                    },
                                    onClick = {
                                        showActionMenu = false
                                        val shareText = buildString {
                                            append("Check out \"$title\" playlist on Mueso:\n")
                                            displayTracks.take(15).forEachIndexed { i, t ->
                                                append("${i + 1}. ${t.title} - ${t.artist}\n")
                                            }
                                            if (displayTracks.size > 15) append("... and ${displayTracks.size - 15} more songs")
                                        }
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Playlist"))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Active Downloads Progress Banner Card
            if (activeDownloads.isNotEmpty()) {
                item {
                    val firstDl = activeDownloads.first()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { firstDl.progress },
                                modifier = Modifier.size(24.dp),
                                color = AccentOrange
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Downloading ${activeDownloads.size} track(s)... (${(firstDl.progress * 100).toInt()}%)",
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { firstDl.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = AccentOrange,
                                    trackColor = AccentOrange.copy(alpha = 0.2f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    displayTracks.forEach { trk ->
                                        if (downloadStates[trk.id]?.isDownloading == true) {
                                            viewModel?.cancelDownload(trk.id)
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel All Downloads",
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Loading Spinner
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(24.dp))
                            Text("Loading playlist tracks...", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else if (filteredTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (tracks.isEmpty()) "No tracks in this playlist yet" else "No matching tracks found",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(filteredTracks, key = { _, trk -> trk.id }) { index, track ->
                    val isDragging = draggedIndex == index

                    OnlineTrackListItem(
                        index = index + 1,
                        totalCount = filteredTracks.size,
                        track = track,
                        isCustomUserPlaylist = isCustomUserPlaylist,
                        isDarkMode = isDarkMode,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffset else 0f,
                        downloadState = downloadStates[track.id],
                        onClick = { onTrackClick(index) },
                        onRemove = { onRemoveTrack?.invoke(track) },
                        onMoveUp = { onMoveTrack?.invoke(index, index - 1) },
                        onMoveDown = { onMoveTrack?.invoke(index, index + 1) },
                        onDownload = { onDownloadTrack?.invoke(track) ?: viewModel?.downloadOnlineTrack(context, track) },
                        onDownloadOptions = { onDownloadTrack?.invoke(track) ?: viewModel?.downloadOnlineTrack(context, track) },
                        onCancelDownload = { viewModel?.cancelDownload(track.id) },
                        dragModifier = if (isCustomUserPlaylist && onMoveTrack != null) {
                            Modifier.pointerInput(track.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        initialDragIndex = index
                                        draggedIndex = index
                                        dragOffset = 0f
                                        android.util.Log.d("MUESO_DRAG", "[OnlinePlaylist] Started dragging '${track.title}' from index $index")
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
                                            android.util.Log.d("MUESO_DRAG", "[OnlinePlaylist] Locally moved '${track.title}' DOWN to index $next")
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
                                            android.util.Log.d("MUESO_DRAG", "[OnlinePlaylist] Locally moved '${track.title}' UP to index $prev")
                                        }
                                    },
                                    onDragEnd = {
                                        val start = initialDragIndex
                                        val finalIdx = draggedIndex
                                        if (start != null && finalIdx != null && start != finalIdx) {
                                            onMoveTrack.invoke(start, finalIdx)
                                            android.util.Log.d("MUESO_DRAG", "[OnlinePlaylist] Drag finished! Committed move from $start -> $finalIdx")
                                        }
                                        draggedIndex = null
                                        initialDragIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        val start = initialDragIndex
                                        val finalIdx = draggedIndex
                                        if (start != null && finalIdx != null && start != finalIdx) {
                                            onMoveTrack.invoke(start, finalIdx)
                                            android.util.Log.d("MUESO_DRAG", "[OnlinePlaylist] Drag end/cancel! Committed move from $start -> $finalIdx")
                                        }
                                        draggedIndex = null
                                        initialDragIndex = null
                                        dragOffset = 0f
                                    }
                                )
                            }
                        } else Modifier
                    )
                }
            }
        }

        if (showDownloadDialog) {
            DownloadPlaylistDialog(
                playlistTitle = title,
                tracks = tracks,
                isDarkMode = isDarkMode,
                onConfirmDownload = { selectedTracks ->
                    selectedTracks.forEach { track ->
                        onDownloadTrack?.invoke(track)
                    }
                    showDownloadDialog = false
                    android.widget.Toast.makeText(context, "Downloading ${selectedTracks.size} tracks...", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showDownloadDialog = false }
            )
        }
    }
}

@Composable
private fun DownloadPlaylistDialog(
    playlistTitle: String,
    tracks: List<TrackEntity>,
    isDarkMode: Boolean = true,
    onConfirmDownload: (List<TrackEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedTracks = remember { mutableStateListOf<TrackEntity>().apply { addAll(tracks) } }
    val dialogBg = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val itemBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Column {
                Text("Download Playlist", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select tracks to download offline", color = textSub, fontSize = 12.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedTracks.size} of ${tracks.size} selected",
                        color = AccentOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            if (selectedTracks.size == tracks.size) {
                                selectedTracks.clear()
                            } else {
                                selectedTracks.clear()
                                selectedTracks.addAll(tracks)
                            }
                        }
                    ) {
                        Text(
                            if (selectedTracks.size == tracks.size) "Deselect All" else "Select All",
                            color = AccentOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        val isChecked = selectedTracks.contains(track)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBg)
                                .clickable {
                                    if (isChecked) selectedTracks.remove(track)
                                    else selectedTracks.add(track)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedTracks.add(track)
                                    else selectedTracks.remove(track)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentOrange,
                                    uncheckedColor = textSub
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artist,
                                    color = textSub,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedTracks.isNotEmpty()) onConfirmDownload(selectedTracks) },
                enabled = selectedTracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Download (${selectedTracks.size})", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSub)
            }
        }
    )
}

@Composable
private fun OnlineTrackListItem(
    index: Int,
    totalCount: Int,
    track: TrackEntity,
    isCustomUserPlaylist: Boolean,
    isDarkMode: Boolean,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    downloadState: DownloadProgress? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onDownloadOptions: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    dragModifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (isDragging) 8f else 0f
            }
            .then(dragModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Track number
        Text(
            text = "$index",
            color = textSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp)
        )

        // Thumbnail artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            com.akshay.musicplayer.ui.components.SmartArtworkImage(
                artworkUrl = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                thumbnailQuality = "Low (Fast)"
            )
        }

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = textSub,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Download status icon / spinner / cancel
        when {
            downloadState?.isDownloading == true -> {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onCancelDownload?.invoke() }
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.size(22.dp),
                        color = AccentOrange,
                        strokeWidth = 2.dp
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = AccentOrange,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            downloadState?.isDownloaded == true -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Overflow Options Menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = textSub
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF),
                shadowElevation = 16.dp
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Text("Play Now", color = textColor)
                        }
                    },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                if (downloadState?.isDownloading != true && downloadState?.isDownloaded != true) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                Text("Download Song", color = textColor)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onDownload?.invoke()
                        }
                    )
                }
                if (downloadState?.isDownloading == true) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                                Text("Cancel Download", color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onCancelDownload?.invoke()
                        }
                    )
                }
                if (isCustomUserPlaylist) {
                    if (index > 1) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                                    Text("Move Up", color = textColor)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (index < totalCount) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                                    Text("Move Down", color = textColor)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                                Text("Remove from Playlist", color = Color(0xFFFF453A))
                            }
                        },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditPlaylistDetailsDialog(
    initialName: String,
    initialDescription: String = "",
    isDarkMode: Boolean = true,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    val dialogBg = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Text(
                "Edit Playlist Details",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Playlist Title", color = textSecondary, fontSize = 12.sp) },
                    placeholder = { Text("Playlist Title", color = textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = AccentOrange
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (Optional)", color = textSecondary, fontSize = 12.sp) },
                    placeholder = { Text("Add an optional description...", color = textSecondary) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = AccentOrange
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), description.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}
