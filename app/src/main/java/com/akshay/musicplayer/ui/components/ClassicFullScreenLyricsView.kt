package com.akshay.musicplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(ExperimentalAnimationApi::class)
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

    // Header controls state
    var isSearchInputActive by remember { mutableStateOf(false) }
    var isOffsetAdjusterActive by remember { mutableStateOf(false) }
    var isAllLinesMode by remember { mutableStateOf(false) }

    // Search state
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
    LaunchedEffect(activeIndex, isAllLinesMode) {
        if (isAllLinesMode && hasSyncedLines && candidateResults == null && activeIndex in lyrics!!.lines.indices) {
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
            // ─── 1. Top Bar: Dismiss, Track Info, Tools (Search / Offset / Mode) ───
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

                // Right Tool Buttons: Offset, Search, View Mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sync Offset Toggle
                    IconButton(
                        onClick = {
                            isOffsetAdjusterActive = !isOffsetAdjusterActive
                            if (isOffsetAdjusterActive) isSearchInputActive = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Offset Adjuster",
                            tint = if (isOffsetAdjusterActive || lyricsOffsetMs != 0L) accent else textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Candidate Search Toggle
                    IconButton(
                        onClick = {
                            isSearchInputActive = !isSearchInputActive
                            if (isSearchInputActive) isOffsetAdjusterActive = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchInputActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Lyrics Candidates",
                            tint = if (isSearchInputActive || candidateResults != null) accent else textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // View Mode Toggle (Focus Flow vs All Lines List)
                    if (hasSyncedLines) {
                        IconButton(
                            onClick = { isAllLinesMode = !isAllLinesMode },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllLinesMode) Icons.AutoMirrored.Filled.Subject else Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = if (isAllLinesMode) "Focus Synced View" else "All Lines List",
                                tint = if (isAllLinesMode) accent else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ─── 2. Expandable Offset Adjuster Bar ───
            AnimatedVisibility(
                visible = isOffsetAdjusterActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sync: ${if (lyricsOffsetMs >= 0) "+${lyricsOffsetMs / 1000.0}s" else "${lyricsOffsetMs / 1000.0}s"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { viewModel.adjustLyricsOffset(-500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("-0.5s", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.adjustLyricsOffset(500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("+0.5s", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.resetLyricsOffset() },
                            enabled = lyricsOffsetMs != 0L,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                "Reset",
                                fontSize = 12.sp,
                                color = if (lyricsOffsetMs != 0L) textPrimary else textSecondary.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ─── 3. Expandable LRClib Search Input Bar ───
            AnimatedVisibility(
                visible = isSearchInputActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            triggerSearch(searchQuery)
                        }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search song title or artist...", color = textSecondary, fontSize = 13.sp)
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
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ─── 4. Main Body: Candidates / Synced Lyrics / Plain Text / Empty ───
            val currentCandidates = candidateResults
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
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
                                Text("Back to lyrics", color = textSecondary, fontSize = 12.sp)
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
                    if (isAllLinesMode) {
                        // ── ALL LINES SCROLLABLE KARAOKE LIST ──
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 24.dp)
                        ) {
                            itemsIndexed(lyrics.lines) { index, line ->
                                val isActive = index == activeIndex
                                val activeFontSize = (lyricsFont.activeSp + 2f).coerceAtLeast(22f).sp
                                val inactiveFontSize = (lyricsFont.inactiveSp * 0.95f).coerceAtLeast(15f).sp

                                Text(
                                    text = line.text,
                                    fontSize = if (isActive) activeFontSize else inactiveFontSize,
                                    lineHeight = if (isActive) (activeFontSize.value * 1.35f).sp else (inactiveFontSize.value * 1.35f).sp,
                                    letterSpacing = if (isActive) (-0.3).sp else 0.sp,
                                    color = if (isActive) textPrimary else textSecondary.copy(alpha = 0.35f),
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.seekTo(line.timestampMs) }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        // ── FOCUS SYNCED FLOW: EXACT SAME SMOOTH ANIMATION AS COMPACT VIEW IN REELS ──
                        AnimatedContent(
                            targetState = activeIndex,
                            transitionSpec = {
                                (slideInVertically { height -> height / 2 } + fadeIn(tween(260))) togetherWith
                                        (slideOutVertically { height -> -height / 2 } + fadeOut(tween(200)))
                            },
                            label = "ClassicLyricsSlideAnimation",
                            modifier = Modifier.fillMaxSize()
                        ) { targetIdx ->
                            val p2 = if (targetIdx >= 2) lyrics.lines.getOrNull(targetIdx - 2) else null
                            val p1 = if (targetIdx >= 1) lyrics.lines.getOrNull(targetIdx - 1) else null
                            val curr = lyrics.lines.getOrNull(targetIdx)
                            val n1 = lyrics.lines.getOrNull(targetIdx + 1)
                            val n2 = lyrics.lines.getOrNull(targetIdx + 2)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                // Previous Line 2
                                if (p2 != null && p2.text.isNotBlank() && !p2.text.equals("null", ignoreCase = true)) {
                                    Text(
                                        text = p2.text,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = textPrimary.copy(alpha = 0.20f),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.seekTo(p2.timestampMs) }
                                            .padding(vertical = 4.dp)
                                    )
                                }

                                // Previous Line 1
                                if (p1 != null && p1.text.isNotBlank() && !p1.text.equals("null", ignoreCase = true)) {
                                    Text(
                                        text = p1.text,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        color = textPrimary.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.seekTo(p1.timestampMs) }
                                            .padding(vertical = 6.dp)
                                    )
                                }

                                // Current Active Line (Hero Line)
                                if (curr != null && curr.text.isNotBlank() && !curr.text.equals("null", ignoreCase = true)) {
                                    val heroFontSize = (lyricsFont.activeSp + 4f).coerceAtLeast(26f).sp
                                    Text(
                                        text = curr.text,
                                        fontSize = heroFontSize,
                                        lineHeight = (heroFontSize.value * 1.32f).sp,
                                        letterSpacing = (-0.5).sp,
                                        color = textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.seekTo(curr.timestampMs) }
                                            .padding(vertical = 12.dp)
                                    )
                                }

                                // Next Line 1
                                if (n1 != null && n1.text.isNotBlank() && !n1.text.equals("null", ignoreCase = true)) {
                                    Text(
                                        text = n1.text,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        color = textPrimary.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.seekTo(n1.timestampMs) }
                                            .padding(vertical = 6.dp)
                                    )
                                }

                                // Next Line 2
                                if (n2 != null && n2.text.isNotBlank() && !n2.text.equals("null", ignoreCase = true)) {
                                    Text(
                                        text = n2.text,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = textPrimary.copy(alpha = 0.20f),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.seekTo(n2.timestampMs) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
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

            // ─── 5. Bottom Section: Integrated Playback Scrubber & Controls ───
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
