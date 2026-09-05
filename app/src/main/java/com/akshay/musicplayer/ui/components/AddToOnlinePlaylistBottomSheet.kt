package com.akshay.musicplayer.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val AccentOrange = Color(0xFFFF512F)
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
    var showCreateDialogType by remember { mutableStateOf<String?>(null) } // "online", "local", "youtube"
    var showCreateChoiceDialog by remember { mutableStateOf(false) }

    val sheetBg = if (isDarkMode) Color(0xFF1A1A2E) else Color(0xFFFFFFFF)
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
                        Toast.makeText(context, "Created YouTube Playlist \"$name\"", Toast.LENGTH_SHORT).show()
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
                viewModel.createOnlinePlaylist(name, desc)
                showCreateDialogType = null
                Toast.makeText(context, "Created Online Playlist \"$name\"", Toast.LENGTH_SHORT).show()
            },
            isDarkMode = isDarkMode
        )
    }

    // Dialog for creating Local Playlist
    if (showCreateDialogType == "local") {
        CreatePlaylistDialog(
            isDarkMode = isDarkMode,
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialogType = null
                Toast.makeText(context, "Created Local Playlist \"$name\"", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCreateDialogType = null }
        )
    }

    // Choice Dialog to pick playlist type if clicking "Create New"
    if (showCreateChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showCreateChoiceDialog = false },
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
                        Surface(
                            onClick = {
                                showCreateChoiceDialog = false
                                showCreateDialogType = "youtube"
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = YouTubeRed)
                                Column {
                                    Text("YouTube Music Playlist", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Syncs with your YouTube account", color = textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showCreateChoiceDialog = false
                            showCreateDialogType = "online"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Stream, contentDescription = null, tint = AccentOrange)
                            Column {
                                Text("Custom Online Playlist", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Online streaming playlist with cloud backup", color = textSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showCreateChoiceDialog = false
                            showCreateDialogType = "local"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color(0xFF34C759))
                            Column {
                                Text("Device Local Playlist", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Stored locally on your phone", color = textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCreateChoiceDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add to Playlist",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (track != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.title} • ${track.artist}",
                        color = AccentOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else if (effectiveTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${effectiveTracks.size} tracks selected",
                        color = AccentOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Filter Categories
            val filterOptions = mutableListOf("All")
            if (isYouTubeLoggedIn) filterOptions.add("YouTube")
            filterOptions.add("Online")
            filterOptions.add("Local")

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) AccentOrange
                                else if (isDarkMode) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (filter) {
                                "All" -> "All (${allItems.size})"
                                "YouTube" -> "YouTube Music (${youtubeUserPlaylists.size})"
                                "Online" -> "Online Playlists (${onlinePlaylists.size})"
                                "Local" -> "Local Playlists (${offlinePlaylists.size})"
                                else -> filter
                            },
                            color = if (isSelected) Color.White else textPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Create New Playlist Option Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentOrange.copy(alpha = 0.15f))
                    .clickable {
                        when (selectedFilter) {
                            "YouTube" -> showCreateDialogType = "youtube"
                            "Online" -> showCreateDialogType = "online"
                            "Local" -> showCreateDialogType = "local"
                            else -> showCreateChoiceDialog = true
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
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
                            "Online" -> "Create Online Playlist"
                            "Local" -> "Create Local Device Playlist"
                            else -> "YouTube, Online, or Device playlist"
                        },
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (allItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedFilter == "YouTube" && !isYouTubeLoggedIn) "Sign in to YouTube Music to view your playlists"
                            else "No playlists found in this category",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Choose Destination Playlist",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allItems) { item ->
                        when (item) {
                            is PlaylistItemWrapper.YouTube -> {
                                YouTubePlaylistItemRow(
                                    ytPlaylist = item.playlist,
                                    effectiveTracks = effectiveTracks,
                                    viewModel = viewModel,
                                    cardBg = cardBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
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
private fun YouTubePlaylistItemRow(
    ytPlaylist: InnerTubePlaylist,
    effectiveTracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable {
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
                if (isAlreadyInYtPlaylist) {
                    Text(
                        text = "(Already added)",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = if (ytPlaylist.subtitle.isNotBlank()) ytPlaylist.subtitle else "YouTube Music Playlist",
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isAlreadyInYtPlaylist) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Already Added",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = YouTubeRed,
                modifier = Modifier.size(20.dp)
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable {
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
                if (isAlreadyInPlaylist) {
                    Text(
                        text = "(Already added)",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = if (!onlinePl.description.isNullOrBlank()) onlinePl.description else "${userTracks.size} songs",
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isAlreadyInPlaylist) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Already Added",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = AccentOrange,
                modifier = Modifier.size(20.dp)
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable {
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
                if (isAlreadyInPlaylist) {
                    Text(
                        text = "(Already added)",
                        color = Color(0xFF34C759),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = "${localTracks.size} songs",
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isAlreadyInPlaylist) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Already Added",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


/**
 * Backward compatibility overload for legacy usages
 */
@Composable
fun AddToOnlinePlaylistBottomSheet(
    track: TrackEntity,
    viewModel: PlayerViewModel,
    onlinePlaylists: List<OnlinePlaylistEntity> = emptyList(),
    onSelectPlaylist: (OnlinePlaylistEntity) -> Unit = {},
    isDarkMode: Boolean = true,
    onDismiss: () -> Unit
) {
    AddToPlaylistBottomSheet(
        track = track,
        viewModel = viewModel,
        isDarkMode = isDarkMode,
        onDismiss = onDismiss
    )
}
