@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.akshay.musicplayer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.AddToPlaylistBottomSheet
import com.akshay.musicplayer.ui.components.AudioQualityCapsule
import com.akshay.musicplayer.ui.components.ClassicFullScreenLyricsView
import com.akshay.musicplayer.ui.components.EqualizerBottomSheet
import com.akshay.musicplayer.ui.components.LyricsView
import com.akshay.musicplayer.ui.components.QueueBottomSheet
import com.akshay.musicplayer.ui.components.SignalPathDialog
import com.akshay.musicplayer.ui.components.SleepTimerBottomSheet
import com.akshay.musicplayer.ui.components.TrackMenuBottomSheet
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalAccentGradient
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicPlayerScreen(
    track: TrackEntity,
    viewModel: PlayerViewModel,
    onCollapseClick: () -> Unit
) {
    var isLyricsVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = isLyricsVisible) {
        isLyricsVisible = false
    }
    BackHandler(enabled = !isLyricsVisible, onBack = onCollapseClick)

    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val accentGradient = LocalAccentGradient.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isPureBlack = LocalIsPureBlack.current
    val bgColor = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF101018) else Color(0xFFF7F7FA)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)

    val playbackState by viewModel.playbackState.collectAsState()
    val activeAudioFormat by viewModel.activeAudioFormat.collectAsState()
    val isClarityActive by viewModel.isStudioMasterClarityEnabled.collectAsState()
    val isBitPerfectActive by viewModel.isBitPerfectEnabled.collectAsState()
    val isEqActive by viewModel.audioEffectsController.isEnabled.collectAsState()

    val isShuffleEnabled by viewModel.isShuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val enableLyrics by viewModel.enableLyrics.collectAsState()
    val lyricsFetchStatusMap by viewModel.lyricsFetchStatus.collectAsState()
    val trackLyricsStatus = lyricsFetchStatusMap[track.id] ?: com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus.IDLE
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()

    val downloadStates by viewModel.downloadStates.collectAsState()
    val trackDlState = downloadStates[track.id]
    val isDownloading = trackDlState?.isDownloading == true
    val isDownloaded = trackDlState?.isDownloaded == true
    val downloadProgress = trackDlState?.progress ?: 0f

    val activeQueue by viewModel.activeQueue.collectAsState()
    val isPlaylistContext by viewModel.isPlaylistContext.collectAsState()
    val playlistTrackCount by viewModel.playlistTrackCount.collectAsState()
    val activePlaylistInfo by viewModel.currentPlayingPlaylist.collectAsState()

    var showTrackMenuSheet by remember { mutableStateOf(false) }
    var showSignalPathDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }

    // Seekbar state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val currentPosMs = if (isDraggingSlider) dragSliderValue.toLong() else playbackState.currentPositionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // ─── 1. Top Navigation Bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapseClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = track.album.ifBlank { "Mueso Master" },
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showTrackMenuSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Menu",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ─── 2. Center: Square Album Artwork ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val artCorner = 20.dp
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl ?: android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), track.albumId))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .shadow(20.dp, RoundedCornerShape(artCorner), ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(artCorner))
                        .background(if (isDarkMode) Color(0xFF1E1E28) else Color.LightGray)
                )
            }

            // ─── 3. Bottom Controls Container ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Song Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = track.artist,
                            color = textSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.openArtistByName(track.artist)
                                    onCollapseClick()
                                }
                                .padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AudioQualityCapsule(
                        audioFormat = activeAudioFormat,
                        onClick = { showSignalPathDialog = true }
                    )
                }

                // Seekbar & Timestamps
                Column {
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                    )
                    Slider(
                        value = currentPosMs.toFloat(),
                        onValueChange = {
                            isDraggingSlider = true
                            dragSliderValue = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            viewModel.seekTo(dragSliderValue.toLong())
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        colors = sliderColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosMs),
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Main Playback Controls: Shuffle, Prev, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.toggleShuffleMode() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) accent else textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playPreviousTrack() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = textPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Large Hero Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(8.dp, CircleShape, spotColor = accent.copy(alpha = 0.55f))
                            .clip(CircleShape)
                            .background(accentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (playbackState.isBuffering && playbackState.isPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.playNextTrack() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = textPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    val isRepeatOne = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
                    val isRepeatAll = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
                    IconButton(
                        onClick = { viewModel.cycleRepeatMode(context) }
                    ) {
                        Icon(
                            imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeatOne || isRepeatAll) accent else textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Quick Action Utilities: Lyrics, Sleep, Queue, EQ, Add to Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isLyricsVisible = !isLyricsVisible }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (isLyricsVisible) accent else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.AvTimer,
                            contentDescription = "Sleep Timer",
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isDownloading) {
                                viewModel.cancelDownload(track.id)
                            } else if (!isDownloaded) {
                                viewModel.downloadOnlineTrack(context, track)
                            }
                        }
                    ) {
                        when {
                            isDownloading -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { if (downloadProgress > 0f) downloadProgress else 0.5f },
                                        modifier = Modifier.size(20.dp),
                                        color = accent,
                                        strokeWidth = 2.dp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Download",
                                        tint = accent,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            isDownloaded -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Song",
                                    tint = textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showAddToPlaylistSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ─── Full-Screen Synced Lyrics Overlay ───
        androidx.compose.animation.AnimatedVisibility(
            visible = isLyricsVisible,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(300)
            ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(250)
            ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            ClassicFullScreenLyricsView(
                track = track,
                playbackState = playbackState,
                viewModel = viewModel,
                onClose = { isLyricsVisible = false }
            )
        }
    }

    // Sheets & Dialogs
    if (showTrackMenuSheet) {
        TrackMenuBottomSheet(
            track = track,
            isDarkMode = isDarkMode,
            activePlaylistInfo = activePlaylistInfo,
            isPlaylistContext = isPlaylistContext,
            onGoToArtist = {
                viewModel.openArtistByName(track.artist)
                onCollapseClick()
            },
            onGoToAlbum = {
                viewModel.openAlbumForCurrentTrack(track) {
                    onCollapseClick()
                }
            },
            onShowSignalPath = { showSignalPathDialog = true },
            onShowEqualizer = { showEqualizerSheet = true },
            onAddToPlaylist = { showAddToPlaylistSheet = true },
            onDismiss = { showTrackMenuSheet = false }
        )
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            effectsController = viewModel.audioEffectsController,
            isDarkMode = isDarkMode,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showSignalPathDialog) {
        SignalPathDialog(
            audioFormat = activeAudioFormat,
            track = track,
            playbackState = playbackState,
            isEqualizerActive = isEqActive && !isBitPerfectActive,
            isClarityActive = isClarityActive,
            isBitPerfectActive = isBitPerfectActive,
            onOpenEqualizer = { showEqualizerSheet = true },
            onDismiss = { showSignalPathDialog = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            tracks = activeQueue,
            currentTrackId = playbackState.currentTrackId,
            isPlaylistContext = isPlaylistContext,
            playlistTrackCount = playlistTrackCount,
            isDarkMode = isDarkMode,
            onTrackClick = { index ->
                viewModel.playTrackAtIndex(index)
                showQueueSheet = false
            },
            onMove = { from, to -> viewModel.moveInQueue(from, to) },
            onClearQueue = { viewModel.clearUpcomingQueue() },
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        val activeSleepMode by viewModel.activeSleepMode.collectAsState()
        val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
        val sleepAfterSongId by viewModel.sleepAfterSongId.collectAsState()
        SleepTimerBottomSheet(
            tracks = activeQueue,
            currentTrackId = playbackState.currentTrackId,
            isPlaylistContext = isPlaylistContext,
            activeSleepMode = activeSleepMode,
            activeTimerMinutes = sleepTimerMinutesLeft,
            activeSleepSongId = sleepAfterSongId,
            isDarkMode = isDarkMode,
            onSetTimer = {
                viewModel.setSleepTimer(it)
                showSleepTimerSheet = false
            },
            onSetAfterSong = {
                viewModel.setSleepAfterSong(it)
                showSleepTimerSheet = false
            },
            onSetEndOfPlaylist = {
                viewModel.setSleepEndOfPlaylist()
                showSleepTimerSheet = false
            },
            onCancelTimer = {
                viewModel.clearSleepTimer()
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistBottomSheet(
            track = track,
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
