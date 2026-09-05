package com.akshay.musicplayer.ui.screens

import android.util.Log

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size

import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.AlbumArtBackground
import com.akshay.musicplayer.ui.components.LyricsView
import com.akshay.musicplayer.ui.components.PlayerControls
import com.akshay.musicplayer.ui.components.OfflineActionsOverlay
import com.akshay.musicplayer.ui.components.QueueBottomSheet
import com.akshay.musicplayer.ui.components.SleepTimerBottomSheet
import com.akshay.musicplayer.ui.components.SleepTimerMode

import com.akshay.musicplayer.ui.components.SongInfo
import com.akshay.musicplayer.ui.state.PlayerUiState
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val activeQueue by viewModel.activeQueue.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()

    if (activeQueue.isNotEmpty()) {
        VerticalPagerScreen(
            tracks = activeQueue,
            viewModel = viewModel,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "No track playing",
                    color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Search or select a song to start playing",
                    color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF1D1D1F).copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun VerticalPagerScreen(
    tracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentTrackIndex by viewModel.currentTrackIndexState.collectAsState()

    val initialIndex = remember(tracks) {
        val requestedId = viewModel.lastRequestedTrackId
        if (requestedId != null) {
            val idx = tracks.indexOfFirst { it.id == requestedId }
            if (idx >= 0) return@remember idx
        }
        currentTrackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { tracks.size }
    )

    // Guard to prevent bidirectional sync feedback loop
    var isSyncingFromVM by remember { mutableStateOf(false) }

    // Keep latest tracks for LaunchedEffect
    val currentTracksList by rememberUpdatedState(tracks)

    var isFirstSync by remember { mutableStateOf(true) }

    // Sync Pager with ViewModel (when ExoPlayer changes track, e.g. auto-advance)
    LaunchedEffect(currentTrackIndex) {
        Log.d("MUESO_SYNC", "PlayerScreen UI: currentTrackIndex changed to $currentTrackIndex, pagerState is ${pagerState.currentPage}")
        if (pagerState.currentPage != currentTrackIndex) {
            isSyncingFromVM = true
            if (isFirstSync) {
                isFirstSync = false
                Log.d("MUESO_SYNC", "PlayerScreen UI: initial instant scrollToPage to $currentTrackIndex")
                pagerState.scrollToPage(currentTrackIndex)
            } else {
                Log.d("MUESO_SYNC", "PlayerScreen UI: animating pager to $currentTrackIndex")
                pagerState.animateScrollToPage(currentTrackIndex)
                Log.d("MUESO_SYNC", "PlayerScreen UI: done animating pager to $currentTrackIndex")
            }
            isSyncingFromVM = false
        } else {
            isFirstSync = false
        }
    }

    // Sync ViewModel with Pager (when user manually swipes)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .debounce(300) // Wait for user to truly settle during rapid swiping
            .collect { page ->
                Log.d("MUESO_SYNC", "PlayerScreen UI: pager settled on page $page. isSyncingFromVM=$isSyncingFromVM")
                if (!isSyncingFromVM && currentTracksList.isNotEmpty() && page < currentTracksList.size) {
                    val track = currentTracksList[page]
                    Log.d("MUESO_SYNC", "PlayerScreen UI: Calling playTrackIfChanged for ${track.id}")
                    viewModel.playTrackIfChanged(track)
                }
            }
    }

    // Sheet states
    val showQueueSheet by viewModel.showQueueSheet.collectAsState()
    val showSleepTimerSheet by viewModel.showSleepTimerSheet.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val activeSleepMode by viewModel.activeSleepMode.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val sleepAfterSongId by viewModel.sleepAfterSongId.collectAsState()
    val isResolvingTrack by viewModel.isResolvingTrack.collectAsState()
    val resolvingTrackTitle by viewModel.resolvingTrackTitle.collectAsState()

    val isPlaylistContext by viewModel.isPlaylistContext.collectAsState()
    val playlistTrackCount by viewModel.playlistTrackCount.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val playButtonPosition by viewModel.playButtonPosition.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (tracks.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> if (page in tracks.indices) "${tracks[page].id}_$page" else page }
            ) { page ->
                if (page < tracks.size) {
                    val track = tracks[page]
                    PlayerPageContent(
                        track = track,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Modern Empty State when nothing is playing
            PlayerEmptyStateView(isDarkMode = isDarkMode)
        }

        // Queue bottom sheet
        if (showQueueSheet) {
            QueueBottomSheet(
                tracks = tracks,
                currentTrackId = playbackState.currentTrackId,
                isPlaylistContext = isPlaylistContext,
                playlistTrackCount = playlistTrackCount,
                isDarkMode = isDarkMode,
                onTrackClick = { index ->
                    viewModel.playTrackAtIndex(index)
                    viewModel.dismissQueueSheet()
                },
                onMove = { from, to -> viewModel.moveInQueue(from, to) },
                onClearQueue = { viewModel.clearUpcomingQueue() },
                onDismiss = { viewModel.dismissQueueSheet() }
            )
        }

        // Sleep timer bottom sheet
        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                tracks = tracks,
                currentTrackId = playbackState.currentTrackId,
                activeSleepMode = activeSleepMode,
                activeTimerMinutes = sleepTimerMinutesLeft,
                activeSleepSongId = sleepAfterSongId,
                isPlaylistContext = isPlaylistContext,
                isDarkMode = isDarkMode,
                onSetTimer = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    viewModel.dismissSleepTimerSheet()
                },
                onSetAfterSong = { trackId ->
                    viewModel.setSleepAfterSong(trackId)
                    viewModel.dismissSleepTimerSheet()
                },
                onSetEndOfPlaylist = {
                    viewModel.setSleepEndOfPlaylist()
                    viewModel.dismissSleepTimerSheet()
                },
                onCancelTimer = {
                    viewModel.clearSleepTimer()
                    viewModel.dismissSleepTimerSheet()
                },
                onDismiss = { viewModel.dismissSleepTimerSheet() }
            )
        }
    }
}

