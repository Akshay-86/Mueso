package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedTrackForPlaylist by remember { mutableStateOf<com.akshay.musicplayer.domain.models.TrackEntity?>(null) }
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isOnlineActive = pagerState.currentPage == 2

    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    androidx.activity.compose.BackHandler(enabled = pagerState.currentPage != 1 && selectedPlaylist == null && !isSearchActive) {
        coroutineScope.launch { pagerState.animateScrollToPage(1) }
    }

    var isOnlineDetailActive by remember { mutableStateOf(false) }

    val showSettingsSheet by viewModel.showSettingsSheet.collectAsState()
    val skipSponsor by viewModel.skipSponsor.collectAsState()
    val skipSelfPromo by viewModel.skipSelfPromo.collectAsState()
    val skipInteraction by viewModel.skipInteraction.collectAsState()
    val skipIntroOutro by viewModel.skipIntroOutro.collectAsState()
    val skipNonMusicOffTopic by viewModel.skipNonMusicOffTopic.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val thumbnailQuality by viewModel.thumbnailQuality.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val downloadFolder by viewModel.downloadFolder.collectAsState()
    val enableLyrics by viewModel.enableLyrics.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Pager always alive
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isSearchActive
        ) { page ->
            when (page) {
                0 -> OfflineLibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onPlaylistClick = { selectedPlaylist = it }
                )
                1 -> PlayerScreen(viewModel = viewModel)
                2 -> OnlinePlaylistsScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onDetailVisibilityChanged = { isOnlineDetailActive = it }
                )
            }
        }

        // Top Navigation Bar with integrated search (hidden when viewing a playlist detail)
        if (selectedPlaylist == null && !isOnlineDetailActive) {
            TopNavigationBarWithSearch(
                isOnlineActive = isOnlineActive,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                isDarkMode = isDarkMode,
                onSearchClick = { isSearchActive = true },
                onSearchClose = {
                    isSearchActive = false
                    viewModel.setSearchQuery("")
                },
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSettingsClick = { viewModel.toggleSettingsSheet() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Floating search results dropdown
        AnimatedVisibility(
            visible = isSearchActive && searchQuery.isNotEmpty(),
            enter = expandVertically(
                animationSpec = tween(250),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            val results by viewModel.searchResults.collectAsState()
            val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1E2E).copy(alpha = 0.95f))
                    .padding(12.dp)
            ) {
                if (isSearchingOnline && results.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AccentOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Searching online...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching songs found", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                } else {
                    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results.size) { index ->
                            val track = results[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.playTrack(track)
                                        isSearchActive = false
                                        viewModel.setSearchQuery("")
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val thumbModel = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}"
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    coil.compose.AsyncImage(
                                        model = thumbModel,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.MusicNote)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                var showSearchMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showSearchMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3C),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSearchMenu,
                                        onDismissRequest = { showSearchMenu = false },
                                        modifier = Modifier.background(if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Play Next", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                            onClick = {
                                                showSearchMenu = false
                                                viewModel.playNext(track)
                                                android.widget.Toast.makeText(context, "Playing next: \"${track.title}\"", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Queue", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                            onClick = {
                                                showSearchMenu = false
                                                viewModel.addToQueue(track)
                                                android.widget.Toast.makeText(context, "Added to queue: \"${track.title}\"", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Playlist", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                            onClick = {
                                                showSearchMenu = false
                                                selectedTrackForPlaylist = track
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scrim when search is active
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (searchQuery.isNotEmpty()) 500.dp else 100.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    }
            )
        }

        // Overlay Playlist Detail
        if (selectedPlaylist != null) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist!!,
                viewModel = viewModel,
                onBack = { selectedPlaylist = null },
                onNavigateToPlayer = {
                    selectedPlaylist = null
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        // Add to Online Playlist Bottom Sheet from Search Results
        if (selectedTrackForPlaylist != null) {
            com.akshay.musicplayer.ui.components.AddToOnlinePlaylistBottomSheet(
                track = selectedTrackForPlaylist!!,
                viewModel = viewModel,
                onlinePlaylists = onlinePlaylists,
                isDarkMode = isDarkMode,
                onSelectPlaylist = { playlist ->
                    viewModel.addTrackToOnlinePlaylist(playlist.id, selectedTrackForPlaylist!!)
                },
                onDismiss = { selectedTrackForPlaylist = null }
            )
        }

        // Settings Bottom Sheet
        if (showSettingsSheet) {
            val showOnLockscreen by viewModel.showOnLockscreen.collectAsState()
            val highRefreshRate by viewModel.highRefreshRate.collectAsState()

            com.akshay.musicplayer.ui.components.SettingsBottomSheet(
                skipSponsor = skipSponsor,
                skipSelfPromo = skipSelfPromo,
                skipInteraction = skipInteraction,
                skipIntroOutro = skipIntroOutro,
                skipNonMusicOffTopic = skipNonMusicOffTopic,
                audioQuality = audioQuality,
                thumbnailQuality = thumbnailQuality,
                downloadQuality = downloadQuality,
                downloadFolder = downloadFolder,
                enableLyrics = enableLyrics,
                isDarkMode = isDarkMode,
                showOnLockscreen = showOnLockscreen,
                highRefreshRate = highRefreshRate,
                onToggleSponsor = { viewModel.setSkipSponsor(it) },
                onToggleSelfPromo = { viewModel.setSkipSelfPromo(it) },
                onToggleInteraction = { viewModel.setSkipInteraction(it) },
                onToggleIntroOutro = { viewModel.setSkipIntroOutro(it) },
                onToggleNonMusicOffTopic = { viewModel.setSkipNonMusicOffTopic(it) },
                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                onThumbnailQualityChange = { viewModel.setThumbnailQuality(it) },
                onDownloadQualityChange = { viewModel.setDownloadQuality(it) },
                onDownloadFolderChange = { viewModel.setDownloadFolder(it) },
                onEnableLyricsToggle = { viewModel.setEnableLyrics(it) },
                onDarkModeToggle = { viewModel.setDarkMode(it) },
                onShowOnLockscreenToggle = { viewModel.setShowOnLockscreen(it) },
                onHighRefreshRateToggle = { viewModel.setHighRefreshRate(it) },
                onForceRefresh = { ctx -> viewModel.forceRefreshAll(ctx) },
                onDismiss = { viewModel.dismissSettingsSheet() }
            )
        }
    }
}

// ─── Top Navigation Bar ──────────────────────────────────────

@Composable
fun TopNavigationBarWithSearch(
    isOnlineActive: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    isDarkMode: Boolean = true,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
    val searchBg = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Use a Box with weight(1f) so the layout size stays fixed during animation
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Left: App Symbol & Mueso Name (hide when search is active)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Mueso App Icon",
                        tint = AccentOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Mueso",
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // Right: Search bar
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = expandHorizontally(
                    animationSpec = tween(300),
                    expandFrom = Alignment.End
                ) + fadeIn(tween(200)),
                exit = shrinkHorizontally(
                    animationSpec = tween(250),
                    shrinkTowards = Alignment.End
                ) + fadeOut(tween(150)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(searchBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = textSub,
                    modifier = Modifier.size(20.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentOrange),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search songs...",
                                    color = textSub,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = textSub,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        } // End AnimatedVisibility
        } // End Box

        // Action buttons: Settings & Search/Close toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isSearchActive) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = textColor
                    )
                }
            }
            IconButton(
                onClick = {
                    if (isSearchActive) onSearchClose() else onSearchClick()
                }
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = textColor
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(title, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineMedium)
    }
}
