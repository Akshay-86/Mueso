package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.ui.theme.LocalAccentColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineActionsOverlay(
    modifier: Modifier = Modifier,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = 0, // 0 = OFF, 1 = ALL, 2 = ONE
    sleepTimerStatus: String? = null,
    queueSize: Int = 0,
    isOnlineSong: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    isDownloaded: Boolean = false,
    isSleepTimerActive: Boolean = false,
    sleepTimerLabel: String? = null,
    onSleepTimerClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
    onCancelDownloadClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {}
) {
    val accentColor = LocalAccentColor.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Full-width scrolling status bar
        if (sleepTimerStatus != null) {
            Text(
                text = sleepTimerStatus,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1500,
                        initialDelayMillis = 800,
                        velocity = 40.dp
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Download Button with Progress Indicator & Completed Checkmark
            if (isOnlineSong) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    val labelText = when {
                        isDownloaded -> "Saved"
                        isDownloading -> if (downloadProgress > 0f) "${(downloadProgress * 100).toInt()}%" else "Saving..."
                        else -> null
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = labelText,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                    .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100)))
                            },
                            label = "DlLabelAnim"
                        ) { text ->
                            if (text != null) {
                                Text(
                                    text = text,
                                    color = if (isDownloaded) Color(0xFF4CAF50) else accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 10.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = !isDownloaded,
                                onClick = {
                                    if (isDownloading) {
                                        onCancelDownloadClick()
                                    } else if (!isDownloaded) {
                                        onDownloadClick()
                                    }
                                },
                                onLongClick = {
                                    if (!isDownloading && !isDownloaded) {
                                        onDownloadLongClick()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isDownloading -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    if (downloadProgress > 0f) {
                                        CircularProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.size(24.dp),
                                            color = accentColor,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = accentColor,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Download",
                                        tint = accentColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            isDownloaded -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Song",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Add to Online Playlist Button (for Online Songs)
            if (isOnlineSong) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    )
                    val playlistInteraction = remember { MutableInteractionSource() }
                    val isPlaylistPressed by playlistInteraction.collectIsPressedAsState()
                    val playlistScale by animateFloatAsState(
                        targetValue = if (isPlaylistPressed) 0.88f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "PlaylistPressScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = playlistScale
                                scaleY = playlistScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onAddToPlaylistClick,
                            interactionSource = playlistInteraction,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Add to Online Playlist",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Sleep Timer
            val sleepTint by animateColorAsState(
                if (isSleepTimerActive) accentColor else Color.White,
                animationSpec = tween(300),
                label = "sleepTint"
            )
            val sleepScale by animateFloatAsState(
                targetValue = if (isSleepTimerActive) 1.12f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "sleepScale"
            )
            val sleepRotation by animateFloatAsState(
                targetValue = if (isSleepTimerActive) 20f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
                label = "sleepRotation"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = sleepTimerLabel,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100)))
                        },
                        label = "SleepLabelAnim"
                    ) { label ->
                        if (label != null) {
                            Text(
                                text = label,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = sleepScale
                            scaleY = sleepScale
                            rotationZ = sleepRotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onSleepTimerClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AvTimer,
                            contentDescription = "Sleep Timer",
                            tint = sleepTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Shuffle Mode Toggle
            val shuffleTint by animateColorAsState(
                if (isShuffleEnabled) accentColor else Color.White,
                animationSpec = tween(300),
                label = "shuffleTint"
            )
            val shuffleScale by animateFloatAsState(
                targetValue = if (isShuffleEnabled) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "shuffleScale"
            )
            val shuffleRotation by animateFloatAsState(
                targetValue = if (isShuffleEnabled) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
                label = "shuffleRotation"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = if (isShuffleEnabled) "ON" else null,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100)))
                        },
                        label = "ShuffleLabelAnim"
                    ) { label ->
                        if (label != null) {
                            Text(
                                text = label,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = shuffleScale
                            scaleY = shuffleScale
                            rotationZ = shuffleRotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onShuffleClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (isShuffleEnabled) "Shuffle On" else "Shuffle Off",
                            tint = shuffleTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Repeat Mode (0 = OFF, 1 = ONE, 2 = ALL)
            val isRepeatOne = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
            val isRepeatAll = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
            val isRepeatActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
            val repeatIcon = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat
            val repeatTint by animateColorAsState(
                if (isRepeatActive) accentColor else Color.White,
                animationSpec = tween(300),
                label = "repeatTint"
            )
            val repeatScale by animateFloatAsState(
                targetValue = if (isRepeatActive) 1.12f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "repeatScale"
            )
            val repeatRotation by animateFloatAsState(
                targetValue = if (isRepeatOne) 360f else if (isRepeatAll) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 350f),
                label = "repeatRotation"
            )
            val repeatLabel = when (repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ALL -> "ALL"
                androidx.media3.common.Player.REPEAT_MODE_ONE -> "ONE"
                else -> null
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = repeatLabel,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100)))
                        },
                        label = "RepeatLabelAnim"
                    ) { label ->
                        if (label != null) {
                            Text(
                                text = label,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = repeatScale
                            scaleY = repeatScale
                            rotationZ = repeatRotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AnimatedContent(
                            targetState = repeatIcon,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.7f, animationSpec = tween(150)) + fadeIn(tween(150)))
                                    .togetherWith(scaleOut(targetScale = 0.7f, animationSpec = tween(120)) + fadeOut(tween(120)))
                            },
                            label = "RepeatIconAnim"
                        ) { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Queue
            val queueScale by animateFloatAsState(
                targetValue = if (queueSize > 0) 1.08f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "queueScale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = if (queueSize > 0) "$queueSize" else null,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100)))
                        },
                        label = "QueueLabelAnim"
                    ) { label ->
                        if (label != null) {
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = queueScale
                            scaleY = queueScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onQueueClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
