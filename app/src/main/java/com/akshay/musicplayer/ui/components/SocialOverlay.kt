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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akshay.musicplayer.domain.models.SocialMetrics

@Composable
fun SocialOverlay(
    metrics: SocialMetrics?,
    onLikeClick: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLiked = remember { mutableStateOf(false) }
    val liked = isLiked.value
    val heartScale by animateFloatAsState(
        targetValue = if (liked) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "HeartScale"
    )
    val heartColor by animateColorAsState(
        targetValue = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "HeartColor"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = metrics?.likeCount ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                IconButton(
                    onClick = {
                        isLiked.value = !isLiked.value
                        onLikeClick(isLiked.value)
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
                ) {
                    AnimatedContent(
                        targetState = liked,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.7f, animationSpec = tween(180)) + fadeIn(tween(140)))
                                .togetherWith(scaleOut(targetScale = 0.7f, animationSpec = tween(140)) + fadeOut(tween(100)))
                        },
                        label = "LikeHeartAnim"
                    ) { isNowLiked ->
                        Icon(
                            imageVector = if (isNowLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isNowLiked) "Unlike" else "Like",
                            tint = heartColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            SocialIconWithLabel(
                icon = Icons.Default.ChatBubbleOutline,
                label = metrics?.commentCount ?: "-",
                onClick = { /* TODO */ }
            )

            SocialIconWithLabel(
                icon = Icons.Default.Share,
                label = metrics?.shareCount ?: "-",
                onClick = onShareClick
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO */ }) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = { /* TODO */ }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SocialIconWithLabel(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
