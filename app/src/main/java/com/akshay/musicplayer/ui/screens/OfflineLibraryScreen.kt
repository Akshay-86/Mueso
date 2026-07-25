package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.state.PlayerUiState
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange = Color(0xFFFF512F)
private val AccentGradient = Brush.horizontalGradient(listOf(Color(0xFFFF512F), Color(0xFFDD2476)))
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceCard = Color(0xFF16213E)
private val BgDark = Color(0xFF0F0F0F)

@Composable
fun OfflineLibraryScreen(
    viewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortOption by remember { mutableStateOf(SortOption.DATE_ADDED) }

    val selectedTab by viewModel.offlineLibraryTab.collectAsState()
    var trackToAdd by remember { mutableStateOf<TrackEntity?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 100.dp)
    ) {
        // Animated tab content
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { it }
                    ) + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                targetOffsetX = { -it / 3 }
                            ) + fadeOut(tween(150))
                        )
                } else {
                    (slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { -it }
                    ) + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                targetOffsetX = { it / 3 }
                            ) + fadeOut(tween(150))
                        )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            label = "tabContent"
        ) { tab ->
            when (tab) {
                1 -> AllSongsTab(
                    uiState = uiState,
                    sortOption = sortOption,
                    isDarkMode = isDarkMode,
                    onSortChange = { sortOption = it },
                    onTrackClick = { track ->
                        viewModel.playTrack(track)
                        onNavigateToPlayer()
                    },
                    onAddToPlaylist = { trackToAdd = it },
                    onRefresh = { viewModel.loadLocalTracks(forceReload = true) }
                )
                else -> PlaylistsTab(
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    onPlaylistClick = onPlaylistClick
                )
            }
        }

        // Bottom center pill tabs
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Playlists" to 0, "All Songs" to 1).forEach { (title, index) ->
                val isSelected = selectedTab == index
                val bgColor by animateColorAsState(
                    if (isSelected) AccentOrange else Color.Transparent,
                    animationSpec = tween(250),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else (if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)),
                    animationSpec = tween(250),
                    label = "tabText"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.setOfflineLibraryTab(index) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Add to Playlist bottom sheet style dialog
        if (trackToAdd != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                trackToAdd = trackToAdd,
                viewModel = viewModel,
                isDarkMode = isDarkMode,
                onPlaylistSelected = { playlistId ->
                    viewModel.addTrackToPlaylist(playlistId, trackToAdd!!.id)
                    trackToAdd = null
                },
                onDismiss = { trackToAdd = null }
            )
        }
    }
}

// ─── All Songs Tab ───────────────────────────────────────────

@Composable
private fun AllSongsTab(
    uiState: PlayerUiState,
    sortOption: SortOption,
    isDarkMode: Boolean,
    onSortChange: (SortOption) -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onRefresh: () -> Unit = {}
) {
    when (val state = uiState) {
        is PlayerUiState.Success -> {
            val tracks = state.tracks
            val sortedTracks = when (sortOption) {
                SortOption.A_Z -> tracks.sortedBy { it.title }
                SortOption.DATE_ADDED -> tracks
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tracks.size} tracks",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Offline Library",
                                tint = AccentOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        SortChip(
                            currentSort = sortOption,
                            isDarkMode = isDarkMode,
                            onSortChange = onSortChange
                        )
                    }
                }

                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(AccentOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = "No Offline Songs Found",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Downloaded songs and local MP3s will appear here. Tap below after downloading.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescan & Refresh Library", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        itemsIndexed(sortedTracks) { index, track ->
                            TrackListItem(
                                track = track,
                                isDarkMode = isDarkMode,
                                onClick = { onTrackClick(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }
            }
        }
        is PlayerUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
        }
        is PlayerUiState.Empty, is PlayerUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Offline Songs Found",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Downloaded songs and local MP3s will appear here.\nTap below to scan your device.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan & Refresh Library", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Sort Chip ───────────────────────────────────────────────

@Composable
private fun SortChip(
    currentSort: SortOption,
    isDarkMode: Boolean,
    onSortChange: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chipBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val textTint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3C)
    val dropdownBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)
    val itemTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(chipBg)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SortByAlpha,
                contentDescription = null,
                tint = textTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = when (currentSort) {
                    SortOption.A_Z -> "A–Z"
                    SortOption.DATE_ADDED -> "Recent"
                },
                color = textTint,
                fontSize = 13.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
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
                        Icon(Icons.Default.Schedule, null, tint = itemTextColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        Text("Date Added", color = itemTextColor)
                    }
                },
                onClick = { onSortChange(SortOption.DATE_ADDED); expanded = false },
                modifier = Modifier.padding(horizontal = 4.dp),
                trailingIcon = {
                    if (currentSort == SortOption.DATE_ADDED) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(AccentOrange)
                        )
                    }
                }
            )
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.SortByAlpha, null, tint = itemTextColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        Text("Alphabetical", color = itemTextColor)
                    }
                },
                onClick = { onSortChange(SortOption.A_Z); expanded = false },
                modifier = Modifier.padding(horizontal = 4.dp),
                trailingIcon = {
                    if (currentSort == SortOption.A_Z) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(AccentOrange)
                        )
                    }
                }
            )
        }
    }
}