@Composable
fun PlayerPageContent(
    track: TrackEntity,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val activeSleepMode by viewModel.activeSleepMode.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val sleepAfterSongId by viewModel.sleepAfterSongId.collectAsState()
    val isResolvingTrack by viewModel.isResolvingTrack.collectAsState()
    val albumArtUri = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}"


    // Short label above icon
    val sleepTimerLabel = when (activeSleepMode) {
        SleepTimerMode.TIMER -> "${sleepTimerMinutesLeft}m"
        SleepTimerMode.AFTER_SONG -> "Song"
        SleepTimerMode.END_OF_PLAYLIST -> "End"
        null -> null
    }

    // Full scrolling status text
    val sleepTimerStatus = when (activeSleepMode) {
        SleepTimerMode.TIMER -> {
            val mins = sleepTimerMinutesLeft ?: 0
            val h = mins / 60
            val m = mins % 60
            when {
                h > 0 && m > 0 -> "Stops in ${h}h ${m}m"
                h > 0 -> "Stops in ${h}h"
                else -> "Stops in ${m} min"
            }
        }
        SleepTimerMode.AFTER_SONG -> {
            val songName = viewModel.getQueueTracks()
                .find { it.id == sleepAfterSongId }?.let { "${it.title} — ${it.artist}" }
                ?: "selected song"
            "Stops after \"$songName\""
        }
        SleepTimerMode.END_OF_PLAYLIST -> "Stops at end of playlist"
        null -> null
    }

    var isLyricsExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen immersive album art background
        AlbumArtBackground(
            albumArtUri = albumArtUri,
            initialBias = viewModel.getTrackBackgroundBias(track),
            onBiasChanged = { newBias ->
                viewModel.saveTrackBackgroundBias(track, newBias)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Full-screen scrim: tapping anywhere outside the lyrics card closes it!
        if (isLyricsExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isLyricsExpanded = false
                    }
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Space for top navigation bar
            Spacer(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(60.dp)
            )

            val enableLyrics by viewModel.enableLyrics.collectAsState()
            val enableVideoMode by viewModel.enableVideoMode.collectAsState()
            val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val playButtonPosition by viewModel.playButtonPosition.collectAsState()
            val lyricsFetchStatusMap by viewModel.lyricsFetchStatus.collectAsState()
            val trackLyricsStatus = lyricsFetchStatusMap[track.id] ?: com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus.IDLE
            val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()
            val hasVideoAvailable = track.filePath.startsWith("online:")
            var showFullScreenVideo by remember { mutableStateOf(false) }

            // 1. Center Content: Video surface OR Lyrics View OR Spacers
            if (enableVideoMode && isVideoModeActive && hasVideoAvailable) {
                val ytView = viewModel.getOnlinePlayerView()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (ytView != null) {
                        AndroidView(
                            factory = {
                                (ytView.parent as? android.view.ViewGroup)?.removeView(ytView)
                                ytView.apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    alpha = 1f
                                    visibility = android.view.View.VISIBLE
                                }
                                ytView
                            },
                            update = { view ->
                                view.alpha = 1f
                                view.visibility = android.view.View.VISIBLE
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFFFF512F), strokeWidth = 2.dp)
                    }

                    // Top-Right Fullscreen Expand Button (Matching YT Music web)
                    IconButton(
                        onClick = { showFullScreenVideo = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else if (enableLyrics) {
                LyricsView(
                    lyrics = track.lyrics,
                    currentPositionMs = playbackState.currentPositionMs,
                    lyricsFetchStatus = trackLyricsStatus,
                    lyricsOffsetMs = lyricsOffsetMs,
                    trackTitle = track.title,
                    trackArtist = track.artist,
                    isSavedInUserPlaylist = viewModel.isTrackInUserPlaylists(track.id),
                    isExpanded = isLyricsExpanded,
                    onExpandedChange = { isLyricsExpanded = it },
                    onAdjustOffset = { viewModel.adjustLyricsOffset(it) },
                    onResetOffset = { viewModel.resetLyricsOffset() },
                    onSearchCandidates = { query -> viewModel.searchLrclibCandidates(query) },
                    onApplyCandidate = { candidate -> viewModel.applyLrclibCandidate(track.id, candidate) },
                    onSeekTo = { timestampMs -> viewModel.seekTo(timestampMs) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 2. Song | Video Switcher Pill (Placed directly below lyrics/video card)
            if (enableVideoMode && hasVideoAvailable) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Song Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!isVideoModeActive) (if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.White) else Color.Transparent)
                            .clickable { viewModel.setVideoModeActive(false) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (!isVideoModeActive) (if (isDarkMode) Color.White else Color(0xFF1D1D1F)) else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Song",
                                color = if (!isVideoModeActive) (if (isDarkMode) Color.White else Color(0xFF1D1D1F)) else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                fontSize = 12.sp,
                                fontWeight = if (!isVideoModeActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // Video Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isVideoModeActive) (if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.White) else Color.Transparent)
                            .clickable { viewModel.setVideoModeActive(true) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (isVideoModeActive) (if (isDarkMode) Color.White else Color(0xFF1D1D1F)) else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Video",
                                color = if (isVideoModeActive) (if (isDarkMode) Color.White else Color(0xFF1D1D1F)) else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                fontSize = 12.sp,
                                fontWeight = if (isVideoModeActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Fullscreen Immersive Video Dialog
            if (showFullScreenVideo && enableVideoMode && isVideoModeActive && hasVideoAvailable) {
                val ytView = viewModel.getOnlinePlayerView()
                Dialog(
                    onDismissRequest = { showFullScreenVideo = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = true
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (ytView != null) {
                            AndroidView(
                                factory = {
                                    (ytView.parent as? android.view.ViewGroup)?.removeView(ytView)
                                    ytView.apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        alpha = 1f
                                        visibility = android.view.View.VISIBLE
                                    }
                                    ytView
                                },
                                update = { view ->
                                    view.alpha = 1f
                                    view.visibility = android.view.View.VISIBLE
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }

                        // Top-right exit fullscreen button
                        IconButton(
                            onClick = { showFullScreenVideo = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom section: Song info, actions, and playback controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SongInfo(
                    track = track,
                    modifier = Modifier.fillMaxWidth()
                )

                val context = androidx.compose.ui.platform.LocalContext.current
                val isOnlineSong = track.filePath.startsWith("online:") || track.artworkUrl != null
                val downloadStates by viewModel.downloadStates.collectAsState()
                val trackDlState = downloadStates[track.id]

                var showAddToPlaylistSheet by remember { mutableStateOf(false) }

                val isShuffleEnabled by viewModel.isShuffleModeEnabled.collectAsState()
                val upcomingQueueSize by viewModel.upcomingTrackCountState.collectAsState()

                OfflineActionsOverlay(
                    repeatMode = repeatMode,
                    isShuffleEnabled = isShuffleEnabled,
                    isSleepTimerActive = activeSleepMode != null,
                    sleepTimerLabel = sleepTimerLabel,
                    sleepTimerStatus = sleepTimerStatus,
                    queueSize = upcomingQueueSize,
                    isOnlineSong = isOnlineSong,
                    isDownloading = trackDlState?.isDownloading == true,
                    downloadProgress = trackDlState?.progress ?: 0f,
                    isDownloaded = trackDlState?.isDownloaded == true,
                    onSleepTimerClick = { viewModel.showSleepTimerSheet() },
                    onShuffleClick = { viewModel.toggleShuffleMode() },
                    onRepeatClick = { viewModel.cycleRepeatMode() },
                    onQueueClick = { viewModel.toggleQueueSheet() },
                    onDownloadClick = {
                        viewModel.downloadOnlineTrack(context, track)
                    },
                    onDownloadLongClick = {
                        viewModel.downloadOnlineTrack(context, track)
                    },
                    onCancelDownloadClick = { viewModel.cancelDownload(track.id) },
                    onAddToPlaylistClick = {
                        showAddToPlaylistSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showAddToPlaylistSheet) {
                    com.akshay.musicplayer.ui.components.AddToPlaylistBottomSheet(
                        track = track,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        onDismiss = { showAddToPlaylistSheet = false }
                    )
                }

                PlayerControls(
                    playbackState = playbackState,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNextTrack() },
                    onPreviousClick = { viewModel.playPreviousTrack() },
                    onSeek = { viewModel.seekTo(it) },
                    isResolvingTrack = isResolvingTrack,
                    playButtonPosition = playButtonPosition,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─── Modern Empty Player View ───

@Composable
private fun PlayerEmptyStateView(isDarkMode: Boolean) {
    val bgColor = if (isDarkMode) Color(0xFF0F0F14) else Color(0xFFF2F2F7)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "No Song Playing",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pick a song from your Offline Library or Online Charts to start playing",
                color = textSub,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}
