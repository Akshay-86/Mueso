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
import androidx.compose.foundation.lazy.rememberLazyListState
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { OnlineMusicRepository() }
    val curatedPlaylists = remember { repo.getCuratedPlaylists() }
    val customOnlinePlaylists by viewModel.onlinePlaylists.collectAsState()

    var selectedCuratedPlaylist by remember { mutableStateOf<CuratedOnlinePlaylist?>(null) }
    var selectedCustomPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    
    val curatedTracksMap = remember { mutableStateMapOf<String, List<TrackEntity>>() }
    val isFetchingCuratedMap = remember { mutableStateMapOf<String, Boolean>() }

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

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

    // State for Editing Custom Online Playlist
    var editingPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }

    // Pre-fetch Hero Curated Playlist if needed
    val heroPlaylistId by viewModel.heroPlaylistId.collectAsState()
    val currentHeroCurated = curatedPlaylists.firstOrNull { it.id == heroPlaylistId } ?: curatedPlaylists.first()
    LaunchedEffect(currentHeroCurated.id) {
        if (!curatedTracksMap.containsKey(currentHeroCurated.id)) {
            val fetched = viewModel.getCuratedPlaylistTracks(currentHeroCurated.searchQuery)
            curatedTracksMap[currentHeroCurated.id] = fetched
        }
    }

    val mainListState = rememberLazyListState()

    // Main Online Playlists Hub Screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = mainListState,
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
                            color = textColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Spotify & YouTube Music Curated Hub",
                            color = textSub,
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
                                containerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                labelColor = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3C)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Hero Banner (Featured Playlist - Dynamic based on heroPlaylistId with 2x2 Cover Art)
            if (selectedCategory == "All" || selectedCategory == "Top Charts") {
                item {
                    val heroPlaylistId by viewModel.heroPlaylistId.collectAsState()
                    val heroCurated = curatedPlaylists.firstOrNull { it.id == heroPlaylistId } ?: curatedPlaylists.first()
                    val heroCustom = customOnlinePlaylists.firstOrNull { "custom_${it.id}" == heroPlaylistId }

                    val heroTitle = heroCustom?.name ?: heroCurated.title
                    val heroSubtitle = heroCustom?.description?.takeIf { it.isNotBlank() } ?: if (heroCustom != null) "Your Custom Online Playlist" else heroCurated.subtitle
                    val heroGradients = if (heroCustom != null) listOf(0xFF8E2DE2, 0xFF4A00E0) else heroCurated.gradientColors

                    // Collect tracks for Hero Playlist
                    val heroCustomTracks = if (heroCustom != null) {
                        val flow = remember(heroCustom.id) { viewModel.getOnlinePlaylistTracks(heroCustom.id) }
                        val tracks by flow.collectAsState(initial = emptyList())
                        tracks
                    } else emptyList()

                    val heroCuratedTracks = if (heroCustom == null) {
                        curatedTracksMap[heroCurated.id] ?: emptyList()
                    } else emptyList()

                    val heroTracks = if (heroCustom != null) heroCustomTracks else heroCuratedTracks

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(heroGradients.map { Color(it) }))
                            .clickable {
                                if (heroCustom != null) selectedCustomPlaylist = heroCustom
                                else selectedCuratedPlaylist = heroCurated
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Left Column: Details & Play Button
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "FEATURED HERO PLAYLIST",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = heroTitle,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = heroSubtitle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FloatingActionButton(
                                    onClick = {
                                        if (heroCustom != null) selectedCustomPlaylist = heroCustom
                                        else selectedCuratedPlaylist = heroCurated
                                    },
                                    containerColor = Color.White,
                                    contentColor = AccentOrange,
                                    shape = CircleShape,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(24.dp))
                                }
                            }

                            // Right Side: 2x2 Collage Cover Art Thumbnail Card (120dp)
                            com.akshay.musicplayer.ui.components.PlaylistCollageArt(
                                tracks = heroTracks,
                                modifier = Modifier.size(120.dp),
                                cornerRadius = 16.dp,
                                fallbackGradient = heroGradients.map { Color(it) }
                            )
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
                            color = textColor,
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
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
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
                                        color = textColor.copy(alpha = 0.8f),
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
                                        viewModel = viewModel,
                                        isDarkMode = isDarkMode,
                                        onClick = { selectedCustomPlaylist = playlist },
                                        onSetHero = { viewModel.setHeroPlaylistId("custom_${playlist.id}") },
                                        onEdit = { editingPlaylist = playlist },
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
                        color = textColor,
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
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCuratedPlaylist = curated },
                                    onSetHero = { viewModel.setHeroPlaylistId(curated.id) }
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

        // Dialog for editing an existing online playlist
        if (editingPlaylist != null) {
            EditPlaylistDialog(
                initialName = editingPlaylist!!.name,
                initialDescription = editingPlaylist!!.description ?: "",
                onConfirm = { newName, newDesc ->
                    viewModel.updateOnlinePlaylistDetails(editingPlaylist!!.id, newName, newDesc)
                    editingPlaylist = null
                },
                onDismiss = { editingPlaylist = null }
            )
        }

        // Overlay for selected Curated Playlist Detail
        if (selectedCuratedPlaylist != null) {
            val playlist = selectedCuratedPlaylist!!
            val curatedTracks = curatedTracksMap[playlist.id] ?: emptyList()
            val isFetchingCuratedTracks = isFetchingCuratedMap[playlist.id] ?: false

            LaunchedEffect(playlist.id) {
                if (!curatedTracksMap.containsKey(playlist.id)) {
                    isFetchingCuratedMap[playlist.id] = true
                    val fetched = viewModel.getCuratedPlaylistTracks(playlist.searchQuery)
                    curatedTracksMap[playlist.id] = fetched
                    isFetchingCuratedMap[playlist.id] = false
                }
            }

            OnlinePlaylistDetailScreen(
                title = playlist.title,
                subtitle = playlist.subtitle,
                gradientColors = playlist.gradientColors.map { Color(it) },
                tracks = curatedTracks,
                isLoading = isFetchingCuratedTracks && curatedTracks.isEmpty(),
                isCustomUserPlaylist = false,
                isDarkMode = isDarkMode,
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
                },
                onDownloadTrack = { track ->
                    viewModel.downloadOnlineTrack(context, track)
                }
            )
        }

        // Overlay for selected Custom User Playlist Detail
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
                isDarkMode = isDarkMode,
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
                },
                onDownloadTrack = { track ->
                    viewModel.downloadOnlineTrack(context, track)
                }
            )
        }
    }
}