// ─── Track List Item ─────────────────────────────────────────

@Composable
fun TrackListItem(
    track: TrackEntity,
    isDarkMode: Boolean = true,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val minutes = (track.duration / 1000) / 60
    val seconds = (track.duration / 1000) % 60

    val iconTint = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF3A3A3C)
    val dropdownBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)
    val itemTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Album art placeholder
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
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onBackground,
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
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
                Text(
                    text = "%d:%02d".format(minutes, seconds),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }
        }

        // 3-dot menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset((-8).dp, 0.dp),
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
                                Icons.Default.PlaylistAdd,
                                null,
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Add to Playlist", color = itemTextColor)
                        }
                    },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// ─── Playlists Tab ───────────────────────────────────────────

@Composable
private fun PlaylistsTab(
    viewModel: PlayerViewModel,
    isDarkMode: Boolean = true,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            isDarkMode = isDarkMode,
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renamePlaylist?.let { playlistToRename ->
        RenamePlaylistDialog(
            initialName = playlistToRename.name,
            isDarkMode = isDarkMode,
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlistToRename.id, newName)
                renamePlaylist = null
            },
            onDismiss = { renamePlaylist = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AccentOrange.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = AccentOrange.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    "No playlists yet",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Playlist", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${playlists.size} playlists",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Playlist",
                            tint = AccentOrange
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            isDarkMode = isDarkMode,
                            onClick = { onPlaylistClick(playlist) },
                            onRename = { renamePlaylist = playlist },
                            onDelete = { viewModel.deletePlaylist(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistEntity,
    isDarkMode: Boolean = true,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val itemBg = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color(0xFF6E6E73)
    val iconTint = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF3A3A3C)
    val dropdownBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(itemBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AccentOrange.copy(alpha = 0.2f), Color(0xFFDD2476).copy(alpha = 0.15f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = AccentOrange.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Playlist",
                color = textSecondary,
                fontSize = 12.sp
            )
        }
        
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = iconTint
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
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF5350)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
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
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)

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
                placeholder = { Text("Playlist name", color = textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.15f),
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

// ─── Create Playlist Dialog ──────────────────────────────────

@Composable
private fun CreatePlaylistDialog(
    isDarkMode: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val dialogBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Text(
                "New Playlist",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                    cursorColor = AccentOrange,
                    focusedLabelColor = AccentOrange,
                    unfocusedLabelColor = textSecondary,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    disabledContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

// ─── Add to Playlist Dialog ──────────────────────────────────

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    trackToAdd: TrackEntity? = null,
    viewModel: PlayerViewModel? = null,
    isDarkMode: Boolean = true,
    onPlaylistSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkMode) SurfaceDark else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val itemBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Text(
                "Add to Playlist",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        null,
                        tint = textSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        "No playlists yet. Create one first!",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists) { playlist ->
                        val playlistTracks by viewModel?.getPlaylistTracks(playlist.id)?.collectAsState(initial = emptyList())
                            ?: remember { mutableStateOf(emptyList()) }
                        val isAlreadyInPlaylist = remember(playlistTracks, trackToAdd?.id) {
                            trackToAdd != null && playlistTracks.any { it.id == trackToAdd.id }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBg)
                                .clickable { onPlaylistSelected(playlist.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                null,
                                tint = AccentOrange.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isAlreadyInPlaylist) {
                                    Text(
                                        "(Already added)",
                                        color = Color(0xFF34C759),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (isAlreadyInPlaylist) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Already Added",
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = textSecondary)
            }
        }
    )
}

enum class SortOption {
    A_Z, DATE_ADDED
}
