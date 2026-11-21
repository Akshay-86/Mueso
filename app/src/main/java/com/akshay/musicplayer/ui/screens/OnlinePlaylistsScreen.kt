package com.akshay.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf
import com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack
import com.akshay.musicplayer.data.remote.innertube.isVideoShelf
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.domain.models.SupportedLanguages
import com.akshay.musicplayer.ui.components.LanguageBadge
import com.akshay.musicplayer.ui.components.LanguageSelectorBottomSheet
import com.akshay.musicplayer.ui.components.PlaylistCollageArt
import com.akshay.musicplayer.ui.components.SmartArtworkImage
import com.akshay.musicplayer.ui.components.YouTubeLoginDialog
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.akshay.musicplayer.ui.viewmodel.managers.YouTubeAccount

private val AccentColor: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

private val TextSecondary = Color(0xFF8E8E93)

data class SelectedOnlinePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val gradientColors: List<Long> = listOf(0xFF8E2DE2, 0xFF4A00E0),
    val isYouTubeUserPlaylist: Boolean = false,
    val initialTracks: List<TrackEntity>? = null
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
    val cardCorner = com.akshay.musicplayer.ui.theme.LocalCornerRadius.current.radiusDp

    // YouTube Music Auth State
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youtubeUserName by viewModel.youtubeUserName.collectAsState()
    val youtubeUserHandle by viewModel.youtubeUserHandle.collectAsState()
    val youtubeUserAvatar by viewModel.youtubeUserAvatar.collectAsState()
    val youtubeSavedAccounts by viewModel.youtubeSavedAccounts.collectAsState()
    val youtubeLikedSongs by viewModel.youtubeLikedSongs.collectAsState()
    val youtubeUserPlaylists by viewModel.youtubeUserPlaylists.collectAsState()

    // Dynamic Explore & Charts Shelves & Continuation
    val exploreShelves by viewModel.exploreShelves.collectAsState()
    val chartsShelves by viewModel.chartsShelves.collectAsState()
    val selectedMoodCategory by viewModel.selectedMoodCategory.collectAsState()
    val isExploreLoading by viewModel.isExploreLoading.collectAsState()
    val exploreContinuationToken by viewModel.exploreContinuationToken.collectAsState()
    val isLoadingMoreShelves by viewModel.isLoadingMoreShelves.collectAsState()

    // User's Preferred Language for Personalized Playlists
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val languagePlaylists by viewModel.languagePlaylists.collectAsState()
    val languagePlaylistShelves by viewModel.languagePlaylistShelves.collectAsState()
    val isLanguageLoading by viewModel.isLanguageLoading.collectAsState()

    // Dynamic data-driven feed shelves with title and content deduplication
    val displayShelves = remember(exploreShelves, chartsShelves, languagePlaylistShelves, languagePlaylists, selectedMoodCategory, preferredLanguage) {
        if (selectedMoodCategory == "All") {
            viewModel.combineShelves(
                homeShelves = exploreShelves,
                languageShelves = languagePlaylistShelves,
                chartsShelves = chartsShelves,
                languagePlaylists = languagePlaylists,
                preferredLanguage = preferredLanguage
            )
        } else {
            val cleanMoodShelves = exploreShelves
                .filterNot { isVideoShelf(it.title, it.subtitle) }
                .map { shelf ->
                    shelf.copy(
                        playlists = shelf.playlists.filterNot { isVideoShelf(it.title, it.subtitle) },
                        tracks = shelf.tracks.filterNot {
                            it.itemType.equals("Video", ignoreCase = true) ||
                            it.title.contains("Official Music Video", ignoreCase = true) ||
                            it.title.contains("Official Video", ignoreCase = true)
                        }
                    )
                }
                .filter { it.tracks.isNotEmpty() || it.playlists.isNotEmpty() }

            val result = mutableListOf<InnerTubeShelf>()
            val seenTitles = mutableSetOf<String>()
            val seenTrackIds = mutableSetOf<String>()
            val seenPlaylistIds = mutableSetOf<String>()
            for (shelf in cleanMoodShelves) {
                val normTitle = shelf.title.trim().lowercase()
                if (normTitle.isBlank() || seenTitles.contains(normTitle)) continue
                if (shelf.tracks.isNotEmpty()) {
                    val novelTracks = shelf.tracks.filter { !seenTrackIds.contains(it.videoId) }
                    if (novelTracks.isEmpty() && seenTrackIds.isNotEmpty()) continue
                    seenTitles.add(normTitle)
                    shelf.tracks.forEach { seenTrackIds.add(it.videoId) }
                    result.add(shelf)
                } else if (shelf.playlists.isNotEmpty()) {
                    val novelPlaylists = shelf.playlists.filter { !seenPlaylistIds.contains(it.id) }
                    if (novelPlaylists.isEmpty() && seenPlaylistIds.isNotEmpty()) continue
                    seenTitles.add(normTitle)
                    shelf.playlists.forEach { seenPlaylistIds.add(it.id) }
                    result.add(shelf)
                }
            }
            result
        }
    }

    val customOnlinePlaylists by viewModel.onlinePlaylists.collectAsState()

    var showYouTubeLoginDialog by remember { mutableStateOf(false) }
    var loginDialogCleanSession by remember { mutableStateOf(false) }
    var showAccountSwitcherDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<SelectedOnlinePlaylist?>(null) }
    var selectedCustomPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    var showCreateChoiceDialog by remember { mutableStateOf(false) }
    var showCreateDialogType by remember { mutableStateOf<String?>(null) } // "youtube", "online", "local"
    var editingPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    var showSpotifySyncScreen by remember { mutableStateOf(false) }
    var linkingSpotifyPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }

    val triggerSpotifySync: (OnlinePlaylistEntity) -> Unit = { playlist ->
        val url = playlist.spotifyUrl
        if (url.isNullOrBlank()) {
            linkingSpotifyPlaylist = playlist
        } else {
            viewModel.syncSpotifyPlaylist(
                context = context,
                playlistId = playlist.id,
                playlistName = playlist.name,
                spotifyUrl = url,
                onChecking = {
                    Toast.makeText(context, "Checking Spotify for updates...", Toast.LENGTH_SHORT).show()
                },
                onNoNewTracks = {
                    Toast.makeText(context, "All caught up! \"${playlist.name}\" is already up to date. ✨", Toast.LENGTH_LONG).show()
                },
                onNewTracksFound = { count ->
                    Toast.makeText(context, "Found $count new track(s)! Matching...", Toast.LENGTH_SHORT).show()
                    showSpotifySyncScreen = true
                },
                onError = { err ->
                    Toast.makeText(context, "Spotify Sync failed: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val isDetailOpen = selectedPlaylist != null || selectedCustomPlaylist != null
    LaunchedEffect(isDetailOpen, showSpotifySyncScreen) {
        onDetailVisibilityChanged(isDetailOpen || showSpotifySyncScreen)
        viewModel.setSpotifyImportOpen(showSpotifySyncScreen)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSpotifyImportOpen(false)
            onDetailVisibilityChanged(false)
        }
    }

    androidx.activity.compose.BackHandler(enabled = isDetailOpen || showSpotifySyncScreen) {
        if (showSpotifySyncScreen) {
            viewModel.resetSpotifyImport()
            showSpotifySyncScreen = false
        } else {
            selectedPlaylist = null
            selectedCustomPlaylist = null
        }
    }

    var lastObservedLanguage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(preferredLanguage) {
        val languageChanged = lastObservedLanguage != null && lastObservedLanguage != preferredLanguage
        lastObservedLanguage = preferredLanguage

        if (languageChanged) {
            viewModel.setPreferredLanguage(preferredLanguage)
            viewModel.loadLanguagePlaylists(preferredLanguage, force = true)
            viewModel.loadExploreAndCharts(force = true)
        } else {
            if (exploreShelves.isEmpty() || chartsShelves.isEmpty()) {
                viewModel.loadExploreAndCharts(force = false)
            }
            val isAuto = preferredLanguage.isBlank() || preferredLanguage.equals("Auto", ignoreCase = true) || preferredLanguage.equals("Default", ignoreCase = true)
            if (!isAuto && languagePlaylists.isEmpty() && languagePlaylistShelves.isEmpty()) {
                viewModel.loadLanguagePlaylists(preferredLanguage, force = false)
            }
        }
    }

    val mainListState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = mainListState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = mainListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 &&
                lastVisibleItemIndex >= totalItems - 2 &&
                !isLoadingMoreShelves &&
                !isExploreLoading &&
                !exploreContinuationToken.isNullOrBlank()
        }
    }

    LaunchedEffect(shouldLoadMore, exploreContinuationToken, isLoadingMoreShelves, isExploreLoading) {
        if (shouldLoadMore && !exploreContinuationToken.isNullOrBlank() && !isLoadingMoreShelves && !isExploreLoading) {
            viewModel.loadMoreShelves()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = mainListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
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
                            .background(AccentColor)
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
                    "R&B & soul", "K-Pop", "Classical"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dedicated Language Selector Pill (combines all languages into a single interactive selector)
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentColor.copy(alpha = if (isDarkMode) 0.16f else 0.10f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = AccentColor.copy(alpha = if (isDarkMode) 0.45f else 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showLanguageSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LanguageBadge(
                                    script = SupportedLanguages.getNativeScript(preferredLanguage),
                                    size = 22.dp,
                                    fontSize = 11.sp,
                                    accentColor = AccentColor
                                )
                                Text(
                                    text = preferredLanguage,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Language",
                                    tint = AccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    items(moodCategories, key = { it }) { mood ->
                        val isSelected = mood.equals(selectedMoodCategory, ignoreCase = true)
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) AccentColor
                            else if (isDarkMode) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f),
                            animationSpec = tween(220),
                            label = "ChipBg"
                        )
                        val chipBorderColor by animateColorAsState(
                            targetValue = if (isSelected) AccentColor
                            else if (isDarkMode) Color.White.copy(alpha = 0.15f)
                            else Color.Black.copy(alpha = 0.1f),
                            animationSpec = tween(220),
                            label = "ChipBorder"
                        )
                        val chipTextColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else textColor,
                            animationSpec = tween(200),
                            label = "ChipText"
                        )
                        val chipScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 1f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "ChipScale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = chipScale
                                    scaleY = chipScale
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .border(
                                    width = 1.dp,
                                    color = chipBorderColor,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.selectMoodCategory(mood)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val moodIcon = when (mood.lowercase()) {
                                    "all" -> Icons.Default.Explore
                                    "chill" -> Icons.Default.Spa
                                    "workout" -> Icons.Default.FitnessCenter
                                    "energize" -> Icons.Default.Bolt
                                    "feel good" -> Icons.Default.SentimentSatisfiedAlt
                                    "romance" -> Icons.Default.Favorite
                                    "focus" -> Icons.Default.Psychology
                                    "party" -> Icons.Default.Celebration
                                    "sad" -> Icons.Default.WaterDrop
                                    "sleep" -> Icons.Default.Bedtime
                                    "commute" -> Icons.Default.DirectionsCar
                                    "gaming" -> Icons.Default.SportsEsports
                                    "pop" -> Icons.Default.Mic
                                    "hip-hop" -> Icons.Default.Album
                                    "rock" -> Icons.Default.ElectricBolt
                                    "indie & alternative" -> Icons.Default.Audiotrack
                                    "dance & electronic" -> Icons.Default.GraphicEq
                                    "r&b & soul" -> Icons.Default.Nightlife
                                    "k-pop" -> Icons.Default.AutoAwesome
                                    "classical" -> Icons.Default.Piano
                                    else -> Icons.Default.MusicNote
                                }
                                Icon(
                                    imageVector = moodIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else AccentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = mood,
                                    color = chipTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
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
                                else listOf(AccentColor, MaterialTheme.colorScheme.secondary)
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

            // 2. Custom In-App Online Playlists (Top Priority)
            if (customOnlinePlaylists.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "My Custom Playlists",
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${customOnlinePlaylists.size} playlist${if (customOnlinePlaylists.size != 1) "s" else ""}",
                                color = textSub,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(customOnlinePlaylists, key = { it.id }) { playlist ->
                                CustomPlaylistCard(
                                    playlist = playlist,
                                    viewModel = viewModel,
                                    isDarkMode = isDarkMode,
                                    onClick = { selectedCustomPlaylist = playlist },
                                    onSetHero = { viewModel.setHeroPlaylistId("custom_${playlist.id}") },
                                    onEdit = { editingPlaylist = playlist },
                                    onDelete = { viewModel.deleteOnlinePlaylist(playlist.id) },
                                    onSyncSpotify = { triggerSpotifySync(playlist) },
                                    onLinkSpotify = { linkingSpotifyPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }

            // 3. User Liked Playlists (When Signed In)
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
                            itemsIndexed(items = youtubeUserPlaylists, key = { index, pl -> "${pl.id}_$index" }) { _, pl: InnerTubePlaylist ->
                                PlaylistSquareCard(
                                    playlist = pl,
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

            // 4. Dedicated Data-Driven Dynamic Feed Shelves
            val showLanguageSkeleton = isLanguageLoading || (displayShelves.isEmpty() && (isLanguageLoading || isExploreLoading))

            if (showLanguageSkeleton) {
                item(key = "language_skeleton_section") {
                    val shimmerBrush = rememberShimmerBrush(isDarkMode = isDarkMode)
                    LanguageSkeletonSection(
                        isDarkMode = isDarkMode,
                        shimmerBrush = shimmerBrush
                    )
                }
            } else if (displayShelves.isEmpty() && !isExploreLoading && !isLanguageLoading) {
                item(key = "empty_or_offline_state") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedMoodCategory == "All") {
                                        if (preferredLanguage.equals("Auto", ignoreCase = true) || preferredLanguage.equals("Default", ignoreCase = true)) "Couldn't load music feed" else "Couldn't load $preferredLanguage music"
                                    } else "No content found for \"$selectedMoodCategory\"",
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Check connection & retry",
                                    color = textSub,
                                    fontSize = 13.sp
                                )
                            }
                            Button(
                                onClick = {
                                    if (selectedMoodCategory == "All") {
                                        viewModel.loadLanguagePlaylists(preferredLanguage, force = true)
                                        viewModel.loadExploreAndCharts(force = true)
                                    } else {
                                        viewModel.loadExploreAndCharts(mood = selectedMoodCategory, force = true)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Retry", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                displayShelves.forEach { shelf ->
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = "song_shelf_${shelf.title}_${shelf.tracks.firstOrNull()?.videoId ?: ""}") {
                            OnlineSongShelf(
                                shelf = shelf,
                                isDarkMode = isDarkMode,
                                textColor = textColor,
                                textSub = textSub,
                                onSeeAll = {
                                    val trackEntities = shelf.tracks.map { it.toTrackEntity().copy(isVideo = false) }
                                    selectedPlaylist = SelectedOnlinePlaylist(
                                        id = "shelf_${shelf.title}",
                                        title = shelf.title,
                                        subtitle = shelf.subtitle.ifBlank { "${shelf.tracks.size} songs" },
                                        initialTracks = trackEntities
                                    )
                                },
                                onPlayAll = {
                                    val trackEntities = shelf.tracks.map { it.toTrackEntity().copy(isVideo = false) }
                                    viewModel.playOnlinePlaylist(trackEntities, 0)
                                    onNavigateToPlayer()
                                },
                                onTrackClick = { globalIndex ->
                                    val trackEntities = shelf.tracks.map { it.toTrackEntity().copy(isVideo = false) }
                                    viewModel.playOnlinePlaylist(trackEntities, globalIndex)
                                    onNavigateToPlayer()
                                }
                            )
                        }
                    } else if (shelf.playlists.isNotEmpty()) {
                        item(key = "playlist_shelf_${shelf.title}_${shelf.playlists.firstOrNull()?.id ?: ""}") {
                            val isAuto = preferredLanguage.equals("Auto", ignoreCase = true) || preferredLanguage.equals("Default", ignoreCase = true)
                            val badgeScript = if (!isAuto && (shelf.title.contains("Featured", ignoreCase = true) || shelf.subtitle.contains(preferredLanguage, ignoreCase = true))) {
                                SupportedLanguages.getNativeScript(preferredLanguage)
                            } else {
                                null
                            }
                            OnlinePlaylistShelf(
                                title = shelf.title,
                                subtitle = shelf.subtitle,
                                playlists = shelf.playlists,
                                isDarkMode = isDarkMode,
                                textColor = textColor,
                                textSub = textSub,
                                badgeScript = badgeScript,
                                onPlaylistClick = { pl ->
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

            // Loading Indicator
            if (isExploreLoading && !isLanguageLoading && displayShelves.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentColor,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            if (isLoadingMoreShelves) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentColor,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
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

        // Dedicated Music Language Bottom Sheet
        if (showLanguageSheet) {
            LanguageSelectorBottomSheet(
                selectedLanguage = preferredLanguage,
                isDarkMode = isDarkMode,
                onLanguageSelected = { newLang ->
                    viewModel.setPreferredLanguage(newLang)
                    viewModel.loadLanguagePlaylists(newLang, force = true)
                    viewModel.loadExploreAndCharts(force = true)
                    showLanguageSheet = false
                },
                onDismiss = { showLanguageSheet = false }
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
                    if (pl.initialTracks != null && pl.initialTracks.isNotEmpty()) {
                        playlistTracks = pl.initialTracks
                        isLoadingTracks = false
                    } else {
                        isLoadingTracks = true
                        val query = when {
                            pl.id.startsWith("query:") -> pl.id.removePrefix("query:")
                            pl.id.startsWith("browse:") -> pl.id
                            pl.id == "LM" -> "browse:LM"
                            else -> "browse:${pl.id}"
                        }
                        val fetched = viewModel.getCuratedPlaylistTracks(query)
                        playlistTracks = fetched
                        if (pl.id.startsWith("VL") || pl.id.startsWith("MPRE") || pl.id.startsWith("RD") || pl.id == "LM") {
                            val trueDesc = viewModel.fetchPlaylistDescription(pl.id) ?: viewModel.getPlaylistDescription(pl.id)
                            if (!trueDesc.isNullOrBlank() && trueDesc != pl.description) {
                                selectedPlaylist = pl.copy(description = trueDesc)
                            }
                        }
                        isLoadingTracks = false
                    }
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
                    artworkUrl = pl.artworkUrl,
                    onDownloadTrack = { track -> viewModel.downloadOnlineTrack(context, track) },
                    onBackClick = { selectedPlaylist = null },
                    onPlayAllClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.playOnlinePlaylist(playlistTracks, 0, pl)
                            selectedPlaylist = null
                            onNavigateToPlayer()
                        }
                    },
                    onShuffleClick = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.playOnlineShuffle(playlistTracks, pl)
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
                        viewModel.playOnlinePlaylist(playlistTracks, index, pl)
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
                val custom = customOnlinePlaylists.find { it.id == selectedCustomPlaylist?.id } ?: selectedCustomPlaylist!!
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
                    spotifyUrl = custom.spotifyUrl,
                    onSyncSpotify = if (!custom.spotifyUrl.isNullOrBlank()) {
                        { triggerSpotifySync(custom) }
                    } else null,
                    onLinkSpotify = { newUrl ->
                        viewModel.updateOnlinePlaylistSpotifyUrl(custom.id, newUrl)
                        triggerSpotifySync(custom.copy(spotifyUrl = newUrl))
                    },
                    onUnlinkSpotify = {
                        viewModel.updateOnlinePlaylistSpotifyUrl(custom.id, null)
                        Toast.makeText(context, "Unlinked from Spotify", Toast.LENGTH_SHORT).show()
                    },
                    onDownloadTrack = { track -> viewModel.downloadOnlineTrack(context, track) },
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

        // Overlay for Spotify Sync Screen
        AnimatedVisibility(
            visible = showSpotifySyncScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(androidx.compose.animation.core.tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(androidx.compose.animation.core.tween(250))
        ) {
            SpotifyImportScreen(
                viewModel = viewModel,
                onBackClick = {
                    viewModel.resetSpotifyImport()
                    showSpotifySyncScreen = false
                }
            )
        }

        // Dialog for linking Spotify to an existing playlist
        if (linkingSpotifyPlaylist != null) {
            val targetPlaylist = linkingSpotifyPlaylist!!
            var inputUrl by remember { mutableStateOf(targetPlaylist.spotifyUrl ?: "") }
            AlertDialog(
                onDismissRequest = { linkingSpotifyPlaylist = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                        }
                        Text("Link Spotify Playlist", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Paste the public Spotify link for \"${targetPlaylist.name}\" to enable 1-tap resyncing in the future:",
                            color = if (isDarkMode) TextSecondary else Color(0xFF6E6E73),
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = { Text("https://open.spotify.com/playlist/...", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = inputUrl.trim()
                            if (trimmed.isNotBlank()) {
                                viewModel.updateOnlinePlaylistSpotifyUrl(targetPlaylist.id, trimmed)
                                linkingSpotifyPlaylist = null
                                triggerSpotifySync(targetPlaylist.copy(spotifyUrl = trimmed))
                            }
                        },
                        enabled = inputUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("Link & Sync", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { linkingSpotifyPlaylist = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color.White
            )
        }
    }
}

@Composable
fun OnlinePlaylistShelf(
    title: String,
    subtitle: String,
    playlists: List<InnerTubePlaylist>,
    isDarkMode: Boolean,
    textColor: Color,
    textSub: Color,
    badgeScript: String? = null,
    onPlaylistClick: (InnerTubePlaylist) -> Unit
) {
    if (playlists.isEmpty()) return

    Column(modifier = Modifier.padding(top = 18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!badgeScript.isNullOrBlank()) {
                    LanguageBadge(
                        script = badgeScript,
                        size = 32.dp,
                        fontSize = 14.sp,
                        accentColor = AccentColor
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = textSub,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items = playlists, key = { index, pl -> "${pl.id}_$index" }) { _, pl ->
                PlaylistSquareCard(
                    playlist = pl,
                    isDarkMode = isDarkMode,
                    onClick = { onPlaylistClick(pl) }
                )
            }
        }
    }
}

@Composable
fun OnlinePlaylistShelf(
    shelf: InnerTubeShelf,
    isDarkMode: Boolean,
    textColor: Color,
    textSub: Color,
    badgeScript: String? = null,
    onPlaylistClick: (InnerTubePlaylist) -> Unit
) {
    OnlinePlaylistShelf(
        title = shelf.title,
        subtitle = shelf.subtitle,
        playlists = shelf.playlists,
        isDarkMode = isDarkMode,
        textColor = textColor,
        textSub = textSub,
        badgeScript = badgeScript,
        onPlaylistClick = onPlaylistClick
    )
}

@Composable
fun OnlineSongShelf(
    shelf: InnerTubeShelf,
    isDarkMode: Boolean,
    textColor: Color,
    textSub: Color,
    onSeeAll: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = shelf.title,
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (shelf.subtitle.isNotBlank()) {
                    Text(
                        text = shelf.subtitle,
                        color = textSub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Play all" Pill
                Surface(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkMode) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play all",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play all",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "See all" Button
                Surface(
                    onClick = onSeeAll,
                    shape = RoundedCornerShape(20.dp),
                    color = AccentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "See all",
                            color = AccentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "See all",
                            tint = AccentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Multi-row chunks of songs in LazyRow (4 songs per column chunk)
        val songChunks = remember(shelf.tracks) { shelf.tracks.chunked(4) }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = songChunks,
                key = { chunkIndex, _ -> "chunk_${shelf.title}_$chunkIndex" }
            ) { chunkIndex, chunk ->
                Column(
                    modifier = Modifier
                        .fillParentMaxWidth(0.85f)
                        .widthIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chunk.forEachIndexed { itemIndex, track ->
                        val globalIndex = chunkIndex * 4 + itemIndex
                        CompactSongRowItem(
                            track = track,
                            isDarkMode = isDarkMode,
                            onClick = { onTrackClick(globalIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactSongRowItem(
    track: InnerTubeTrack,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(track.artworkUrl) {
        track.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        }
    }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSec = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 48x48dp rounded cover art thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!track.artworkUrl.isNullOrBlank()) {
                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                    artworkUrl = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    thumbnailQuality = "Low (240p)"
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song title and artist/album
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = remember(track.artist, track.album) {
                val artist = track.artist.ifBlank { "YouTube Music" }
                val album = track.album?.ifBlank { null }
                if (album != null && !album.equals("YouTube Music", ignoreCase = true) && !album.equals("Music Video", ignoreCase = true)) {
                    "$artist • $album"
                } else {
                    artist
                }
            }
            Text(
                text = subtitle,
                color = textSec,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = textSec.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun OnlineSongCard(
    track: InnerTubeTrack,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    CompactSongRowItem(track = track, isDarkMode = isDarkMode, onClick = onClick)
}

@Composable
fun PlaylistSquareCard(
    playlist: InnerTubePlaylist,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(playlist.artworkUrl) {
        playlist.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        }
    }
    DynamicPlaylistCard(
        title = playlist.title,
        subtitle = playlist.subtitle,
        artworkUrl = playlist.artworkUrl,
        isDarkMode = isDarkMode,
        onClick = onClick
    )
}

@Composable
fun DynamicPlaylistCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isDarkMode: Boolean,
    gradientColors: List<Long>? = null,
    onClick: () -> Unit
) {
    val isPureBlack = LocalIsPureBlack.current
    val cardBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1C1C1E)) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) (if (isPureBlack) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)) else Color(0xFFE2E2E8)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSec = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(artworkUrl) {
        artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .width(176.dp)
            .height(242.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (!artworkUrl.isNullOrBlank()) {
                        Modifier.background(Color.Black.copy(alpha = 0.2f))
                    } else if (gradientColors != null && gradientColors.size >= 2) {
                        Modifier.background(Brush.linearGradient(gradientColors.map { Color(it) }))
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.2f))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!artworkUrl.isNullOrBlank()) {
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
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = textSec,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    onDelete: () -> Unit,
    onSyncSpotify: (() -> Unit)? = null,
    onLinkSpotify: (() -> Unit)? = null
) {
    val tracksFlow = remember(playlist.id) { viewModel.getOnlinePlaylistTracks(playlist.id) }
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val isPureBlack = LocalIsPureBlack.current
    val cardBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1C1C1E)) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) (if (isPureBlack) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)) else Color(0xFFE2E2E8)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSec = if (isDarkMode) TextSecondary else Color(0xFF6E6E73)

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(176.dp)
            .height(242.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            PlaylistCollageArt(
                tracks = tracks,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp
            )

            // 3-Dots Menu Button overlay in top-end corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                val menuCorner = com.akshay.musicplayer.ui.theme.LocalCornerRadius.current.radiusDp
                val menuBg = if (isPureBlack) Color(0xFF141414) else if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFFFFFF)
                val menuBorderColor = if (isPureBlack) Color.White.copy(alpha = 0.14f) else if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                val itemTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
                val itemIconTint = if (isDarkMode) Color.White.copy(alpha = 0.70f) else Color(0xFF6E6E73)

                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Playlist options",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.border(1.dp, menuBorderColor, RoundedCornerShape(menuCorner)),
                    shape = RoundedCornerShape(menuCorner),
                    containerColor = menuBg,
                    shadowElevation = 10.dp
                ) {
                    if (!playlist.spotifyUrl.isNullOrBlank() && onSyncSpotify != null) {
                        DropdownMenuItem(
                            text = { Text("Sync with Spotify", color = itemTextColor) },
                            onClick = { showMenu = false; onSyncSpotify() },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF1DB954)) }
                        )
                    } else if (onLinkSpotify != null) {
                        DropdownMenuItem(
                            text = { Text("Link Spotify", color = itemTextColor) },
                            onClick = { showMenu = false; onLinkSpotify() },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF1DB954)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit Details", color = itemTextColor) },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = itemIconTint) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = Color(0xFFEF5350)) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350)) }
                    )
                }
            }

            // Quick Play Button in bottom-end corner
            if (tracks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .shadow(6.dp, CircleShape, spotColor = AccentColor)
                        .clip(CircleShape)
                        .background(AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = playlist.name,
                color = textPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                color = textSec,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
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
    val cardCorner = com.akshay.musicplayer.ui.theme.LocalCornerRadius.current.radiusDp

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

@Composable
fun rememberShimmerBrush(isDarkMode: Boolean): Brush {
    val isPureBlack = LocalIsPureBlack.current
    val shimmerColors = if (isDarkMode) {
        if (isPureBlack) {
            listOf(
                Color(0xFF101010),
                Color(0xFF262626),
                Color(0xFF101010)
            )
        } else {
            listOf(
                Color(0xFF242426),
                Color(0xFF3C3C40),
                Color(0xFF242426)
            )
        }
    } else {
        listOf(
            Color(0xFFE5E5EA),
            Color(0xFFF2F2F7),
            Color(0xFFE5E5EA)
        )
    }

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslation"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun ShimmerShelfHeader(
    shimmerBrush: Brush,
    hasPillPlaceholder: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            // Title placeholder bar
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Subtitle placeholder bar
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
        if (hasPillPlaceholder) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun ShimmerCardSkeletonItem(
    isDarkMode: Boolean,
    shimmerBrush: Brush
) {
    val isPureBlack = LocalIsPureBlack.current
    val cardBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1C1C1E)) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) (if (isPureBlack) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)) else Color(0xFFE2E2E8)

    Column(
        modifier = Modifier
            .width(176.dp)
            .height(242.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        // 160x160dp square rounded box
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 2 lines of text skeleton below
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
    }
}

@Composable
fun CompactSongRowSkeletonItem(
    shimmerBrush: Brush
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 48x48dp rounded thumbnail skeleton
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Title text bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Artist/album text bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
fun LanguageSkeletonSection(
    isDarkMode: Boolean,
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Shimmer shelf 1 ("Featured playlists for you" placeholder):
        // - Title & subtitle shimmer placeholder bars
        // - Horizontal row (LazyRow) of 4 card skeleton items (160x160dp square rounded box + 2 lines of text skeleton below)
        Column(modifier = Modifier.padding(top = 18.dp)) {
            ShimmerShelfHeader(shimmerBrush = shimmerBrush)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(4) {
                    ShimmerCardSkeletonItem(isDarkMode = isDarkMode, shimmerBrush = shimmerBrush)
                }
            }
        }

        // Shimmer shelf 2 ("Trending songs for you" placeholder):
        // - Title & subtitle shimmer placeholder bars
        // - 4 compact song skeleton rows (48x48dp rounded thumbnail skeleton + 2 title/artist text bars)
        Column(modifier = Modifier.padding(top = 18.dp)) {
            ShimmerShelfHeader(shimmerBrush = shimmerBrush, hasPillPlaceholder = true)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) {
                    CompactSongRowSkeletonItem(shimmerBrush = shimmerBrush)
                }
            }
        }

        // Shimmer shelf 3 ("Brb, Being Nostalgic!" placeholder):
        // - Title & subtitle shimmer placeholder bars
        // - Horizontal row of 4 card skeleton items (160x160dp)
        Column(modifier = Modifier.padding(top = 18.dp)) {
            ShimmerShelfHeader(shimmerBrush = shimmerBrush)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(4) {
                    ShimmerCardSkeletonItem(isDarkMode = isDarkMode, shimmerBrush = shimmerBrush)
                }
            }
        }

        // Shimmer shelf 4 ("Albums for you" placeholder):
        // - Title & subtitle shimmer placeholder bars
        // - Horizontal row of 4 card skeleton items (160x160dp)
        Column(modifier = Modifier.padding(top = 18.dp)) {
            ShimmerShelfHeader(shimmerBrush = shimmerBrush)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(4) {
                    ShimmerCardSkeletonItem(isDarkMode = isDarkMode, shimmerBrush = shimmerBrush)
                }
            }
        }
    }
}
