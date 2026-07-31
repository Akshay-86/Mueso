package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class SleepTimerMode { TIMER, AFTER_SONG, END_OF_PLAYLIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    activeSleepMode: SleepTimerMode?,
    activeTimerMinutes: Int?,
    activeSleepSongId: Long?,
    isPlaylistContext: Boolean = false,
    isDarkMode: Boolean = true,
    onSetTimer: (minutes: Int) -> Unit,
    onSetAfterSong: (trackId: Long) -> Unit,
    onSetEndOfPlaylist: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val accentColor = Color(0xFFFF512F)
    val accentGradient = Brush.horizontalGradient(listOf(Color(0xFFFF512F), Color(0xFFDD2476)))

    val sheetBg = if (isDarkMode) Color(0xFF1A1A2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sleep Timer",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Show active status
                if (activeSleepMode != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when (activeSleepMode) {
                        SleepTimerMode.TIMER -> "${activeTimerMinutes}m remaining"
                        SleepTimerMode.AFTER_SONG -> {
                            val songTitle = tracks.find { it.id == activeSleepSongId }?.title ?: "song"
                            "Stops after \"$songTitle\""
                        }
                        SleepTimerMode.END_OF_PLAYLIST -> "Stops at end of playlist"
                    }
                    Text(
                        text = statusText,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple("Timer", Icons.Default.AccessTime, 0),
                    Triple("After Song", Icons.Default.MusicNote, 1),
                    Triple("End of List", Icons.AutoMirrored.Filled.PlaylistPlay, 2)
                )
                tabs.forEach { (label, icon, index) ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                            selectedLabelColor = accentColor,
                            selectedLeadingIconColor = accentColor,
                            containerColor = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                            labelColor = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                            iconColor = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f),
                            selectedBorderColor = accentColor.copy(alpha = 0.5f),
                            enabled = true,
                            selected = selectedTab == index
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> TimerTab(
                    activeMinutes = if (activeSleepMode == SleepTimerMode.TIMER) activeTimerMinutes else null,
                    accentColor = accentColor,
                    isDarkMode = isDarkMode,
                    onSelect = onSetTimer
                )
                1 -> AfterSongTab(
                    tracks = tracks,
                    currentTrackId = currentTrackId,
                    activeSongId = if (activeSleepMode == SleepTimerMode.AFTER_SONG) activeSleepSongId else null,
                    accentColor = accentColor,
                    isDarkMode = isDarkMode,
                    onSelect = onSetAfterSong
                )
                2 -> EndOfPlaylistTab(
                    isActive = activeSleepMode == SleepTimerMode.END_OF_PLAYLIST,
                    isPlaylistContext = isPlaylistContext,
                    accentColor = accentColor,
                    accentGradient = accentGradient,
                    isDarkMode = isDarkMode,
                    onSet = onSetEndOfPlaylist
                )
            }

            // Cancel button if a timer is active
            if (activeSleepMode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onCancelTimer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Sleep Timer", color = Color(0xFFFF6B6B))
                }
            }
        }
    }
}