@Composable
private fun CustomPlaylistCard(
    playlist: OnlinePlaylistEntity,
    viewModel: PlayerViewModel,
    isDarkMode: Boolean = true,
    onClick: () -> Unit,
    onSetHero: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val userTracksFlow = remember(playlist.id) { viewModel.getOnlinePlaylistTracks(playlist.id) }
    val userTracks by userTracksFlow.collectAsState(initial = emptyList())

    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            com.akshay.musicplayer.ui.components.PlaylistCollageArt(
                tracks = userTracks,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 16.dp,
                fallbackGradient = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
                androidx.compose.material3.MaterialTheme(
                    colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                        surface = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
                    ),
                    shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                        extraSmall = RoundedCornerShape(12.dp)
                    )
                ) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                    DropdownMenuItem(
                        text = { Text("Set as Hero Banner", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                        onClick = {
                            showMenu = false
                            onSetHero()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Details", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
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
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${userTracks.size} tracks",
            color = textSub,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CuratedPlaylistGridCard(
    playlist: CuratedOnlinePlaylist,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSetHero: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(playlist.gradientColors.map { Color(it) }))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
        ) {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White.copy(alpha = 0.8f))
            }
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                    surface = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
                ),
                shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                    extraSmall = RoundedCornerShape(12.dp)
                )
            ) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                DropdownMenuItem(
                    text = { Text("Set as Hero Banner", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                    onClick = {
                        showMenu = false
                        onSetHero()
                    }
                )
                }
            }
        }

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
private fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String,
    onConfirm: (newName: String, newDescription: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Playlist Details", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        focusedLabelColor = AccentOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        focusedLabelColor = AccentOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), description.trim())
                    }
                }
            ) {
                Text("Save", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1E1E2E)
    )
}

@Composable
fun CreateOnlinePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit,
    isDarkMode: Boolean = true
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val dialogBg = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = { Text("Create Online Playlist", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name", color = textSub) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = textSub
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)", color = textSub) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = textSub
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
                Text("Cancel", color = textSub)
            }
        }
    )
}
