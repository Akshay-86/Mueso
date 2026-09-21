@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.LrclibSearchResultItem
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalAccentGradient
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.theme.LocalLyricsFontSize
import com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ClassicFullScreenLyricsView(
    track: TrackEntity,
    playbackState: PlaybackState,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val accent = LocalAccentColor.current
    val accentGradient = LocalAccentGradient.current
    val isPureBlack = LocalIsPureBlack.current
    val lyricsFont = LocalLyricsFontSize.current

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()
    val lyricsFetchStatusMap by viewModel.lyricsFetchStatus.collectAsState()
    val trackLyricsStatus = lyricsFetchStatusMap[track.id] ?: LyricsFetchStatus.IDLE
    val isShuffleEnabled by viewModel.isShuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val bgColor = when {
        isPureBlack -> Color.Black
        isDarkMode -> Color(0xFF0D0D12)
        else -> Color(0xFFF7F7FA)
    }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    // Search and tools state
    var isSearchInputActive by remember { mutableStateOf(false) }
    var isOffsetAdjusterActive by remember { mutableStateOf(false) }
    var searchQuery by remember(track.title) { mutableStateOf(track.title) }
    var isSearchingCandidates by remember { mutableStateOf(false) }
    var candidateResults by remember { mutableStateOf<List<LrclibSearchResultItem>?>(null) }

    // Seekbar dragging state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val currentPosMs = if (isDraggingSlider) dragSliderValue.toLong() else playbackState.currentPositionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val adjustedPositionMs = (currentPosMs + lyricsOffsetMs).coerceAtLeast(0L)

    val lyrics = track.lyrics
    val hasSyncedLines = lyrics != null && lyrics.lines.isNotEmpty()
    val hasPlainText = lyrics != null && !lyrics.rawText.isNullOrBlank() && !lyrics.rawText.equals("null", ignoreCase = true)

    val activeIndex = remember(adjustedPositionMs, lyrics) {
        if (lyrics != null && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= adjustedPositionMs }
            if (idx < 0) 0 else idx
        } else 0
    }

    val listState = rememberLazyListState()

    // Smoothly auto-scroll to keep the active line centered
    LaunchedEffect(activeIndex) {
        if (candidateResults == null && lyrics != null && lyrics.lines.isNotEmpty() && activeIndex in lyrics.lines.indices) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    fun triggerSearch(q: String) {
        if (q.isBlank()) return
        isSearchingCandidates = true
        coroutineScope.launch {
            val results = viewModel.searchLrclibCandidates(q)
            candidateResults = results
            isSearchingCandidates = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // ─── 1. Top Navigation Bar: Dismiss Arrow & Track Info & Action Buttons ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Collapse Arrow
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Lyrics",
                        tint = textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center: Track Title & Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = track.artist,
                        color = textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Action Buttons (Sync Adjuster & Online Search)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sync offset toggle button
                    IconButton(
                        onClick = {
                            isOffsetAdjusterActive = !isOffsetAdjusterActive
                            if (isOffsetAdjusterActive) isSearchInputActive = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Adjust Lyrics Sync",
                            tint = if (isOffsetAdjusterActive || lyricsOffsetMs != 0L) accent else textPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Online lyrics search toggle button
                    IconButton(
                        onClick = {
                            isSearchInputActive = !isSearchInputActive
                            if (isSearchInputActive) {
                                isOffsetAdjusterActive = false
                            } else {
                                candidateResults = null
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchInputActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Lyrics Online",
                            tint = if (isSearchInputActive || candidateResults != null) accent else textPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ─── 2. Expandable Search Bar Panel ───
            AnimatedVisibility(
                visible = isSearchInputActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            triggerSearch(searchQuery)
                        }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search song title or artist...",
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                candidateResults = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Standalone Round Search Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable {
                                keyboardController?.hide()
                                triggerSearch(searchQuery)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearchingCandidates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ─── 3. Expandable Sync Offset Adjuster Panel ───
            AnimatedVisibility(
                visible = isOffsetAdjusterActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Sync: ${if (lyricsOffsetMs >= 0) "+${lyricsOffsetMs / 1000.0}s" else "${lyricsOffsetMs / 1000.0}s"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { viewModel.adjustLyricsOffset(-500L) },
                            shape = RoundedCornerShape(12.dp),
                            color = accent.copy(alpha = 0.15f),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-0.5s", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = { viewModel.adjustLyricsOffset(500L) },
                            shape = RoundedCornerShape(12.dp),
                            color = accent.copy(alpha = 0.15f),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+0.5s", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (lyricsOffsetMs != 0L) {
                            Surface(
                                onClick = { viewModel.resetLyricsOffset() },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Reset", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        IconButton(
                            onClick = { isOffsetAdjusterActive = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Sync Adjuster",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ─── 3. Main Body: Show ALL Lyrics at once OR Candidate Search Results ───
            val currentCandidates = candidateResults
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isSearchingCandidates) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Searching lyrics candidates online...", color = textSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (currentCandidates != null) {
                    // Candidate Results List
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${currentCandidates.size} Matches",
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { candidateResults = null }) {
                                Text("Show lyrics", color = textSecondary, fontSize = 12.sp)
                            }
                        }

                        if (currentCandidates.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No matches found for \"$searchQuery\"", color = textSecondary, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                items(currentCandidates, key = { it.id }) { item ->
                                    FullScreenCandidateCard(
                                        item = item,
                                        accent = accent,
                                        isDarkMode = isDarkMode,
                                        onClick = {
                                            viewModel.applyLrclibCandidate(track.id, item)
                                            candidateResults = null
                                            isSearchInputActive = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (trackLyricsStatus == LyricsFetchStatus.FETCHING) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching lyrics for \"${track.title}\"...", color = textSecondary, fontSize = 14.sp)
                        }
                    }
                } else if (lyrics != null && lyrics.lines.isNotEmpty()) {
                    // ── SHOW ALL LYRICS AT ONCE, HIGHLIGHT ONLY ACTIVE LINE, AUTO-SCROLL TO CENTER ──
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 80.dp, bottom = 140.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeIndex
                            val animatedAlpha by animateFloatAsState(
                                targetValue = if (isActive) 1f else 0.32f,
                                animationSpec = tween(250),
                                label = "lyricsLineAlpha"
                            )
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isActive) 1.03f else 1.0f,
                                animationSpec = tween(250),
                                label = "lyricsLineScale"
                            )
                            val activeFontSize = (lyricsFont.activeSp + 4f).coerceAtLeast(24f).sp
                            val inactiveFontSize = (lyricsFont.inactiveSp * 1.05f).coerceAtLeast(16f).sp

                            Text(
                                text = line.text,
                                fontSize = if (isActive) activeFontSize else inactiveFontSize,
                                lineHeight = if (isActive) (activeFontSize.value * 1.35f).sp else (inactiveFontSize.value * 1.35f).sp,
                                letterSpacing = if (isActive) (-0.3).sp else 0.sp,
                                color = if (isActive) textPrimary else textSecondary.copy(alpha = animatedAlpha),
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.seekTo(line.timestampMs) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                } else if (lyrics != null && !lyrics.rawText.isNullOrBlank() && !lyrics.rawText.equals("null", ignoreCase = true)) {
                    // Plain Text Lyrics
                    val plainLines = lyrics.rawText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Plain text lyrics • Synced unavailable",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(plainLines) { line ->
                                Text(
                                    text = line,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // No lyrics found state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "No lyrics found for this song",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "You can search and link lyrics online manually",
                                color = textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { isSearchInputActive = true },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search Lyrics Online", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ─── 4. Bottom Section: Integrated Playback Scrubber & Controls ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Seekbar slider & Timestamps
                Slider(
                    value = currentPosMs.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        dragSliderValue = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(dragSliderValue.toLong())
                        isDraggingSlider = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosMs), color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(text = formatTime(durationMs), color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Playback Controls Row: Shuffle, Prev, Play/Pause Hero, Next, Repeat, Dismiss Lyrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) accent else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Previous Track
                    IconButton(onClick = { viewModel.playPreviousTrack() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = textPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Play/Pause Hero Button
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(8.dp, CircleShape, spotColor = accent.copy(alpha = 0.5f))
                            .clip(CircleShape)
                            .background(accentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Next Track
                    IconButton(onClick = { viewModel.playNextTrack() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = textPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Repeat Mode
                    val isRepeatOne = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
                    val isRepeatAll = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
                    IconButton(onClick = { viewModel.cycleRepeatMode(context) }) {
                        Icon(
                            imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeatOne || isRepeatAll) accent else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Active Lyrics Icon (Tap to close full-screen lyrics back to album art)
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Hide Lyrics",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenCandidateCard(
    item: LrclibSearchResultItem,
    accent: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val durationMin = item.durationSeconds / 60
    val durationSec = item.durationSeconds % 60
    val formattedDuration = String.format(Locale.getDefault(), "%d:%02d", durationMin, durationSec)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.trackName,
                color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formattedDuration,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73),
                    fontSize = 11.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isSynced) accent.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.isSynced) "Synced" else "Plain",
                        color = if (item.isSynced) accent else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val subtitle = listOf(item.artistName, item.albumName).filter { it.isNotBlank() }.joinToString(" • ")
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
