package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.LyricsData

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LyricsView(
    lyrics: LyricsData?,
    currentPositionMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (prevLine, currLine, nextLine) = lyrics?.getDisplayLines(currentPositionMs)
        ?: Triple(null, "No lyrics available", null)

    val activeIndex = remember(currentPositionMs, lyrics) {
        if (lyrics != null && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx < 0) 0 else idx
        } else 0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isExpanded) Color.Black.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isExpanded = !isExpanded
            }
            .padding(12.dp)
    ) {
        if (isExpanded && lyrics != null && lyrics.lines.isNotEmpty()) {
            // Expanded Full Lyrics View
            val listState = rememberLazyListState()

            LaunchedEffect(activeIndex) {
                if (activeIndex in lyrics.lines.indices) {
                    listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Text(
                    text = "Tap to collapse (Compact view)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF512F),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 8.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = if (isActive) 24.sp else 16.sp,
                                lineHeight = if (isActive) 32.sp else 22.sp
                            ),
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            // Compact 3-Line View with Smooth Upscrolling Animation
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                AnimatedContent(
                    targetState = currLine to prevLine,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { height -> -height / 2 } + fadeOut())
                    },
                    label = "LyricsAnimation"
                ) { (current, previous) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Previous Line
                        if (!previous.isNullOrBlank()) {
                            Text(
                                text = previous,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                ),
                                color = Color.White.copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Current Line (Reduced Font Size to 28sp)
                        Text(
                            text = current,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            color = Color.White.copy(alpha = if (lyrics == null) 0.4f else 1f),
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Next Line
                if (!nextLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = nextLine,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 24.sp
                        ),
                        color = Color.White.copy(alpha = 0.35f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
