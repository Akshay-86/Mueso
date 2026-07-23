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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineActionsOverlay(
    modifier: Modifier = Modifier,
    repeatMode: Int = 0,
    isSleepTimerActive: Boolean = false,
    sleepTimerLabel: String? = null,
    sleepTimerStatus: String? = null,
    queueSize: Int = 0,
    isOnlineSong: Boolean = false,
    onSleepTimerClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
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
            // Download Button (for online songs)
            if (isOnlineSong) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Save",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Song",
                            tint = accentColor,
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

            // Repeat Mode
            val repeatIcon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
            val repeatTint by animateColorAsState(
                if (repeatMode != 0) accentColor else Color.White,
                animationSpec = tween(300),
                label = "repeatTint"
            )
            val repeatLabel = when (repeatMode) {
                1 -> "All"
                2 -> "1"
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
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
