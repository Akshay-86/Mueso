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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.state.PlayerUiState
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isOfflineRealm: Boolean? = null
) {
    val activeQueue by viewModel.activeQueue.collectAsState()
    val offlineQueue by viewModel.offlineQueue.collectAsState()
    val onlineQueue by viewModel.onlineQueue.collectAsState()
    val allLocalTracks by viewModel.allLocalTracks.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val tracks = when (isOfflineRealm) {
        true -> if (offlineQueue.isNotEmpty()) offlineQueue else allLocalTracks
        false -> onlineQueue
        null -> activeQueue
    }

    if (tracks.isNotEmpty()) {
        VerticalPagerScreen(
            tracks = tracks,
            viewModel = viewModel,
            modifier = modifier,
            isOfflineRealm = isOfflineRealm
        )
    } else {
        val emptyTitle = when (isOfflineRealm) {
            true -> "No Local Music"
            false -> "No Online Music"
            null -> "No track playing"
        }
        val emptySubtitle = when (isOfflineRealm) {
            true -> "Add MP3s or downloaded tracks to your device to play offline"
            false -> "Search or select songs from Online Playlists to start streaming"
            null -> "Search or select a song to start playing"
        }
        val emptyIcon = when (isOfflineRealm) {
            true -> Icons.Default.LibraryMusic
            false -> Icons.Default.CloudQueue
            null -> Icons.Default.MusicNote
        }

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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AccentOrange.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = emptyIcon,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = emptyTitle,
                    color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptySubtitle,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF1D1D1F).copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    modifier: Modifier = Modifier,
    isOfflineRealm: Boolean? = null
) {
    val activeRealm by viewModel.activeRealm.collectAsState()
    val isRealmActive = when (isOfflineRealm) {
        true -> activeRealm == PlayerViewModel.PlayerRealm.OFFLINE
        false -> activeRealm == PlayerViewModel.PlayerRealm.ONLINE
        null -> true
    }

    val currentTrackIndex by viewModel.currentTrackIndexState.collectAsState()

    val initialIndex = remember(tracks) {
        if (isRealmActive) {
            val requestedId = viewModel.lastRequestedTrackId
            if (requestedId != null) {
                val idx = tracks.indexOfFirst { it.id == requestedId }
                if (idx >= 0) return@remember idx
            }
            currentTrackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        } else {
            val savedId = if (isOfflineRealm == true) viewModel.savedOfflineTrackId else viewModel.savedOnlineTrackId
            if (savedId != null) {
                val idx = tracks.indexOfFirst { it.id == savedId }
                if (idx >= 0) return@remember idx
            }
            0
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { tracks.size }
    )

    // Keep latest tracks for LaunchedEffect
    val currentTracksList by rememberUpdatedState(tracks)
    var isFirstSync by remember { mutableStateOf(true) }

    // Sync Pager with ViewModel (when track changes externally in active realm)
    LaunchedEffect(currentTrackIndex, isRealmActive) {
        if (isRealmActive && currentTrackIndex in currentTracksList.indices) {
            val isCurrentOrSettled = pagerState.currentPage == currentTrackIndex || pagerState.settledPage == currentTrackIndex
            if (!isCurrentOrSettled && !pagerState.isScrollInProgress) {
                if (isFirstSync) {
                    isFirstSync = false
                    Log.d("MUESO_SYNC", "PlayerScreen UI: initial instant scrollToPage to $currentTrackIndex")
                    pagerState.scrollToPage(currentTrackIndex)
                } else {
                    Log.d("MUESO_SYNC", "PlayerScreen UI: external track change -> animating pager to $currentTrackIndex")
                    pagerState.animateScrollToPage(currentTrackIndex)
                }
            } else {
                isFirstSync = false
            }
        }
    }

    // Detect user dragging to avoid triggering realm switch on initial composition or horizontal swipes
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userInteracted by remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userInteracted = true
        }
    }

    // Sync ViewModel with Pager (only when user finishes scrolling and settles on a new page)
    LaunchedEffect(pagerState, isRealmActive) {
        snapshotFlow {
            val isScrolling = pagerState.isScrollInProgress
            val settled = pagerState.settledPage
            val current = pagerState.currentPage
            if (!isScrolling && settled == current) settled else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { settledPage ->
                if (settledPage in currentTracksList.indices) {
                    val track = currentTracksList[settledPage]
                    if (isOfflineRealm != null) {
                        if (!isRealmActive && userInteracted) {
                            val target = if (isOfflineRealm) PlayerViewModel.PlayerRealm.OFFLINE else PlayerViewModel.PlayerRealm.ONLINE
                            Log.d("MUESO_SYNC", "PlayerScreen UI: user scrolled in dormant realm -> switching to $target for '${track.title}'")
                            viewModel.switchToRealmAndPlay(target, track = track)
                        } else if (isRealmActive && settledPage != currentTrackIndex) {
                            Log.d("MUESO_SYNC", "PlayerScreen UI: user settled on page $settledPage -> playTrackIfChanged for ${track.id}")
                            viewModel.playTrackIfChanged(track)
                        }
                    } else {
                        if (settledPage != currentTrackIndex) {
                            Log.d("MUESO_SYNC", "PlayerScreen UI: user settled on page $settledPage -> playTrackIfChanged for ${track.id}")
                            viewModel.playTrackIfChanged(track)
                        }
                    }
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
    val activePlaylistInfo by viewModel.currentPlayingPlaylist.collectAsState()

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
                        modifier = Modifier.fillMaxSize(),
                        isOfflineRealm = isOfflineRealm
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
                    if (isOfflineRealm == true) {
                        viewModel.switchToRealmAndPlay(PlayerViewModel.PlayerRealm.OFFLINE, tracks.getOrNull(index), index)
                    } else if (isOfflineRealm == false) {
                        viewModel.switchToRealmAndPlay(PlayerViewModel.PlayerRealm.ONLINE, tracks.getOrNull(index), index)
                    } else {
                        viewModel.playTrackAtIndex(index)
                    }
                    viewModel.dismissQueueSheet()
                },
                onMove = { from, to -> viewModel.moveInQueue(from, to) },
                onRemove = { globalIndex, track -> viewModel.removeFromQueue(track.id, globalIndex) },
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
    modifier: Modifier = Modifier,
    isOfflineRealm: Boolean? = null
) {
    val rawPlaybackState by viewModel.playbackState.collectAsState()
    val activeRealm by viewModel.activeRealm.collectAsState()

    val isCurrentRealmActive = when (isOfflineRealm) {
        true -> activeRealm == PlayerViewModel.PlayerRealm.OFFLINE
        false -> activeRealm == PlayerViewModel.PlayerRealm.ONLINE
        null -> true
    }

    val playbackState = if (isCurrentRealmActive && rawPlaybackState.currentTrackId == track.id) {
        rawPlaybackState
    } else {
        val savedPos = when (isOfflineRealm) {
            true -> if (track.id == viewModel.savedOfflineTrackId) viewModel.savedOfflinePositionMs else 0L
            false -> if (track.id == viewModel.savedOnlineTrackId) viewModel.savedOnlinePositionMs else 0L
            null -> 0L
        }
        PlaybackState(
            isPlaying = false,
            isBuffering = false,
            currentTrackId = track.id,
            currentPositionMs = savedPos,
            durationMs = track.duration.coerceAtLeast(0L)
        )
    }
    val repeatMode by viewModel.repeatMode.collectAsState()
    val activeSleepMode by viewModel.activeSleepMode.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val sleepAfterSongId by viewModel.sleepAfterSongId.collectAsState()
    val isResolvingTrack by viewModel.isResolvingTrack.collectAsState()
    val albumArtUri = when {
        !track.artworkUrl.isNullOrBlank() -> track.artworkUrl
        track.id > 0L && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http") ->
            "content://media/external/audio/media/${track.id}"
        track.filePath.isNotBlank() && !track.filePath.startsWith("online:") -> track.filePath
        track.albumId > 0L -> "content://media/external/audio/albumart/${track.albumId}"
        else -> null
    }


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
    androidx.activity.compose.BackHandler(enabled = isLyricsExpanded) {
        isLyricsExpanded = false
    }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showTrackMenuSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    val activeAudioFormat by viewModel.activeAudioFormat.collectAsState()
    val isClarityActive by viewModel.isStudioMasterClarityEnabled.collectAsState()
    val isBitPerfectActive by viewModel.isBitPerfectEnabled.collectAsState()
    val isEqActive by viewModel.audioEffectsController.isEnabled.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleModeEnabled.collectAsState()
    val upcomingQueueSize by viewModel.upcomingTrackCountState.collectAsState()
    val isPlaylistContext by viewModel.isPlaylistContext.collectAsState()
    val activePlaylistInfo by viewModel.currentPlayingPlaylist.collectAsState()

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
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val playButtonPosition by viewModel.playButtonPosition.collectAsState()
            val lyricsFetchStatusMap by viewModel.lyricsFetchStatus.collectAsState()
            val trackLyricsStatus = lyricsFetchStatusMap[track.id] ?: com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus.IDLE
            val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()

            // Center Content: Synchronized Lyrics View OR Spacer
            if (enableLyrics) {
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

            Spacer(modifier = Modifier.weight(1f))

            // Bottom section: Song info, actions, and playback controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SongInfo(
                            track = track,
                            onArtistClick = { viewModel.openArtistByName(track.artist) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.akshay.musicplayer.ui.components.AudioQualityCapsule(
                            audioFormat = activeAudioFormat
                        )
                        IconButton(
                            onClick = { showTrackMenuSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Track options",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                val context = androidx.compose.ui.platform.LocalContext.current
                val isOnlineSong = track.filePath.startsWith("online:") || track.artworkUrl != null
                val downloadStates by viewModel.downloadStates.collectAsState()
                val trackDlState = downloadStates[track.id]


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
                    onRepeatClick = { viewModel.cycleRepeatMode(context) },
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

                if (showTrackMenuSheet) {
                    com.akshay.musicplayer.ui.components.TrackMenuBottomSheet(
                        track = track,
                        isDarkMode = isDarkMode,
                        activePlaylistInfo = activePlaylistInfo,
                        isPlaylistContext = isPlaylistContext,
                        onGoToArtist = {
                            viewModel.openArtistByName(track.artist)
                        },
                        onGoToAlbum = {
                            viewModel.openAlbumForCurrentTrack(track)
                        },
                        onShowEqualizer = { showEqualizerSheet = true },
                        onAddToPlaylist = { showAddToPlaylistSheet = true },
                        onDismiss = { showTrackMenuSheet = false }
                    )
                }

                if (showEqualizerSheet) {
                    com.akshay.musicplayer.ui.components.EqualizerBottomSheet(
                        effectsController = viewModel.audioEffectsController,
                        isDarkMode = isDarkMode,
                        onDismiss = { showEqualizerSheet = false }
                    )
                }

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
                    onPlayPauseClick = {
                        if (!isCurrentRealmActive) {
                            val targetRealm = if (isOfflineRealm == true) PlayerViewModel.PlayerRealm.OFFLINE else PlayerViewModel.PlayerRealm.ONLINE
                            viewModel.switchToRealmAndPlay(targetRealm, track = track)
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    onNextClick = {
                        if (!isCurrentRealmActive) {
                            val targetRealm = if (isOfflineRealm == true) PlayerViewModel.PlayerRealm.OFFLINE else PlayerViewModel.PlayerRealm.ONLINE
                            viewModel.switchToRealmAndPlay(targetRealm, track = null)
                        } else {
                            viewModel.playNextTrack()
                        }
                    },
                    onPreviousClick = {
                        if (!isCurrentRealmActive) {
                            val targetRealm = if (isOfflineRealm == true) PlayerViewModel.PlayerRealm.OFFLINE else PlayerViewModel.PlayerRealm.ONLINE
                            viewModel.switchToRealmAndPlay(targetRealm, track = null)
                        } else {
                            viewModel.playPreviousTrack()
                        }
                    },
                    onSeek = { seekPos ->
                        if (isCurrentRealmActive) {
                            viewModel.seekTo(seekPos)
                        }
                    },
                    isResolvingTrack = if (isCurrentRealmActive) isResolvingTrack else false,
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
