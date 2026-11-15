package com.akshay.musicplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.flow.distinctUntilChanged

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
    val accentColor = com.akshay.musicplayer.ui.theme.LocalAccentColor.current
    val accentGradient = com.akshay.musicplayer.ui.theme.LocalAccentGradient.current

    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
    val sheetBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1A1A2E)) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sleep Timer",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Timer Hero Card with AnimatedContent
            AnimatedContent(
                targetState = activeSleepMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                },
                label = "ActiveTimerHeroCard"
            ) { mode ->
                if (mode != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                val statusText = when (mode) {
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick extension pills if mode is timer
                            if (mode == SleepTimerMode.TIMER) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QuickExtensionPill(
                                        label = "+5 min",
                                        accentColor = accentColor,
                                        onClick = { onSetTimer((activeTimerMinutes ?: 0) + 5) }
                                    )
                                    QuickExtensionPill(
                                        label = "+15 min",
                                        accentColor = accentColor,
                                        onClick = { onSetTimer((activeTimerMinutes ?: 0) + 15) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab row with spring press feedback & animated indicator
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
                    val isTabSelected = selectedTab == index
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val tabScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else if (isTabSelected) 1.02f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "TabScale_$index"
                    )

                    Surface(
                        onClick = { selectedTab = index },
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isTabSelected) accentColor.copy(alpha = 0.2f)
                        else if (isDarkMode) Color.White.copy(alpha = 0.05f)
                        else Color.Black.copy(alpha = 0.05f),
                        border = BorderStroke(
                            1.dp,
                            if (isTabSelected) accentColor.copy(alpha = 0.5f)
                            else if (isDarkMode) Color.White.copy(alpha = 0.1f)
                            else Color.Black.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isTabSelected) accentColor
                                else if (isDarkMode) Color.White.copy(alpha = 0.7f)
                                else Color(0xFF1D1D1F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabSelected) accentColor
                                else if (isDarkMode) Color.White.copy(alpha = 0.7f)
                                else Color(0xFF1D1D1F)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AnimatedContent for smooth Tab Transitions
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> width / 3 } + fadeOut())
                    }
                },
                label = "SleepTimerTabContent"
            ) { tabIndex ->
                when (tabIndex) {
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
            }

            // Cancel button with AnimatedVisibility
            AnimatedVisibility(
                visible = activeSleepMode != null,
                enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Column {
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
}

@Composable
private fun QuickExtensionPill(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "ExtensionPillScale_$label"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = accentColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PresetCard(
    minutes: Int,
    isActive: Boolean,
    accentColor: Color,
    isDarkMode: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "PresetScale_$minutes"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.20f)
        else (if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
        animationSpec = tween(200),
        label = "PresetBgColor_$minutes"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.75f)
        else (if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
        animationSpec = tween(200),
        label = "PresetBorderColor_$minutes"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) accentColor else (if (isDarkMode) Color.White else Color(0xFF1D1D1F)),
        animationSpec = tween(200),
        label = "PresetTextColor_$minutes"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelect(minutes) }
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedContent(
                    targetState = isActive,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                            .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
                    },
                    label = "PresetIcon_$minutes"
                ) { active ->
                    if (active) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = "min",
                    color = if (isActive) accentColor.copy(alpha = 0.85f) else (if (isDarkMode) Color.White.copy(alpha = 0.45f) else Color(0xFF6E6E73)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$minutes",
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
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

        // Preset grid: 2 rows of 3 with elevated preset cards
        for (row in presets.chunked(3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { minutes ->
                    val isActive = activeMinutes == minutes
                    PresetCard(
                        minutes = minutes,
                        isActive = isActive,
                        accentColor = accentColor,
                        isDarkMode = isDarkMode,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
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

        // Custom time picker with AnimatedContent transition
        AnimatedContent(
            targetState = showCustomPicker,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + expandVertically())
                    .togetherWith(fadeOut(animationSpec = tween(150)) + shrinkVertically())
            },
            label = "CustomPickerAnim"
        ) { isExpanded ->
            if (!isExpanded) {
                // Collapsed: show an elevated button to expand with spring press
                val customInteraction = remember { MutableInteractionSource() }
                val isCustomPressed by customInteraction.collectIsPressedAsState()
                val customScale by animateFloatAsState(
                    targetValue = if (isCustomPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                    label = "CustomCardScale"
                )

                Surface(
                    onClick = { showCustomPicker = true },
                    interactionSource = customInteraction,
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = customScale
                            scaleY = customScale
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                        .border(
                            1.dp,
                            if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            RoundedCornerShape(14.dp)
                        )
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
            modifier = Modifier.height(280.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(relevantTracks) { _, track ->
                val isSelected = track.id == activeSongId
                val isCurrent = track.id == currentTrackId

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val rowScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "AfterSongRowScale_${track.id}"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = rowScale
                            scaleY = rowScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (isSelected) accentColor.copy(alpha = 0.4f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(track.id) }
                        )
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
                        AnimatedContent(
                            targetState = isSelected,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                                    .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
                            },
                            label = "AfterSongIcon_${track.id}"
                        ) { selected ->
                            if (selected) {
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "EndOfPlaylistBtnScale"
    )

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
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) accentColor.copy(alpha = 0.2f) else accentColor,
                disabledContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
        ) {
            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                        .togetherWith(scaleOut(targetScale = 0.5f) + fadeOut())
                },
                label = "EndOfPlaylistBtnContent"
            ) { active ->
                if (active) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active", color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Enable", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