@Composable
private fun TimerTab(
    activeMinutes: Int?,
    accentColor: Color,
    isDarkMode: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val presets = listOf(5, 10, 15, 30, 45, 60)
    var showCustomPicker by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(15) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Stop playing after",
            color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Preset grid: 2 rows of 3
        for (row in presets.chunked(3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { minutes ->
                    val isActive = activeMinutes == minutes
                    val bgColor by animateColorAsState(
                        if (isActive) accentColor.copy(alpha = 0.2f)
                        else (if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
                        label = "timerBg"
                    )
                    val textColor by animateColorAsState(
                        if (isActive) accentColor else (if (isDarkMode) Color.White else Color(0xFF1D1D1F)),
                        label = "timerText"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$minutes",
                                color = textColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "min",
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Divider with "or" label
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f)
            )
            Text(
                "or set custom",
                color = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73),
                fontSize = 12.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom time picker
        if (!showCustomPicker) {
            // Collapsed: show a button to expand
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
                    .clickable { showCustomPicker = true }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Custom Time",
                        color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF1D1D1F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Expanded: scroll wheel picker + confirm button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours wheel
                    ScrollWheelPicker(
                        items = (0..12).toList(),
                        selectedIndex = customHours,
                        onSelectedChange = { customHours = it },
                        label = "hr",
                        accentColor = accentColor,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.width(80.dp)
                    )

                    Text(
                        ":",
                        color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF1D1D1F),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Minutes wheel
                    ScrollWheelPicker(
                        items = (0..59).toList(),
                        selectedIndex = customMinutes,
                        onSelectedChange = { customMinutes = it },
                        label = "min",
                        accentColor = accentColor,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.width(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val totalMinutes = customHours * 60 + customMinutes
                Button(
                    onClick = { if (totalMinutes > 0) onSelect(totalMinutes) },
                    enabled = totalMinutes > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(
                        if (totalMinutes > 0) {
                            val h = totalMinutes / 60
                            val m = totalMinutes % 60
                            when {
                                h > 0 && m > 0 -> "Set ${h}h ${m}m"
                                h > 0 -> "Set ${h}h"
                                else -> "Set ${m}m"
                            }
                        } else "Select time",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollWheelPicker(
    items: List<Int>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    label: String,
    accentColor: Color,
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val visibleItems = 3
    val itemHeight = 40.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    // Sync scroll to selected item
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex + if (listState.firstVisibleItemScrollOffset > itemHeight.value * 0.5f) 1 else 0
        }
            .distinctUntilChanged()
            .collect { centerIndex ->
                val clampedIndex = centerIndex.coerceIn(0, items.size - 1)
                onSelectedChange(clampedIndex)
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.height(itemHeight * visibleItems),
            contentAlignment = Alignment.Center
        ) {
            // Center highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f))
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.height(itemHeight * visibleItems)
            ) {
                // Padding items for centering
                item {
                    Spacer(modifier = Modifier.height(itemHeight))
                }
                items(items.size) { index ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(items[index]),
                            color = if (isSelected) accentColor else (if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)),
                            fontSize = if (isSelected) 26.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // Padding items for centering
                item {
                    Spacer(modifier = Modifier.height(itemHeight))
                }
            }
        }
    }
}

@Composable
private fun AfterSongTab(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    activeSongId: Long?,
    accentColor: Color,
    isDarkMode: Boolean = true,
    onSelect: (Long) -> Unit
) {
    Column {
        Text(
            "Stop after this song finishes",
            color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show tracks from current onwards
        val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        val relevantTracks = tracks.subList(currentIndex, tracks.size)

        LazyColumn(
            modifier = Modifier.height(280.dp)
        ) {
            itemsIndexed(relevantTracks) { _, track ->
                val isSelected = track.id == activeSongId
                val isCurrent = track.id == currentTrackId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(track.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Playing/selected indicator
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> accentColor.copy(alpha = 0.3f)
                                    isCurrent -> if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                                    else -> if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.StopCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) (if (isDarkMode) Color.White else Color(0xFF1D1D1F)) else (if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73)),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = when {
                                isSelected -> accentColor
                                isCurrent -> if (isDarkMode) Color.White else Color(0xFF1D1D1F)
                                else -> if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF1D1D1F)
                            },
                            fontSize = 14.sp,
                            fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track.artist,
                            color = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrent) {
                        EqualizerBarsAnimation(color = accentColor)
                    } else if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerBarsAnimation(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eqAnim")
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        modifier = modifier.size(width = 18.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(height1)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(height2)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(height3)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

@Composable
private fun EndOfPlaylistTab(
    isActive: Boolean,
    isPlaylistContext: Boolean,
    accentColor: Color,
    accentGradient: Brush,
    isDarkMode: Boolean = true,
    onSet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = if (isActive) accentColor else if (!isPlaylistContext) (if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)) else (if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = when {
                !isPlaylistContext -> "Only available when playing a playlist\n(Radio mode has continuous suggestions)"
                isActive -> "Will stop at end of playlist"
                else -> "Stop playback when the current playlist ends"
            },
            color = if (!isPlaylistContext) (if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF6E6E73)) else (if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F)),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Button(
            onClick = onSet,
            enabled = isPlaylistContext,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) accentColor.copy(alpha = 0.2f) else accentColor,
                disabledContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            if (isActive) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Active", fontWeight = FontWeight.SemiBold)
            } else {
                Text("Enable", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
