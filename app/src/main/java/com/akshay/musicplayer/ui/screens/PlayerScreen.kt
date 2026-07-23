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
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is PlayerUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF512F))
            }
        }

        is PlayerUiState.Empty -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F)),
                contentAlignment = Alignment.Center
            ) {
                Text("No local tracks found", color = Color.White.copy(alpha = 0.5f))
            }
        }

        is PlayerUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F)),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.message}", color = Color.White.copy(alpha = 0.5f))
            }
        }

        is PlayerUiState.Success -> {
            VerticalPagerScreen(
                tracks = state.tracks,
                viewModel = viewModel,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalPagerScreen(
    tracks: List<TrackEntity>,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentTrackIndex by viewModel.currentTrackIndexState.collectAsState()

    // Read the restored index SYNCHRONOUSLY so the pager never starts at 0
    val initialIndex = remember { viewModel.getRestoredTrackIndex() }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { tracks.size }
    )

    // Guard to prevent bidirectional sync feedback loop
    var isSyncingFromVM by remember { mutableStateOf(false) }

    // Keep latest tracks for LaunchedEffect
    val currentTracksList by rememberUpdatedState(tracks)

    // Sync Pager with ViewModel (when ExoPlayer changes track, e.g. auto-advance)
    LaunchedEffect(currentTrackIndex) {
        Log.d("MUESO_SYNC", "PlayerScreen UI: currentTrackIndex changed to $currentTrackIndex, pagerState is ${pagerState.currentPage}")
        if (pagerState.currentPage != currentTrackIndex) {
            isSyncingFromVM = true
            Log.d("MUESO_SYNC", "PlayerScreen UI: animating pager to $currentTrackIndex")
            pagerState.animateScrollToPage(currentTrackIndex)
            Log.d("MUESO_SYNC", "PlayerScreen UI: done animating pager to $currentTrackIndex")
            isSyncingFromVM = false
        }
    }

    // Sync ViewModel with Pager (when user manually swipes)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
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

    Box(modifier = modifier.fillMaxSize()) {
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

        // Preparing Track Stream Loading Overlay
        if (isResolvingTrack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF512F),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Preparing track...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!resolvingTrackTitle.isNullOrBlank()) {
                        Text(
                            text = resolvingTrackTitle!!,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Queue bottom sheet
        if (showQueueSheet) {
            QueueBottomSheet(
                tracks = tracks,
                currentTrackId = playbackState.currentTrackId,
                onTrackClick = { index ->
                    viewModel.playTrackAtIndex(index)
                    viewModel.dismissQueueSheet()
                },
                onMove = { from, to -> viewModel.moveInQueue(from, to) },
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

    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen immersive album art background
        AlbumArtBackground(
            albumArtUri = albumArtUri,
            modifier = Modifier.fillMaxSize()
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Space for future top header
            Spacer(modifier = Modifier.height(100.dp))

            val lyricsFetchStatusMap by viewModel.lyricsFetchStatus.collectAsState()
            val trackLyricsStatus = lyricsFetchStatusMap[track.id] ?: com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus.IDLE
            val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()

            // Dynamic lyrics view
            LyricsView(
                lyrics = track.lyrics,
                currentPositionMs = playbackState.currentPositionMs,
                lyricsFetchStatus = trackLyricsStatus,
                lyricsOffsetMs = lyricsOffsetMs,
                onAdjustOffset = { viewModel.adjustLyricsOffset(it) },
                onResetOffset = { viewModel.resetLyricsOffset() },
                modifier = Modifier.fillMaxWidth()
            )

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

                OfflineActionsOverlay(
                    repeatMode = repeatMode,
                    isSleepTimerActive = activeSleepMode != null,
                    sleepTimerLabel = sleepTimerLabel,
                    sleepTimerStatus = sleepTimerStatus,
                    queueSize = viewModel.getUpcomingTrackCount(),
                    isOnlineSong = isOnlineSong,
                    isDownloading = trackDlState?.isDownloading == true,
                    downloadProgress = trackDlState?.progress ?: 0f,
                    isDownloaded = trackDlState?.isDownloaded == true,
                    onSleepTimerClick = { viewModel.showSleepTimerSheet() },
                    onRepeatClick = { viewModel.cycleRepeatMode() },
                    onQueueClick = { viewModel.toggleQueueSheet() },
                    onDownloadClick = { viewModel.downloadOnlineTrack(context, track) },
                    modifier = Modifier.fillMaxWidth()
                )

                PlayerControls(
                    playbackState = playbackState,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNextTrack() },
                    onPreviousClick = { viewModel.playPreviousTrack() },
                    onSeek = { viewModel.seekTo(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
