package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AlbumArtBackground(
    albumArtUri: String?,
    contentDescription: String = "Album Art",
    initialBias: Float = 0f,
    onBiasChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var horizontalBias by remember(albumArtUri) { mutableFloatStateOf(initialBias) }
    var isAdjusting by remember { mutableStateOf(false) }

    var containerWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width.toFloat()
            }
            .pointerInput(albumArtUri, containerWidth) {
                if (containerWidth > 0f) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isAdjusting = true
                        },
                        onDragEnd = {
                            isAdjusting = false
                            onBiasChanged?.invoke(horizontalBias)
                        },
                        onDragCancel = {
                            isAdjusting = false
                            onBiasChanged?.invoke(horizontalBias)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaBias = -dragAmount.x / (containerWidth * 0.5f)
                            horizontalBias = (horizontalBias + deltaBias).coerceIn(-1.0f, 1.0f)
                        }
                    )
                }
            }
    ) {
        if (!albumArtUri.isNullOrEmpty()) {
            SmartArtworkImage(
                artworkUrl = albumArtUri,
                contentDescription = contentDescription,
                alignment = BiasAlignment(horizontalBias, 0f),
                contentScale = ContentScale.Crop,
                thumbnailQuality = "Highest (1080p Maxres)",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isAdjusting) 0.88f else 1.0f
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Visual adjustment hint badge while long-press dragging to frame cover
        AnimatedVisibility(
            visible = isAdjusting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .padding(top = 90.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "↔️ Drag sideways to frame cover • Release to lock",
                    color = Color(0xFFFF512F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
