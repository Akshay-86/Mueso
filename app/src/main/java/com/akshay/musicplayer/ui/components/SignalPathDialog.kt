package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.domain.models.ActiveAudioFormat
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.theme.LocalAccentColor

/**
 * Modern Technical Audio Specifications & Signal Path Dialog.
 * Displays the decoded pipeline, stream parameters, and hardware sink routing.
 */
@Composable
fun SignalPathDialog(
    audioFormat: ActiveAudioFormat,
    track: TrackEntity? = null,
    isEqualizerActive: Boolean = false,
    isClarityActive: Boolean = false,
    isBitPerfectActive: Boolean = false,
    onOpenEqualizer: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val isHiRes = audioFormat.isHiRes
    val isLossless = audioFormat.isLossless

    val verdictBadge = when {
        isBitPerfectActive -> "BIT-PERFECT DIRECT"
        isHiRes -> "24-BIT HI-RES AUDIO"
        isLossless -> "LOSSLESS CD QUALITY"
        audioFormat.codec.contains("OPUS", ignoreCase = true) -> "OPUS HD 160K"
        else -> "AAC 256K"
    }

    val verdictColor = when {
        isBitPerfectActive || isHiRes -> Color(0xFF00E676) // Neon Emerald
        isLossless -> Color(0xFF00E5FF)                   // Neon Cyan
        else -> accent
    }

    val eqInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isEqPressed by eqInteraction.collectIsPressedAsState()
    val eqScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isEqPressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "eqScale"
    )

    val doneInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isDonePressed by doneInteraction.collectIsPressedAsState()
    val doneScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDonePressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "doneScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF13131E),
            contentColor = Color.White,
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f), Color.Transparent)
                    ),
                    RoundedCornerShape(32.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── Header ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Technical Audio Specs",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = "Studio Signal Chain & DAC Route",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ─── Hero Track Banner (if track available) ───
                if (track != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(track.artworkUrl ?: android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), track.albumId))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF222232))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    fontSize = 13.sp,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Neon Verdict Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(verdictColor.copy(alpha = 0.18f))
                                    .border(1.dp, verdictColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = verdictBadge,
                                    color = verdictColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }
                }

                // ─── Step 1: Ingest Stream ───
                PipelineCard(
                    stepNumber = "1",
                    title = "INPUT STREAM",
                    icon = Icons.Default.MusicNote,
                    accentColor = verdictColor
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecRow("Audio Codec", audioFormat.codec)
                        SpecRow("Resolution", "${audioFormat.bitDepth}-bit / ${audioFormat.sampleRateHz / 1000f} kHz")
                        SpecRow("Bitrate", "${audioFormat.bitrateKbps} kbps")
                        SpecRow("Content Delivery", audioFormat.sourceName)
                    }
                }

                // Signal Flow Pulse Dots
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(accent.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent.copy(alpha = 0.8f)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(accent.copy(alpha = 0.35f)))
                }

                // ─── Step 2: Processing Engine & DSP ───
                PipelineCard(
                    stepNumber = "2",
                    title = "PROCESSING & DSP",
                    icon = Icons.Default.Tune,
                    accentColor = accent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecRow(
                            label = "Hardware Equalizer",
                            value = if (isBitPerfectActive) "Bypassed (Bit-Perfect)" else if (isEqualizerActive) "Active (5-Band Hardware DSP)" else "Flat / Off",
                            highlight = isEqualizerActive && !isBitPerfectActive
                        )
                        SpecRow(
                            label = "Studio Master Clarity",
                            value = if (isBitPerfectActive) "Bypassed" else if (isClarityActive) "Active (+2.5dB Sparkle)" else "Disabled",
                            highlight = isClarityActive && !isBitPerfectActive
                        )
                        SpecRow(
                            label = "Internal Audio Path",
                            value = if (isBitPerfectActive) "Bit-Perfect Direct Pass-Through" else "32-Bit Float Dynamic PCM"
                        )
                    }
                }

                // Signal Flow Pulse Dots
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF00E5FF).copy(alpha = 0.35f)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00E5FF).copy(alpha = 0.8f)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF00E5FF).copy(alpha = 0.35f)))
                }

                // ─── Step 3: Hardware Output Route ───
                PipelineCard(
                    stepNumber = "3",
                    title = "HARDWARE OUTPUT ROUTE",
                    icon = Icons.Default.Headphones,
                    accentColor = Color(0xFF00E5FF)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecRow("Output Device", audioFormat.audioOutputDevice)
                        SpecRow("Channels", "${audioFormat.channelCount}.0 Stereo (L + R)")
                        SpecRow(
                            label = "DAC Mode",
                            value = if (isBitPerfectActive) "Bit-Perfect Dedicated Sink" else "Android Low-Latency AudioTrack",
                            highlight = isBitPerfectActive
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ─── Action Buttons ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onOpenEqualizer != null) {
                        Surface(
                            onClick = {
                                onDismiss()
                                onOpenEqualizer()
                            },
                            interactionSource = eqInteraction,
                            shape = RoundedCornerShape(24.dp),
                            color = accent.copy(alpha = 0.16f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = eqScale
                                    scaleY = eqScale
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Equalizer", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Surface(
                        onClick = onDismiss,
                        interactionSource = doneInteraction,
                        shape = RoundedCornerShape(24.dp),
                        color = accent,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = doneScale
                                scaleY = doneScale
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineCard(
    stepNumber: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = accentColor
                )
            }

            content()
        }
    }
}

@Composable
private fun SpecRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
        )
    }
}
