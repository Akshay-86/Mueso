package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.remote.CuratedOnlinePlaylist
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)
private val TextSecondary = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistsScreen(
    viewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onDetailVisibilityChanged: (Boolean) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { OnlineMusicRepository() }
    val curatedPlaylists = remember { repo.getCuratedPlaylists() }
    val customOnlinePlaylists by viewModel.onlinePlaylists.collectAsState()

    var selectedCuratedPlaylist by remember { mutableStateOf<CuratedOnlinePlaylist?>(null) }
    var selectedCustomPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    
    var curatedTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var isFetchingCuratedTracks by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Top Charts", "Mood & Focus", "Fitness & Energy", "Classics", "My Playlists")

    val isDetailOpen = selectedCuratedPlaylist != null || selectedCustomPlaylist != null
    LaunchedEffect(isDetailOpen) {
        onDetailVisibilityChanged(isDetailOpen)
    }

    androidx.activity.compose.BackHandler(enabled = isDetailOpen) {
        selectedCuratedPlaylist = null
        selectedCustomPlaylist = null
    }

    // Drill down into Curated Playlist Detail View
    if (selectedCuratedPlaylist != null) {
        val playlist = selectedCuratedPlaylist!!
        LaunchedEffect(playlist.id) {
            isFetchingCuratedTracks = true
            curatedTracks = viewModel.getCuratedPlaylistTracks(playlist.searchQuery)
            isFetchingCuratedTracks = false
        }

        OnlinePlaylistDetailScreen(
            title = playlist.title,
            subtitle = playlist.subtitle,
            gradientColors = playlist.gradientColors.map { Color(it) },
            tracks = curatedTracks,
            isLoading = isFetchingCuratedTracks,
            isCustomUserPlaylist = false,
            onBackClick = { selectedCuratedPlaylist = null },
            onPlayAllClick = {
                if (curatedTracks.isNotEmpty()) {
                    viewModel.playOnlinePlaylist(curatedTracks, 0)
                    selectedCuratedPlaylist = null
                    onNavigateToPlayer()
                }
            },
            onShuffleAllClick = {
                if (curatedTracks.isNotEmpty()) {
                    val shuffled = curatedTracks.shuffled()
                    viewModel.playOnlinePlaylist(shuffled, 0)
                    selectedCuratedPlaylist = null
                    onNavigateToPlayer()
                }
            },
            onTrackClick = { index ->
                if (index in curatedTracks.indices) {
                    viewModel.playOnlinePlaylist(curatedTracks, index)
                    selectedCuratedPlaylist = null
                    onNavigateToPlayer()
                }
            }
        )
        return
    }

    // Drill down into Custom User Online Playlist Detail View
    if (selectedCustomPlaylist != null) {
        val playlist = selectedCustomPlaylist!!
        val userTracksFlow = remember(playlist.id) { viewModel.getOnlinePlaylistTracks(playlist.id) }
        val userTracks by userTracksFlow.collectAsState(initial = emptyList())

        OnlinePlaylistDetailScreen(
            title = playlist.name,
            subtitle = playlist.description ?: "Custom Online Playlist",
            gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
            tracks = userTracks,
            isLoading = false,
            isCustomUserPlaylist = true,
            onBackClick = { selectedCustomPlaylist = null },
            onPlayAllClick = {
                if (userTracks.isNotEmpty()) {
                    viewModel.playOnlinePlaylist(userTracks, 0)
                    selectedCustomPlaylist = null
                    onNavigateToPlayer()
                }
            },
            onShuffleAllClick = {
                if (userTracks.isNotEmpty()) {
                    val shuffled = userTracks.shuffled()
                    viewModel.playOnlinePlaylist(shuffled, 0)
                    selectedCustomPlaylist = null
                    onNavigateToPlayer()
                }
            },
            onTrackClick = { index ->
                if (index in userTracks.indices) {
                    viewModel.playOnlinePlaylist(userTracks, index)
                    selectedCustomPlaylist = null
                    onNavigateToPlayer()
                }
            },
            onRemoveTrack = { track ->
                viewModel.removeTrackFromOnlinePlaylist(playlist.id, track.id)
            },
            onMoveTrack = { fromIndex, toIndex ->
                viewModel.moveTrackInOnlinePlaylist(playlist.id, fromIndex, toIndex)
            }
        )
        return
    }

    // Main Online Playlists Hub Screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header Title Bar (positioned below TopNavigationBarWithSearch)
            item {
                Spacer(modifier = Modifier.height(100.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Online Playlists Hub",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Spotify & YouTube Music Curated Hub",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    // Create Playlist Button
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AccentOrange)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Online Playlist", tint = Color.White)
                    }
                }
            }

            // Category Filter Chips Row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = cat == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Hero Banner (Featured Global Chart)
            if (selectedCategory == "All" || selectedCategory == "Top Charts") {
                item {
                    val hero = curatedPlaylists.first()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(hero.gradientColors.map { Color(it) }))
                            .clickable { selectedCuratedPlaylist = hero }
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FEATURED CHART",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = hero.title,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = hero.subtitle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FloatingActionButton(
                                onClick = { selectedCuratedPlaylist = hero },
                                containerColor = Color.White,
                                contentColor = AccentOrange,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }

            // My Online Playlists Section
            if (selectedCategory == "All" || selectedCategory == "My Playlists") {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "My Online Playlists",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        if (customOnlinePlaylists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { showCreateDialog = true }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentOrange)
                                    Text(
                                        text = "Create your first Online Playlist",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(customOnlinePlaylists) { playlist ->
                                    CustomPlaylistCard(
                                        playlist = playlist,
                                        onClick = { selectedCustomPlaylist = playlist },
                                        onDelete = { viewModel.deleteOnlinePlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Curated Playlists Sections
            val filteredCurated = if (selectedCategory == "All" || selectedCategory == "My Playlists") {
                curatedPlaylists
            } else {
                curatedPlaylists.filter { it.category == selectedCategory }
            }

            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = if (selectedCategory == "All") "Curated Playlists & Charts" else "$selectedCategory Playlists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    val rows = filteredCurated.chunked(2)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { curated ->
                                CuratedPlaylistGridCard(
                                    playlist = curated,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCuratedPlaylist = curated }
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Dialog for creating a new online playlist
        if (showCreateDialog) {
            CreateOnlinePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, desc ->
                    viewModel.createOnlinePlaylist(name, desc)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
private fun CustomPlaylistCard(
    playlist: OnlinePlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlaylistPlay,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1F1F2E))
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = Color(0xFFFF453A)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CuratedPlaylistGridCard(
    playlist: CuratedOnlinePlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(playlist.gradientColors.map { Color(it) }))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = playlist.category.uppercase(),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.subtitle,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreateOnlinePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F1F2E),
        title = { Text("Create Online Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), description.trim().ifBlank { null }) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}
