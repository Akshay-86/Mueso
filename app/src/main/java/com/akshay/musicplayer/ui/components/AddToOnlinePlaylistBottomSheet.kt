package com.akshay.musicplayer.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.screens.CreateOnlinePlaylistDialog
import com.akshay.musicplayer.ui.screens.CreatePlaylistDialog
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

private val YouTubeRed = Color(0xFFFF0000)
private val PurpleGradient = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))

sealed class PlaylistItemWrapper {
    data class YouTube(val playlist: InnerTubePlaylist) : PlaylistItemWrapper()
    data class CustomOnline(val playlist: OnlinePlaylistEntity) : PlaylistItemWrapper()
    data class Local(val playlist: PlaylistEntity) : PlaylistItemWrapper()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    track: TrackEntity? = null,
    tracks: List<TrackEntity> = emptyList(),
    viewModel: PlayerViewModel,
    isDarkMode: Boolean = true,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val effectiveTracks = remember(track, tracks) {
        if (track != null) listOf(track) else tracks
    }

    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youtubeUserPlaylists by viewModel.youtubeUserPlaylists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val offlinePlaylists by viewModel.playlists.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialogType by remember { mutableStateOf<String?>(null) } // "online", "local", "youtube"
    var showCreateChoiceDialog by remember { mutableStateOf(false) }

    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
    val sheetBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1A1A2E)) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    // Dialog for creating YouTube playlist
    if (showCreateDialogType == "youtube") {
        CreateOnlinePlaylistDialog(
            onDismiss = { showCreateDialogType = null },
            onCreate = { name, desc ->
                viewModel.createYouTubePlaylist(name, desc ?: "") { id ->
                    if (id != null) {
                        effectiveTracks.forEach { trk ->
                            viewModel.addTrackToYouTubePlaylist(
                                com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist(id = id, title = name),
                                trk
                            ) { _ -> }
                        }
                        val msg = if (effectiveTracks.isEmpty()) "Created YouTube Playlist \"$name\""
                        else if (effectiveTracks.size == 1) "Added \"${effectiveTracks.first().title}\" to \"$name\" (YouTube)"
                        else "Added ${effectiveTracks.size} tracks to \"$name\" (YouTube)"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Failed to create YouTube Playlist", Toast.LENGTH_SHORT).show()
                    }
                }
                showCreateDialogType = null
            },
            isDarkMode = isDarkMode
        )
    }

    // Dialog for creating Custom Online Playlist
    if (showCreateDialogType == "online") {
        CreateOnlinePlaylistDialog(
            onDismiss = { showCreateDialogType = null },
            onCreate = { name, desc ->
                viewModel.createOnlinePlaylist(name, desc) { newId ->
                    if (effectiveTracks.isNotEmpty()) {
                        viewModel.addTracksToOnlinePlaylist(newId, effectiveTracks)
                    }
                }
                showCreateDialogType = null
                val msg = if (effectiveTracks.isEmpty()) "Created Online Playlist \"$name\""
                else if (effectiveTracks.size == 1) "Added \"${effectiveTracks.first().title}\" to \"$name\""
                else "Added ${effectiveTracks.size} tracks to \"$name\""
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            isDarkMode = isDarkMode
        )
    }

    // Dialog for creating Local Playlist
    if (showCreateDialogType == "local") {
        CreatePlaylistDialog(
            isDarkMode = isDarkMode,
            onConfirm = { name ->
                viewModel.createPlaylist(name) { newId ->
                    if (effectiveTracks.isNotEmpty()) {
                        if (effectiveTracks.size == 1) {
                            viewModel.addTrackToPlaylist(newId, effectiveTracks.first().id)
                        } else {
                            viewModel.addTracksToPlaylist(newId, effectiveTracks.map { it.id })
                        }
                    }
                }
                showCreateDialogType = null
                val msg = if (effectiveTracks.isEmpty()) "Created Local Playlist \"$name\""
                else if (effectiveTracks.size == 1) "Added \"${effectiveTracks.first().title}\" to \"$name\""
                else "Added ${effectiveTracks.size} tracks to \"$name\""
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { showCreateDialogType = null }
        )
    }

    // Choice Dialog to pick playlist type if clicking "Create New"
    if (showCreateChoiceDialog) {
        CreatePlaylistChoiceDialog(
            isYouTubeLoggedIn = isYouTubeLoggedIn,
            isDarkMode = isDarkMode,
            onSelectType = { type ->
                showCreateChoiceDialog = false
                showCreateDialogType = type
            },
            onDismiss = { showCreateChoiceDialog = false }
        )
    }

    // Build unified playlists list according to filter
    val allItems = remember(youtubeUserPlaylists, onlinePlaylists, offlinePlaylists, selectedFilter, isYouTubeLoggedIn) {
        val list = mutableListOf<PlaylistItemWrapper>()
        if (selectedFilter == "All" || selectedFilter == "YouTube") {
            if (isYouTubeLoggedIn) {
                list.addAll(youtubeUserPlaylists.map { PlaylistItemWrapper.YouTube(it) })
            }
        }
        if (selectedFilter == "All" || selectedFilter == "Online") {
            list.addAll(onlinePlaylists.map { PlaylistItemWrapper.CustomOnline(it) })
        }
        if (selectedFilter == "All" || selectedFilter == "Local") {
            list.addAll(offlinePlaylists.map { PlaylistItemWrapper.Local(it) })
        }
        list
    }

    // Filter items by search query
    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter { item ->
            when (item) {
                is PlaylistItemWrapper.YouTube -> item.playlist.title.contains(searchQuery, ignoreCase = true)
                is PlaylistItemWrapper.CustomOnline -> item.playlist.name.contains(searchQuery, ignoreCase = true)
                is PlaylistItemWrapper.Local -> item.playlist.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add to Playlist",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Track info preview capsule
            if (track != null || effectiveTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TrackPreviewCapsule(
                    track = track ?: effectiveTracks.firstOrNull(),
                    trackCount = effectiveTracks.size,
                    accentColor = AccentOrange,
                    isDarkMode = isDarkMode,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Inline Search Bar with proper 52dp height, centered vertical alignment, and clean padding
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search playlists...",
                        color = textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange.copy(alpha = 0.5f),
                    unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                    focusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f),
                    unfocusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category filter chips with animated icons & spring scale feedback
            val filterOptions = mutableListOf<Triple<String, Int, ImageVector>>()
            filterOptions.add(Triple("All", allItems.size, Icons.AutoMirrored.Filled.QueueMusic))
            if (isYouTubeLoggedIn) {
                filterOptions.add(Triple("YouTube", youtubeUserPlaylists.size, Icons.Default.CloudQueue))
            }
            filterOptions.add(Triple("Online", onlinePlaylists.size, Icons.Default.Stream))
            filterOptions.add(Triple("Local", offlinePlaylists.size, Icons.Default.Folder))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { (filter, count, icon) ->
                    val isSelected = selectedFilter == filter
                    CategoryFilterChip(
                        label = filter,
                        count = count,
                        icon = icon,
                        isSelected = isSelected,
                        activeColor = AccentOrange,
                        isDarkMode = isDarkMode,
                        textPrimary = textPrimary,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Elevated 'Create New Playlist' Card with Accent Gradient Icon and Spring Interaction
            CreatePlaylistElevatedCard(
                selectedFilter = selectedFilter,
                accentColor = AccentOrange,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isDarkMode = isDarkMode,
                onClick = {
                    when (selectedFilter) {
                        "YouTube" -> showCreateDialogType = "youtube"
                        "Online" -> showCreateDialogType = "online"
                        "Local" -> showCreateDialogType = "local"
                        else -> showCreateChoiceDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No playlists matching \"$searchQuery\""
                            else if (selectedFilter == "YouTube" && !isYouTubeLoggedIn) "Sign in to YouTube Music to view your playlists"
                            else "No playlists found in this category",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Text(
                    text = if (searchQuery.isNotBlank()) "Search Results (${filteredItems.size})" else "Choose Destination Playlist",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        when (item) {
                            is PlaylistItemWrapper.YouTube -> {
                                YouTubePlaylistItemRow(
                                    ytPlaylist = item.playlist,
                                    effectiveTracks = effectiveTracks,
                                    viewModel = viewModel,
                                    cardBg = cardBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isDarkMode = isDarkMode,
                                    onDismiss = onDismiss
                                )
                            }

                            is PlaylistItemWrapper.CustomOnline -> {
                                CustomOnlinePlaylistItemRow(
                                    onlinePl = item.playlist,
                                    effectiveTracks = effectiveTracks,
                                    viewModel = viewModel,
                                    cardBg = cardBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isDarkMode = isDarkMode,
                                    onDismiss = onDismiss
                                )
                            }

                            is PlaylistItemWrapper.Local -> {
                                LocalPlaylistItemRow(
                                    localPl = item.playlist,
                                    effectiveTracks = effectiveTracks,
                                    viewModel = viewModel,
                                    cardBg = cardBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isDarkMode = isDarkMode,
                                    onDismiss = onDismiss
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
private fun TrackPreviewCapsule(
    track: TrackEntity?,
    trackCount: Int,
    accentColor: Color,
    isDarkMode: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    if (track == null && trackCount == 0) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (trackCount > 1) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$trackCount tracks selected",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Adding multi-track selection",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Batch",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (track != null) {
                val artworkUrl = track.artworkUrl ?: if (track.filePath.startsWith("online:")) {
                    val vid = track.filePath.removePrefix("online:")
                    if (vid.isNotBlank()) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else null
                } else if (track.albumId > 0) {
                    "content://media/external/audio/albumart/${track.albumId}"
                } else null

                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Track",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    count: Int,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    isDarkMode: Boolean,
    textPrimary: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ChipScale_$label"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else textPrimary,
        label = "ChipTextColor_$label"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) activeColor
                else if (isDarkMode) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) activeColor.copy(alpha = 0.6f)
                else if (isDarkMode) Color.White.copy(alpha = 0.1f)
                else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "ChipIconScale_$label"
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White
                else if (isDarkMode) Color.White.copy(alpha = 0.7f)
                else Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Text(
                text = when (label) {
                    "All" -> "All ($count)"
                    "YouTube" -> "YouTube Music ($count)"
                    "Online" -> "Online ($count)"
                    "Local" -> "Local ($count)"
                    else -> "$label ($count)"
                },
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CreatePlaylistElevatedCard(
    selectedFilter: String,
    accentColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "CreatePlaylistCardScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.75f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Create New Playlist",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (selectedFilter) {
                        "YouTube" -> "Create on YouTube Music"
                        "Online" -> "Create Online Playlist with Cloud Sync"
                        "Local" -> "Create Local Device Playlist"
                        else -> "YouTube, Online, or Device playlist"
                    },
                    color = textSecondary,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDarkMode) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun YouTubePlaylistItemRow(
    ytPlaylist: InnerTubePlaylist,
    effectiveTracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var ytTracks by remember(ytPlaylist.id) { mutableStateOf<List<TrackEntity>?>(null) }

    LaunchedEffect(ytPlaylist.id) {
        val query = if (ytPlaylist.id == "LM") "browse:LM" else "browse:${ytPlaylist.id}"
        ytTracks = viewModel.getCuratedPlaylistTracks(query)
    }

    val isAlreadyInYtPlaylist = remember(ytTracks, effectiveTracks) {
        if (effectiveTracks.size == 1) {
            val single = effectiveTracks.first()
            val targetVideoId = if (single.filePath.startsWith("online:")) single.filePath.removePrefix("online:") else ""
            ytTracks?.any { ytTrack ->
                ytTrack.id == single.id ||
                (targetVideoId.isNotBlank() && ytTrack.filePath == single.filePath) ||
                (ytTrack.title.equals(single.title, ignoreCase = true) && ytTrack.artist.equals(single.artist, ignoreCase = true))
            } ?: false
        } else false
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "YtRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (isAlreadyInYtPlaylist) Color(0xFF34C759).copy(alpha = 0.3f)
                else (if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (isAlreadyInYtPlaylist && effectiveTracks.size == 1) {
                    Toast.makeText(context, "Already in \"${ytPlaylist.title}\"", Toast.LENGTH_SHORT).show()
                    onDismiss()
                    return@clickable
                }
                Toast.makeText(context, "Adding to \"${ytPlaylist.title}\"...", Toast.LENGTH_SHORT).show()
                effectiveTracks.forEach { trk ->
                    viewModel.addTrackToYouTubePlaylist(ytPlaylist, trk) { _ -> }
                }
                Toast.makeText(context, "Added ${effectiveTracks.size} song(s) to \"${ytPlaylist.title}\" (YouTube Music)", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!ytPlaylist.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ytPlaylist.artworkUrl,
                contentDescription = ytPlaylist.title,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFCC0000)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color.White)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ytPlaylist.title,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(YouTubeRed.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "YouTube",
                        color = YouTubeRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (ytPlaylist.subtitle.isNotBlank()) ytPlaylist.subtitle else "YouTube Music Playlist",
                    color = textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAlreadyInYtPlaylist) {
                    Text(
                        text = "• Already added",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        AnimatedContent(
            targetState = isAlreadyInYtPlaylist,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "YtCheckmarkAnim"
        ) { alreadyIn ->
            if (alreadyIn) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Already Added",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(YouTubeRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = YouTubeRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomOnlinePlaylistItemRow(
    onlinePl: OnlinePlaylistEntity,
    effectiveTracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userTracksFlow = remember(onlinePl.id) { viewModel.getOnlinePlaylistTracks(onlinePl.id) }
    val userTracks by userTracksFlow.collectAsState(initial = emptyList())
    val isAlreadyInPlaylist = remember(userTracks, effectiveTracks) {
        if (effectiveTracks.size == 1) {
            val single = effectiveTracks.first()
            userTracks.any { it.id == single.id || (it.title.equals(single.title, ignoreCase = true) && it.artist.equals(single.artist, ignoreCase = true)) }
        } else false
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "CustomOnlineRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (isAlreadyInPlaylist) Color(0xFF34C759).copy(alpha = 0.3f)
                else (if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (isAlreadyInPlaylist && effectiveTracks.size == 1) {
                    Toast.makeText(context, "Already in \"${onlinePl.name}\"", Toast.LENGTH_SHORT).show()
                    onDismiss()
                    return@clickable
                }
                if (effectiveTracks.size == 1) {
                    viewModel.addTrackToOnlinePlaylist(onlinePl.id, effectiveTracks.first())
                } else {
                    viewModel.addTracksToOnlinePlaylist(onlinePl.id, effectiveTracks)
                }
                Toast.makeText(context, "Added ${effectiveTracks.size} track(s) to \"${onlinePl.name}\"", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaylistCollageArt(
            tracks = userTracks,
            modifier = Modifier.size(44.dp),
            cornerRadius = 10.dp,
            fallbackGradient = PurpleGradient
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = onlinePl.name,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF8E2DE2).copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Online",
                        color = Color(0xFF8E2DE2),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (!onlinePl.description.isNullOrBlank()) onlinePl.description else "${userTracks.size} songs",
                    color = textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAlreadyInPlaylist) {
                    Text(
                        text = "• Already added",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        AnimatedContent(
            targetState = isAlreadyInPlaylist,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "CustomOnlineCheckmarkAnim"
        ) { alreadyIn ->
            if (alreadyIn) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Already Added",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AccentOrange.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistItemRow(
    localPl: PlaylistEntity,
    effectiveTracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localTracksFlow = remember(localPl.id) { viewModel.getPlaylistTracks(localPl.id) }
    val localTracks by localTracksFlow.collectAsState(initial = emptyList())
    val isAlreadyInPlaylist = remember(localTracks, effectiveTracks) {
        if (effectiveTracks.size == 1) {
            val single = effectiveTracks.first()
            localTracks.any { it.id == single.id || (it.title.equals(single.title, ignoreCase = true) && it.artist.equals(single.artist, ignoreCase = true)) }
        } else false
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "LocalRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (isAlreadyInPlaylist) Color(0xFF34C759).copy(alpha = 0.3f)
                else (if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (isAlreadyInPlaylist && effectiveTracks.size == 1) {
                    Toast.makeText(context, "Already in \"${localPl.name}\"", Toast.LENGTH_SHORT).show()
                    onDismiss()
                    return@clickable
                }
                if (effectiveTracks.size == 1) {
                    viewModel.addTrackToPlaylist(localPl.id, effectiveTracks.first().id)
                } else {
                    viewModel.addTracksToPlaylist(localPl.id, effectiveTracks.map { it.id })
                }
                Toast.makeText(context, "Added ${effectiveTracks.size} track(s) to \"${localPl.name}\"", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaylistCollageArt(
            tracks = localTracks,
            modifier = Modifier.size(44.dp),
            cornerRadius = 10.dp,
            fallbackGradient = listOf(Color(0xFF34C759), Color(0xFF1B8A38))
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = localPl.name,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF34C759).copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Local",
                        color = Color(0xFF34C759),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${localTracks.size} songs",
                    color = textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAlreadyInPlaylist) {
                    Text(
                        text = "• Already added",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        AnimatedContent(
            targetState = isAlreadyInPlaylist,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "LocalCheckmarkAnim"
        ) { alreadyIn ->
            if (alreadyIn) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Already Added",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistChoiceDialog(
    isYouTubeLoggedIn: Boolean,
    isDarkMode: Boolean = true,
    onSelectType: (type: String) -> Unit, // "youtube", "online", "local"
    onDismiss: () -> Unit
) {
    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
    val sheetBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1A1A2E)) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        title = {
            Text(
                text = "Create New Playlist",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isYouTubeLoggedIn) {
                    ChoiceDialogOption(
                        title = "YouTube Music Playlist",
                        subtitle = "Syncs with your YouTube account",
                        icon = Icons.Default.CloudQueue,
                        iconTint = YouTubeRed,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { onSelectType("youtube") }
                    )
                }

                ChoiceDialogOption(
                    title = "Custom Online Playlist",
                    subtitle = "Online streaming playlist with cloud backup",
                    icon = Icons.Default.Stream,
                    iconTint = AccentOrange,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onSelectType("online") }
                )

                ChoiceDialogOption(
                    title = "Device Local Playlist",
                    subtitle = "Stored locally on your phone",
                    icon = Icons.Default.LibraryMusic,
                    iconTint = Color(0xFF34C759),
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onSelectType("local") }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

@Composable
private fun ChoiceDialogOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ChoiceOptionScale_$title"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = textSecondary, fontSize = 11.sp)
            }
        }
    }
}
