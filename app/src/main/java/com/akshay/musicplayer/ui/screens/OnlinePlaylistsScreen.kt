package com.akshay.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.YouTubeLoginDialog
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.akshay.musicplayer.ui.viewmodel.managers.YouTubeAccount

private val OrangeAccent = Color(0xFFFF512F)
private val TextSecondary = Color(0xFF8E8E93)

data class SelectedOnlinePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val gradientColors: List<Long> = listOf(0xFF8E2DE2, 0xFF4A00E0),
    val isYouTubeUserPlaylist: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistsScreen(
    viewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onDetailVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    // YouTube Music Auth State
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youtubeUserName by viewModel.youtubeUserName.collectAsState()
    val youtubeUserHandle by viewModel.youtubeUserHandle.collectAsState()
    val youtubeUserAvatar by viewModel.youtubeUserAvatar.collectAsState()
    val youtubeSavedAccounts by viewModel.youtubeSavedAccounts.collectAsState()
    val youtubeLikedSongs by viewModel.youtubeLikedSongs.collectAsState()
    val youtubeUserPlaylists by viewModel.youtubeUserPlaylists.collectAsState()

    // Dynamic Explore & Charts Shelves
    val exploreShelves by viewModel.exploreShelves.collectAsState()
    val chartsShelves by viewModel.chartsShelves.collectAsState()
    val selectedMoodCategory by viewModel.selectedMoodCategory.collectAsState()
    val isExploreLoading by viewModel.isExploreLoading.collectAsState()

    val customOnlinePlaylists by viewModel.onlinePlaylists.collectAsState()

    var showYouTubeLoginDialog by remember { mutableStateOf(false) }
    var loginDialogCleanSession by remember { mutableStateOf(false) }
    var showAccountSwitcherDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<SelectedOnlinePlaylist?>(null) }
    var selectedCustomPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    var showCreateChoiceDialog by remember { mutableStateOf(false) }
    var showCreateDialogType by remember { mutableStateOf<String?>(null) } // "youtube", "online", "local"
    var editingPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }

    val isDetailOpen = selectedPlaylist != null || selectedCustomPlaylist != null
    LaunchedEffect(isDetailOpen) {
        onDetailVisibilityChanged(isDetailOpen)
    }

    androidx.activity.compose.BackHandler(enabled = isDetailOpen) {
        selectedPlaylist = null
        selectedCustomPlaylist = null
    }

    LaunchedEffect(Unit) {
        if (exploreShelves.isEmpty()) {
            viewModel.loadExploreAndCharts()
        }
    }

    val mainListState = rememberLazyListState()

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
            // Top Spacing & Header
            item {
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(60.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "YouTube Music Hub",
                            color = textColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Live dynamic charts, ready-made playlists & library",
                            color = textSub,
                            fontSize = 13.sp
                        )
                    }

                    // Create Playlist Button
                    IconButton(
                        onClick = { showCreateChoiceDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(OrangeAccent)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = Color.White)
                    }
                }
            }

            // Mood & Activity Category Filter Chips
            item {
                val moodCategories = listOf(
                    "All", "Chill", "Workout", "Energize", "Feel good", "Romance", 
                    "Focus", "Party", "Sad", "Sleep", "Commute", "Gaming",
                    "Pop", "Hip-hop", "Rock", "Indie & alternative", "Dance & electronic", 
                    "R&B & soul", "K-Pop", "Classical", "Hindi", "Punjabi", "Telugu", "Tamil"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(moodCategories) { mood ->
                        val isSelected = mood.equals(selectedMoodCategory, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) OrangeAccent
                                    else if (isDarkMode) Color.White.copy(alpha = 0.08f)
                                    else Color.Black.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) OrangeAccent else if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.selectMoodCategory(mood)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mood,
                                color = if (isSelected) Color.White else textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 1. YouTube Music Account Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (isYouTubeLoggedIn) listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                else listOf(Color(0xFFFF512F), Color(0xFFDD2476))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isYouTubeLoggedIn && !youtubeUserAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = youtubeUserAvatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isYouTubeLoggedIn) (youtubeUserName ?: "Connected to YouTube Music") else "Connect YouTube Music",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isYouTubeLoggedIn && !youtubeUserHandle.isNullOrBlank()) {
                                        Text(
                                            text = youtubeUserHandle!!,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (isYouTubeLoggedIn) "${youtubeLikedSongs.size} Liked Songs • ${youtubeUserPlaylists.size} Playlists" else "Sign in to sync your Liked Songs and personal playlists",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (isYouTubeLoggedIn) {
                                IconButton(
                                    onClick = {
                                        viewModel.refreshYouTubeLibrary()
                                        viewModel.loadExploreAndCharts(force = true)
                                        android.widget.Toast.makeText(context, "Refreshing library & charts...", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isYouTubeLoggedIn) {
                            Button(
                                onClick = {
                                    loginDialogCleanSession = true
                                    showYouTubeLoginDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Sign in to YouTube Music",
                                    color = Color(0xFFDD2476),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (youtubeLikedSongs.isNotEmpty()) {
                                            viewModel.playOnlinePlaylist(youtubeLikedSongs, 0)
                                            onNavigateToPlayer()
                                        } else {
                                            viewModel.refreshYouTubeLibrary()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF8E2DE2), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Liked (${youtubeLikedSongs.size})",
                                        color = Color(0xFF8E2DE2),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showAccountSwitcherDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Accounts", fontSize = 12.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.logoutYouTube() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                                ) {
                                    Text("Logout", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. User Liked Playlists (When Signed In)
            if (isYouTubeLoggedIn && youtubeUserPlaylists.isNotEmpty() && selectedMoodCategory == "All") {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "My YouTube Music Playlists",
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(youtubeUserPlaylists) { pl: InnerTubePlaylist ->
                                DynamicPlaylistCard(
                                    title = pl.title,
                                    subtitle = pl.subtitle,
                                    artworkUrl = pl.artworkUrl,
                                    isDarkMode = isDarkMode,
                                    onClick = {
                                        selectedPlaylist = SelectedOnlinePlaylist(
                                            id = pl.id,
                                            title = pl.title,
                                            subtitle = pl.subtitle,
                                            artworkUrl = pl.artworkUrl,
                                            isYouTubeUserPlaylist = true
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Loading Indicator
            if (isExploreLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = OrangeAccent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            // Empty State
            if (!isExploreLoading && exploreShelves.isEmpty() && chartsShelves.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedMoodCategory == "All") "No content available right now" else "No content found for \"$selectedMoodCategory\"",
                            color = textSub,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadExploreAndCharts(mood = selectedMoodCategory, force = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
                        ) {
                            Text("Refresh", color = Color.White)
                        }
                    }
                }
            }

            // 3. Dynamic Charts Shelves (Live from YouTube Music)
            if (chartsShelves.isNotEmpty()) {
                chartsShelves.forEach { shelf ->
                    if (shelf.tracks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shelf.title,
                                            color = textColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (shelf.subtitle.isNotBlank()) {
                                            Text(
                                                text = shelf.subtitle,
                                                color = textSub,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            val trackEntities = shelf.tracks.map { it.toTrackEntity() }
                                            viewModel.playOnlinePlaylist(trackEntities, 0)
                                            onNavigateToPlayer()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = OrangeAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play all",
                                                tint = OrangeAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Play all",
                                                color = OrangeAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(shelf.tracks) { index, track ->
                                        OnlineSongCard(
                                            track = track,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                val trackEntities = shelf.tracks.map { it.toTrackEntity() }
                                                viewModel.playOnlinePlaylist(trackEntities, index)
                                                onNavigateToPlayer()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (shelf.playlists.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = shelf.title,
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                                if (shelf.subtitle.isNotBlank()) {
                                    Text(
                                        text = shelf.subtitle,
                                        color = textSub,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                                    )
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(shelf.playlists) { pl: InnerTubePlaylist ->
                                        DynamicPlaylistCard(
                                            title = pl.title,
                                            subtitle = pl.subtitle,
                                            artworkUrl = pl.artworkUrl,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                selectedPlaylist = SelectedOnlinePlaylist(
                                                    id = pl.id,
                                                    title = pl.title,
                                                    subtitle = pl.subtitle,
                                                    artworkUrl = pl.artworkUrl
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Dynamic Explore Shelves (Live from YouTube Music)
            if (exploreShelves.isNotEmpty()) {
                exploreShelves.forEach { shelf ->
                    if (shelf.tracks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shelf.title,
                                            color = textColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (shelf.subtitle.isNotBlank()) {
                                            Text(
                                                text = shelf.subtitle,
                                                color = textSub,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            val trackEntities = shelf.tracks.map { it.toTrackEntity() }
                                            viewModel.playOnlinePlaylist(trackEntities, 0)
                                            onNavigateToPlayer()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = OrangeAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play all",
                                                tint = OrangeAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Play all",
                                                color = OrangeAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(shelf.tracks) { index, track ->
                                        OnlineSongCard(
                                            track = track,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                val trackEntities = shelf.tracks.map { it.toTrackEntity() }
                                                viewModel.playOnlinePlaylist(trackEntities, index)
                                                onNavigateToPlayer()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (shelf.playlists.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = shelf.title,
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                                if (shelf.subtitle.isNotBlank()) {
                                    Text(
                                        text = shelf.subtitle,
                                        color = textSub,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                                    )
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(shelf.playlists) { pl: InnerTubePlaylist ->
                                        DynamicPlaylistCard(
                                            title = pl.title,
                                            subtitle = pl.subtitle,
                                            artworkUrl = pl.artworkUrl,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                selectedPlaylist = SelectedOnlinePlaylist(
                                                    id = pl.id,
                                                    title = pl.title,
                                                    subtitle = pl.subtitle,
                                                    artworkUrl = pl.artworkUrl
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Custom In-App Online Playlists
            if (customOnlinePlaylists.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Custom Online Playlists",
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        val customRows = customOnlinePlaylists.chunked(2)
                        customRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                row.forEach { playlist ->
                                    CustomPlaylistCard(
                                        playlist = playlist,
                                        viewModel = viewModel,
                                        modifier = Modifier.weight(1f),
                                        isDarkMode = isDarkMode,
                                        onClick = { selectedCustomPlaylist = playlist },
                                        onSetHero = { viewModel.setHeroPlaylistId("custom_${playlist.id}") },
                                        onEdit = { editingPlaylist = playlist },
                                        onDelete = { viewModel.deleteOnlinePlaylist(playlist.id) }
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
        }

        // YouTube Music Login Dialog
        if (showYouTubeLoginDialog) {
            YouTubeLoginDialog(
                cleanSession = loginDialogCleanSession,
                onDismiss = {
                    showYouTubeLoginDialog = false
                    loginDialogCleanSession = false
                },
                onLoginSuccess = { cookie, name ->
                    viewModel.saveYouTubeCookies(cookie, name)
                }
            )
        }

        // Account Switcher Dialog
        if (showAccountSwitcherDialog) {
            AccountSwitcherDialog(
                currentAccountName = youtubeUserName,
                currentAccountHandle = youtubeUserHandle,
                savedAccounts = youtubeSavedAccounts,
                isDarkMode = isDarkMode,
                onDismiss = { showAccountSwitcherDialog = false },
                onSelectAccount = { accountId ->
                    viewModel.switchYouTubeAccount(accountId)
                    showAccountSwitcherDialog = false
                },
                onRemoveAccount = { accountId ->
                    viewModel.removeYouTubeAccount(accountId)
                },
                onAddNewAccount = {
                    showAccountSwitcherDialog = false
                    loginDialogCleanSession = true
                    showYouTubeLoginDialog = true
                },
                onLogoutCurrent = {
                    viewModel.logoutYouTube()
                    showAccountSwitcherDialog = false
                }
            )
        }

        // Choice Dialog to pick playlist type if clicking "+"
        if (showCreateChoiceDialog) {
            com.akshay.musicplayer.ui.components.CreatePlaylistChoiceDialog(
                isYouTubeLoggedIn = isYouTubeLoggedIn,
                isDarkMode = isDarkMode,
                onSelectType = { type ->
                    showCreateChoiceDialog = false
                    showCreateDialogType = type
                },
                onDismiss = { showCreateChoiceDialog = false }
            )
        }

        // Dialog for creating YouTube playlist
        if (showCreateDialogType == "youtube") {
            CreateOnlinePlaylistDialog(
                onDismiss = { showCreateDialogType = null },
                onCreate = { name, desc ->
                    viewModel.createYouTubePlaylist(name, desc ?: "") { id ->
                        if (id != null) {
                            Toast.makeText(context, "Created YouTube Playlist \"$name\"", Toast.LENGTH_SHORT).show()
                            viewModel.refreshYouTubeLibrary()
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

        // Edit Online Playlist Dialog
        if (editingPlaylist != null) {
            val plToEdit = editingPlaylist!!
            EditPlaylistDialog(
                initialName = plToEdit.name,
                initialDescription = plToEdit.description ?: "",
                onConfirm = { newName, _ ->
                    viewModel.renameOnlinePlaylist(plToEdit.id, newName)
                    editingPlaylist = null
                },
                onDismiss = { editingPlaylist = null }
            )
        }

        // Overlay for Selected Online Playlist Details
        AnimatedVisibility(
            visible = selectedPlaylist != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(androidx.compose.animation.core.tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(androidx.compose.animation.core.tween(200))
        ) {
            if (selectedPlaylist != null) {
                val pl = selectedPlaylist!!
                var playlistTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
                var isLoadingTracks by remember { mutableStateOf(true) }

                LaunchedEffect(pl.id) {
                    isLoadingTracks = true
                    val query = if (pl.id == "LM") "browse:LM" else "browse:${pl.id}"
                    val fetched = viewModel.getCuratedPlaylistTracks(query)
                    playlistTracks = fetched
                    val trueDesc = viewModel.fetchPlaylistDescription(pl.id) ?: viewModel.getPlaylistDescription(pl.id)
                    if (!trueDesc.isNullOrBlank() && trueDesc != pl.description) {
                        selectedPlaylist = pl.copy(description = trueDesc)
                    }
                    isLoadingTracks = false
                }

                OnlinePlaylistDetailScreen(
                    title = pl.title,
                    subtitle = pl.subtitle,
                    description = pl.description,
                    gradientColors = pl.gradientColors.map { Color(it) },
                    tracks = playlistTracks,
                    isLoading = isLoadingTracks,
                    isCustomUserPlaylist = pl.isYouTubeUserPlaylist,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    onBackClick = { selectedPlaylist = null },
                    onPlayAllClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.playOnlinePlaylist(playlistTracks, 0)
                            selectedPlaylist = null
                            onNavigateToPlayer()
                        }
                    },
                    onShuffleClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.playOnlineShuffle(playlistTracks)
                            selectedPlaylist = null
                            onNavigateToPlayer()
                        }
                    },
                    onPlayNextClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.playNextTracks(playlistTracks)
                            Toast.makeText(context, "Playing ${playlistTracks.size} track(s) next", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddToQueueClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.addTracksToQueue(playlistTracks)
                            Toast.makeText(context, "Added ${playlistTracks.size} track(s) to Queue", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTrackClick = { index ->
                        viewModel.playOnlinePlaylist(playlistTracks, index)
                        selectedPlaylist = null
                        onNavigateToPlayer()
                    },
                    onRemoveTrack = if (pl.isYouTubeUserPlaylist) { track ->
                        val vid = if (track.filePath.startsWith("online:")) track.filePath.removePrefix("online:")
                        else if (track.artworkUrl != null && track.artworkUrl.contains("/vi/")) track.artworkUrl.substringAfter("/vi/").substringBefore("/")
                        else ""
                        if (vid.isNotBlank()) {
                            viewModel.removeTrackFromYouTubePlaylist(pl.id, vid) { success ->
                                if (success) {
                                    playlistTracks = playlistTracks.filter { it.id != track.id }
                                    Toast.makeText(context, "Removed from YouTube Playlist", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to remove from YouTube Playlist", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else null,
                    onEditPlaylistDetails = if (pl.isYouTubeUserPlaylist) { newName, newDesc ->
                        viewModel.editYouTubePlaylistDetails(pl.id, newName, newDesc) { success ->
                            if (success) {
                                selectedPlaylist = pl.copy(title = newName, description = newDesc)
                                Toast.makeText(context, "Updated YouTube Playlist", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to update YouTube Playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                    onDeletePlaylist = if (pl.isYouTubeUserPlaylist) {
                        {
                            viewModel.deleteYouTubePlaylist(pl.id) { success ->
                                if (success) {
                                    selectedPlaylist = null
                                    Toast.makeText(context, "Deleted YouTube Playlist", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to delete YouTube Playlist", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else null
                )
            }
        }

        // Overlay for Selected Custom Playlist Details
        AnimatedVisibility(
            visible = selectedCustomPlaylist != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(androidx.compose.animation.core.tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(androidx.compose.animation.core.tween(200))
        ) {
            if (selectedCustomPlaylist != null) {
                val custom = selectedCustomPlaylist!!
                val flow = remember(custom.id) { viewModel.getOnlinePlaylistTracks(custom.id) }
                val tracks by flow.collectAsState(initial = emptyList())

                OnlinePlaylistDetailScreen(
                    title = custom.name,
                    subtitle = "${tracks.size} tracks",
                    description = custom.description,
                    gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                    tracks = tracks,
                    isLoading = false,
                    isCustomUserPlaylist = true,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    onBackClick = { selectedCustomPlaylist = null },
                    onPlayAllClick = {
                        if (tracks.isNotEmpty()) {
                            viewModel.playOnlinePlaylist(tracks, 0)
                            selectedCustomPlaylist = null
                            onNavigateToPlayer()
                        }
                    },
                    onShuffleClick = {
                        if (tracks.isNotEmpty()) {
                            viewModel.playOnlineShuffle(tracks)
                            selectedCustomPlaylist = null
                            onNavigateToPlayer()
                        }
                    },
                    onPlayNextClick = {
                        if (tracks.isNotEmpty()) {
                            viewModel.playNextTracks(tracks)
                            Toast.makeText(context, "Playing ${tracks.size} track(s) next", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddToQueueClick = {
                        if (tracks.isNotEmpty()) {
                            viewModel.addTracksToQueue(tracks)
                            Toast.makeText(context, "Added ${tracks.size} track(s) to Queue", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTrackClick = { index: Int ->
                        viewModel.playOnlinePlaylist(tracks, index)
                        selectedCustomPlaylist = null
                        onNavigateToPlayer()
                    },
                    onRemoveTrack = { track: TrackEntity ->
                        viewModel.removeTrackFromOnlinePlaylist(custom.id, track.id)
                    },
                    onMoveTrack = { fromIndex: Int, toIndex: Int ->
                        viewModel.moveTrackInOnlinePlaylist(custom.id, fromIndex, toIndex)
                    },
                    onEditPlaylistDetails = { newName: String, newDesc: String ->
                        viewModel.updateOnlinePlaylistDetails(custom.id, newName, newDesc)
                    },
                    onDeletePlaylist = {
                        viewModel.deleteOnlinePlaylist(custom.id)
                        selectedCustomPlaylist = null
                    }
                )
            }
        }
    }
}

@Composable
fun OnlineSongCard(
    track: InnerTubeTrack,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSec = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                    artworkUrl = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    thumbnailQuality = "Medium (480p)"
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist.ifBlank { "YouTube Music" },
            color = textSec,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DynamicPlaylistCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSec = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                    artworkUrl = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    thumbnailQuality = "Medium (480p)"
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = textSec,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CustomPlaylistCard(
    playlist: OnlinePlaylistEntity,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onSetHero: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tracksFlow = remember(playlist.id) { viewModel.getOnlinePlaylistTracks(playlist.id) }
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(24.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = textPrimary.copy(alpha = 0.6f))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit Details") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Playlist") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = playlist.name,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${tracks.size} track(s)",
                color = textPrimary.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CreateOnlinePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
    isDarkMode: Boolean = true
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Online Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), desc.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), desc.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
fun AccountSwitcherDialog(
    currentAccountName: String?,
    currentAccountHandle: String?,
    savedAccounts: List<YouTubeAccount>,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddNewAccount: () -> Unit,
    onLogoutCurrent: () -> Unit
) {
    val bg = if (isDarkMode) Color(0xFF1E1E24) else Color.White
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "YouTube Music Accounts",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSub)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Switch between accounts or sign in with another account:",
                    fontSize = 13.sp,
                    color = textSub
                )

                savedAccounts.forEach { acc ->
                    val isActive = (acc.name == currentAccountName && (acc.handle == currentAccountHandle || acc.handle == null))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isActive) (if (isDarkMode) Color(0xFF8E2DE2).copy(alpha = 0.25f) else Color(0xFF8E2DE2).copy(alpha = 0.12f))
                                else (if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 0.dp,
                                color = if (isActive) Color(0xFF8E2DE2) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (!isActive) onSelectAccount(acc.id)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!acc.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = acc.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = if (isActive) Color(0xFF8E2DE2) else textSub,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = acc.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color(0xFF8E2DE2),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (!acc.handle.isNullOrBlank()) {
                                    Text(
                                        text = acc.handle,
                                        fontSize = 12.sp,
                                        color = textSub,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onRemoveAccount(acc.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove Account",
                                tint = textSub,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Add Account Button
                OutlinedButton(
                    onClick = onAddNewAccount,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E2DE2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8E2DE2).copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Another Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF8E2DE2), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onLogoutCurrent) {
                Text("Log Out", color = Color.Red.copy(alpha = 0.8f))
            }
        },
        containerColor = bg,
        shape = RoundedCornerShape(24.dp)
    )
}
