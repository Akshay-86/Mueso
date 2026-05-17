package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is PlayerUiState.Empty -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No local tracks found", color = androidx.compose.ui.graphics.Color.White)
            }
        }

        is PlayerUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.message}")
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
    val currentTrackIndex by remember { 
        viewModel.getCurrentTrackIndexState() 
    }.collectAsState()

    // Initialize pager at the RESTORED track index, not 0
    val pagerState = rememberPagerState(
        initialPage = currentTrackIndex,
        pageCount = { tracks.size }
    )

    // Sync Pager with ViewModel (Auto-play next)
    LaunchedEffect(currentTrackIndex) {
        if (pagerState.currentPage != currentTrackIndex) {
            pagerState.animateScrollToPage(currentTrackIndex)
        }
    }

    // Sync ViewModel with Pager (Manual swipe)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                if (tracks.isNotEmpty()) {
                    val track = tracks[page]
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

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
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

        // Queue bottom sheet
        if (showQueueSheet) {
            QueueBottomSheet(
                tracks = tracks,
                currentTrackId = playbackState.currentTrackId,
                onTrackClick = { index ->
                    viewModel.playTrackAtIndex(index)
                    viewModel.dismissQueueSheet()
                },
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
    val albumArtUri = "content://media/external/audio/albumart/${track.albumId}"

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

            // Dynamic lyrics view
            LyricsView(
                lyrics = track.lyrics,
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

                OfflineActionsOverlay(
                    repeatMode = repeatMode,
                    isSleepTimerActive = activeSleepMode != null,
                    sleepTimerLabel = sleepTimerLabel,
                    sleepTimerStatus = sleepTimerStatus,
                    queueSize = viewModel.getTotalTracks(),
                    onSleepTimerClick = { viewModel.showSleepTimerSheet() },
                    onRepeatClick = { viewModel.cycleRepeatMode() },
                    onQueueClick = { viewModel.toggleQueueSheet() },
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
