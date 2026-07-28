package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.LrclibSearchResultItem
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LyricsView(
    lyrics: LyricsData?,
    currentPositionMs: Long = 0L,
    lyricsFetchStatus: LyricsFetchStatus = LyricsFetchStatus.IDLE,
    lyricsOffsetMs: Long = 0L,
    trackTitle: String = "",
    trackArtist: String = "",
    isSavedInUserPlaylist: Boolean = false,
    isExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onAdjustOffset: (Long) -> Unit = {},
    onResetOffset: () -> Unit = {},
    onSearchCandidates: suspend (query: String) -> List<LrclibSearchResultItem> = { emptyList() },
    onApplyCandidate: (LrclibSearchResultItem) -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adjustedPositionMs = (currentPositionMs + lyricsOffsetMs).coerceAtLeast(0L)

    val (prevLine, currLine, nextLine) = when (lyricsFetchStatus) {
        LyricsFetchStatus.FETCHING -> Triple(null, "Searching lyrics...", null)
        LyricsFetchStatus.NOT_FOUND -> Triple(null, "No lyrics found", null)
        else -> lyrics?.getDisplayLines(adjustedPositionMs) ?: Triple(null, if (lyricsFetchStatus == LyricsFetchStatus.FETCHING) "Searching lyrics..." else "No lyrics available (Tap to search)", null)
    }

    AnimatedContent(
        targetState = isExpanded,
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
        },
        label = "LyricsViewExpandTransition",
        modifier = modifier.fillMaxWidth()
    ) { expanded ->
        if (!expanded) {
            // Normal Player View: Clean 3-Line Synced Lyrics ONLY (In Place)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChange(true)
                    }
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    AnimatedContent(
                        targetState = Triple(prevLine, currLine, nextLine),
                        transitionSpec = {
                            (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height / 2 } + fadeOut())
                        },
                        label = "LyricsAnimation"
                    ) { (previous, current, next) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Previous Line
                            if (!previous.isNullOrBlank()) {
                                Text(
                                    text = previous,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Current Active Line (26sp ExtraBold)
                            Text(
                                text = current,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontSize = 26.sp,
                                    lineHeight = 34.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = Color.White.copy(
                                    alpha = if (lyricsFetchStatus == LyricsFetchStatus.FETCHING || lyrics == null) 0.5f else 1f
                                ),
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Next Line
                            if (!next.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = next,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 23.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap for controls & search 🔍",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF512F).copy(alpha = 0.9f)
                        )
                        if (isSavedInUserPlaylist) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Saved in Playlist", tint = Color(0xFF34C759), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Saved", color = Color(0xFF34C759), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Expanded Translucent Glass Window ONLY (3-line view is hidden)
            InlineLyricsGlassCard(
                lyrics = lyrics,
                adjustedPositionMs = adjustedPositionMs,
                lyricsFetchStatus = lyricsFetchStatus,
                lyricsOffsetMs = lyricsOffsetMs,
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                isSavedInUserPlaylist = isSavedInUserPlaylist,
                onClose = { onExpandedChange(false) },
                onAdjustOffset = onAdjustOffset,
                onResetOffset = onResetOffset,
                onSearchCandidates = onSearchCandidates,
                onApplyCandidate = { candidate ->
                    onApplyCandidate(candidate)
                    onExpandedChange(false)
                },
                onSeekTo = onSeekTo
            )
        }
    }
}

@Composable
private fun InlineLyricsGlassCard(
    lyrics: LyricsData?,
    adjustedPositionMs: Long,
    lyricsFetchStatus: LyricsFetchStatus,
    lyricsOffsetMs: Long,
    trackTitle: String,
    trackArtist: String,
    isSavedInUserPlaylist: Boolean,
    onClose: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSearchCandidates: suspend (query: String) -> List<LrclibSearchResultItem>,
    onApplyCandidate: (LrclibSearchResultItem) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var isSearchInputActive by remember { mutableStateOf(false) }
    var searchQuery by remember(trackTitle) { mutableStateOf(trackTitle) }
    var isSearching by remember { mutableStateOf(false) }
    var candidateResults by remember { mutableStateOf<List<LrclibSearchResultItem>?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeIndex = remember(adjustedPositionMs, lyrics) {
        if (lyrics != null && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= adjustedPositionMs }
            if (idx < 0) 0 else idx
        } else 0
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (candidateResults == null && lyrics != null && activeIndex in lyrics.lines.indices) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    fun triggerSearch(query: String) {
        if (query.isBlank()) return
        isSearching = true
        coroutineScope.launch {
            val results = onSearchCandidates(query)
            candidateResults = results
            isSearching = false
        }
    }

    // Translucent Glass Card Floating in Player Center with Glowing Accent Border
    // Tapping empty background space inside card collapses back to 3-line view!
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(BorderStroke(1.2.dp, Color(0xFFFF512F).copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping empty background space inside box collapses window!
                onClose()
            }
            .padding(12.dp)
    ) {
        // Saved Status Header (If track is in user playlist)
        if (isSavedInUserPlaylist) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lyrics saved to playlist", color = Color(0xFF34C759), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Single Row: Left Pill Capsule (Offset or Search Input) + Right Standalone Round Search Button (NO EXTRA 3RD BUTTON)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume click so control bar doesn't trigger collapse */ },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchInputActive) {
                // Expanded Left Search Bar Pill Capsule (EXACT SAME SHAPE & SIZE AS OFFSET CAPSULE!)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            triggerSearch(searchQuery)
                        }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search song...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear text",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    searchQuery = ""
                                    candidateResults = null
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Standalone Round Orange Search Button (Clicking searches!)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF512F))
                        .clickable {
                            keyboardController?.hide()
                            triggerSearch(searchQuery)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                // Default State: Standalone Left Offset Pill Capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFFFF512F), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Offset: ${if (lyricsOffsetMs >= 0) "+${lyricsOffsetMs / 1000.0}s" else "${lyricsOffsetMs / 1000.0}s"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(
                        onClick = { onAdjustOffset(-500L) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("-0.5s", fontSize = 11.sp, color = Color(0xFFFF512F), fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { onAdjustOffset(500L) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+0.5s", fontSize = 11.sp, color = Color(0xFFFF512F), fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onResetOffset,
                        enabled = lyricsOffsetMs != 0L,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 11.sp,
                            color = if (lyricsOffsetMs != 0L) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Standalone Round Search Icon Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { isSearchInputActive = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Expand Search", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Content Area: Candidate Results List OR Synced Karaoke Lyrics List
        val currentCandidates = candidateResults
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF512F), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Searching candidates...", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        } else if (currentCandidates != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Matches (${currentCandidates.size})",
                        color = Color(0xFFFF512F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { candidateResults = null },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Show Lyrics", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (currentCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matches for '$searchQuery'", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentCandidates, key = { it.id }) { item ->
                            CandidateResultCard(
                                item = item,
                                onClick = {
                                    onApplyCandidate(item)
                                    candidateResults = null
                                }
                            )
                        }
                    }
                }
            }
        } else if (lyrics != null && lyrics.lines.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = if (isActive) 20.sp else 14.sp,
                            lineHeight = if (isActive) 26.sp else 20.sp
                        ),
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekTo(line.timestampMs) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No lyrics loaded",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap search 🔍 above to pick a candidate",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateResultCard(
    item: LrclibSearchResultItem,
    onClick: () -> Unit
) {
    val durationMin = item.durationSeconds / 60
    val durationSec = item.durationSeconds % 60
    val formattedDuration = String.format("%d:%02d", durationMin, durationSec)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.trackName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = formattedDuration, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (item.isSynced) Color(0xFF2ECC71).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.isSynced) "Synced" else "Plain",
                        color = if (item.isSynced) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val subtitle = listOf(item.albumName, item.artistName).filter { it.isNotBlank() }.joinToString(" - ")
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
