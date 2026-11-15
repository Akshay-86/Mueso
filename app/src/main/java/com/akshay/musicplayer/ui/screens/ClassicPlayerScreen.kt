@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.akshay.musicplayer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.akshay.musicplayer.ui.components.SleepTimerBottomSheet
import com.akshay.musicplayer.ui.components.SmartArtworkImage
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
    onCollapseClick: () -> Unit,
    modifier: Modifier = Modifier
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
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }

    // Seekbar state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }
    var pendingSeekPosition by remember { mutableStateOf<Long?>(null) }
    var showRemainingTime by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(playbackState.currentPositionMs) {
        if (pendingSeekPosition != null && kotlin.math.abs(playbackState.currentPositionMs - pendingSeekPosition!!) <= 1500L) {
            pendingSeekPosition = null
        }
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentTrackId) {
        pendingSeekPosition = null
    }

    val currentPosMs = if (isDraggingSlider) dragSliderValue.toLong() else pendingSeekPosition ?: playbackState.currentPositionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(isLyricsVisible) {
                if (!isLyricsVisible) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDragEnd = { totalDragY = 0f },
                        onDragCancel = { totalDragY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            totalDragY += dragAmount
                            if (totalDragY > 120f && dragAmount > 0f) {
                                change.consume()
                                onCollapseClick()
                            }
                        }
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Intercept and consume unhandled taps so they never leak through */ }
            )
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

            // ─── 2. Center: Living Breathing Album Artwork Deck ───
            val artScale by animateFloatAsState(
                targetValue = if (playbackState.isPlaying) 1.0f else 0.91f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
                label = "ArtworkScale"
            )
            val artShadowElevation by animateDpAsState(
                targetValue = if (playbackState.isPlaying) 24.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
                label = "ArtworkShadow"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val artCorner = 24.dp
                val albumArtUri = remember(track.id, track.artworkUrl, track.albumId, track.filePath) {
                    when {
                        !track.artworkUrl.isNullOrBlank() -> track.artworkUrl
                        track.id > 0L && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http") ->
                            "content://media/external/audio/media/${track.id}"
                        track.filePath.isNotBlank() && !track.filePath.startsWith("online:") -> track.filePath
                        track.albumId > 0L -> "content://media/external/audio/albumart/${track.albumId}"
                        else -> null
                    }
                }

                // Ambient Radial Glow Bloom behind artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = artScale * 1.08f
                            scaleY = artScale * 1.08f
                            alpha = if (playbackState.isPlaying) 0.40f else 0.15f
                        }
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )

                // Main Album Cover
                SmartArtworkImage(
                    artworkUrl = albumArtUri,
                    contentDescription = "Album Artwork",
                    contentScale = ContentScale.Crop,
                    thumbnailQuality = "Highest (1080p Maxres)",
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                        }
                        .shadow(artShadowElevation, RoundedCornerShape(artCorner), ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.45f))
                        .clip(RoundedCornerShape(artCorner))
                        .background(if (isDarkMode) Color(0xFF1E1E28) else Color.LightGray)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isLyricsVisible = !isLyricsVisible }
                        )
                )
            }

            // ─── 3. Bottom Controls Container ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        audioFormat = activeAudioFormat
                    )
                }

                // Seekbar & Timestamps with Tooltip & Remaining Toggle
                Column {
                    // Floating Scrub Delta Tooltip when dragging
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDraggingSlider) {
                            val scrubPos = dragSliderValue.toLong()
                            val deltaMs = scrubPos - playbackState.currentPositionMs
                            val deltaPrefix = if (deltaMs >= 0) "+" else "-"
                            val deltaFormatted = "$deltaPrefix${formatTime(kotlin.math.abs(deltaMs))}"

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accent,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = "${formatTime(scrubPos)} ($deltaFormatted)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

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
                            val target = dragSliderValue.toLong()
                            pendingSeekPosition = target
                            isDraggingSlider = false
                            viewModel.seekTo(target)
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        colors = sliderColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosMs),
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val remainingMs = (durationMs - currentPosMs).coerceAtLeast(0L)
                        Text(
                            text = if (showRemainingTime) "-${formatTime(remainingMs)}" else formatTime(durationMs),
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showRemainingTime = !showRemainingTime }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Main Playback Controls: Shuffle, Prev, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shuffleTint by animateColorAsState(if (isShuffleEnabled) accent else textSecondary, tween(200), label = "ShuffleColor")
                    val shuffleScale by animateFloatAsState(if (isShuffleEnabled) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "ShuffleScale")

                    IconButton(
                        onClick = { viewModel.toggleShuffleMode() },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = shuffleScale
                                scaleY = shuffleScale
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (isShuffleEnabled) "Shuffle On" else "Shuffle Off",
                            tint = shuffleTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playPreviousTrack() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Large Hero Play/Pause Button (72dp with radial spring feedback)
                    val heroPlayInteraction = remember { MutableInteractionSource() }
                    val isHeroPressed by heroPlayInteraction.collectIsPressedAsState()
                    val heroScale by animateFloatAsState(
                        targetValue = if (isHeroPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
                        label = "HeroPlayScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                scaleX = heroScale
                                scaleY = heroScale
                            }
                            .shadow(12.dp, CircleShape, spotColor = accent.copy(alpha = 0.55f))
                            .clip(CircleShape)
                            .background(accentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            interactionSource = heroPlayInteraction,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (playbackState.isBuffering && playbackState.isPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                AnimatedContent(
                                    targetState = playbackState.isPlaying,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.65f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(140)))
                                            .togetherWith(scaleOut(targetScale = 0.65f, animationSpec = tween(140, easing = FastOutSlowInEasing)) + fadeOut(tween(100)))
                                    },
                                    label = "HeroPlayPauseAnim"
                                ) { isPlaying ->
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.playNextTrack() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    val isRepeatOne = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
                    val isRepeatAll = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
                    val isRepeatActive = isRepeatOne || isRepeatAll
                    val repeatTint by animateColorAsState(if (isRepeatActive) accent else textSecondary, tween(200), label = "RepeatColor")
                    val repeatScale by animateFloatAsState(if (isRepeatActive) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "RepeatScale")

                    IconButton(
                        onClick = { viewModel.cycleRepeatMode(context) },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = repeatScale
                                scaleY = repeatScale
                            }
                    ) {
                        AnimatedContent(
                            targetState = isRepeatOne,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.7f, animationSpec = tween(150)) + fadeIn(tween(150)))
                                    .togetherWith(scaleOut(targetScale = 0.7f, animationSpec = tween(120)) + fadeOut(tween(120)))
                            },
                            label = "RepeatOneAnim"
                        ) { repeatOne ->
                            Icon(
                                imageVector = if (repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = if (repeatOne) "Repeat One" else if (isRepeatAll) "Repeat All" else "Repeat Off",
                                tint = repeatTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Elevated Floating Utility Action Pill Dock (Safe >20dp above gesture bar)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isLyricsVisible = !isLyricsVisible },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Lyrics",
                                tint = if (isLyricsVisible) accent else textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { showSleepTimerSheet = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AvTimer,
                                contentDescription = "Sleep Timer",
                                tint = textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { showQueueSheet = true },
                            modifier = Modifier.size(44.dp)
                        ) {
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
                            },
                            modifier = Modifier.size(44.dp)
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

                        IconButton(
                            onClick = { showAddToPlaylistSheet = true },
                            modifier = Modifier.size(44.dp)
                        ) {
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
            onRemove = { globalIndex, track -> viewModel.removeFromQueue(track.id, globalIndex) },
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
