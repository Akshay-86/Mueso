package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineActionsOverlay(
    modifier: Modifier = Modifier,
    repeatMode: Int = 0,
    isShuffleEnabled: Boolean = false,
    isSleepTimerActive: Boolean = false,
    sleepTimerLabel: String? = null,
    sleepTimerStatus: String? = null,
    queueSize: Int = 0,
    isOnlineSong: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    isDownloaded: Boolean = false,
    onSleepTimerClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
    onCancelDownloadClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {}
) {
    val accentColor = Color(0xFFFF512F)

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download Button with Progress Indicator & Completed Checkmark
            if (isOnlineSong) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val labelText = when {
                        isDownloaded -> "Saved"
                        isDownloading -> if (downloadProgress > 0f) "${(downloadProgress * 100).toInt()}%" else "Saving..."
                        else -> null
                    }
                    if (labelText != null) {
                        Text(
                            text = labelText,
                            color = if (isDownloaded) Color(0xFF4CAF50) else accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp
                        )
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onAddToPlaylistClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Add to Online Playlist",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Sleep Timer
            val sleepTint by animateColorAsState(
                if (isSleepTimerActive) accentColor else Color.White,
                animationSpec = tween(300),
                label = "sleepTint"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (sleepTimerLabel != null) {
                    Text(
                        text = sleepTimerLabel,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                }
                IconButton(onClick = onSleepTimerClick) {
                    Icon(
                        imageVector = Icons.Default.AvTimer,
                        contentDescription = "Sleep Timer",
                        tint = sleepTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Shuffle Mode Toggle
            val shuffleTint by animateColorAsState(
                if (isShuffleEnabled) accentColor else Color.White,
                animationSpec = tween(300),
                label = "shuffleTint"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isShuffleEnabled) {
                    Text(
                        text = "ON",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                }
                IconButton(onClick = onShuffleClick) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = shuffleTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Repeat Mode (0 = OFF, 1 = ONE, 2 = ALL)
            val isRepeatOne = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
            val isRepeatAll = repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
            val repeatIcon = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat
            val repeatTint by animateColorAsState(
                if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) accentColor else Color.White,
                animationSpec = tween(300),
                label = "repeatTint"
            )
            val repeatLabel = when (repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ALL -> "ALL"
                androidx.media3.common.Player.REPEAT_MODE_ONE -> "ONE"
                else -> null
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (repeatLabel != null) {
                    Text(
                        text = repeatLabel,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                }
                IconButton(onClick = onRepeatClick) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Queue
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (queueSize > 0) {
                    Text(
                        text = "$queueSize",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                }
                IconButton(onClick = onQueueClick) {
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
