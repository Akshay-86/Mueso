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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.akshay.musicplayer.ui.viewmodel.DownloadProgress
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)
private val TextSecondary = Color(0xFF8E8E93)

@Composable
fun OnlinePlaylistDetailScreen(
    title: String,
    subtitle: String?,
    gradientColors: List<Color>,
    tracks: List<TrackEntity>,
    isLoading: Boolean,
    isCustomUserPlaylist: Boolean = false,
    isDarkMode: Boolean = true,
    viewModel: PlayerViewModel? = null,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: ((TrackEntity) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onDownloadTrack: ((TrackEntity) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)
    val context = LocalContext.current

    val downloadStates by viewModel?.downloadStates?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }
    val activeDownloads = remember(downloadStates, tracks) {
        tracks.mapNotNull { track -> downloadStates[track.id] }.filter { it.isDownloading }
    }

    val filteredTracks = remember(searchQuery, tracks) {
        if (searchQuery.isBlank()) tracks
        else {
            val q = searchQuery.trim().lowercase()
            tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
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
                        // Header with Back Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                if (!subtitle.isNullOrBlank()) {
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

            // Action Controls Bar (Play All, Shuffle All, Download Playlist)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Play All Button
                    Button(
                        onClick = onPlayAllClick,
                        enabled = tracks.isNotEmpty() && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Shuffle Button
                    Button(
                        onClick = onShuffleAllClick,
                        enabled = tracks.isNotEmpty() && !isLoading,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                            contentColor = textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // Download Playlist Button
                    Button(
                        onClick = { showDownloadDialog = true },
                        enabled = tracks.isNotEmpty() && !isLoading,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                            contentColor = textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download Playlist", tint = textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                itemsIndexed(filteredTracks) { index, track ->
                    OnlineTrackListItem(
                        index = index + 1,
                        totalCount = filteredTracks.size,
                        track = track,
                        isCustomUserPlaylist = isCustomUserPlaylist,
                        isDarkMode = isDarkMode,
                        downloadState = downloadStates[track.id],
                        onClick = { onTrackClick(index) },
                        onRemove = { onRemoveTrack?.invoke(track) },
                        onMoveUp = { onMoveTrack?.invoke(index, index - 1) },
                        onMoveDown = { onMoveTrack?.invoke(index, index + 1) },
                        onDownload = { onDownloadTrack?.invoke(track) },
                        onCancelDownload = { viewModel?.cancelDownload(track.id) }
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
    isDarkMode: Boolean = true,
    downloadState: DownloadProgress? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                contentScale = ContentScale.Crop
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
                    text = { Text("Play Now", color = textColor) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
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
                            text = { Text("Move Up", color = textColor) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (index < totalCount) {
                        DropdownMenuItem(
                            text = { Text("Move Down", color = textColor) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist", color = Color(0xFFFF453A)) },
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
