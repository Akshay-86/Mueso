package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.ActiveAudioFormat

/**
 * Audiophile quality capsule pill displayed in player screens and miniplayers.
 * Tapping opens the detailed SignalPathDialog.
 */
@Composable
fun AudioQualityCapsule(
    audioFormat: ActiveAudioFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHiRes = audioFormat.isHiRes
    val isLossless = audioFormat.isLossless

    val badgeColor = when {
        isHiRes -> Color(0xFFFFB300)      // Amber Gold for Hi-Res 24-bit
        isLossless -> Color(0xFF00E5FF)   // Electric Cyan for CD Lossless FLAC
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }

    val containerBg = when {
        isHiRes -> Color(0xFF261D00).copy(alpha = 0.85f)
        isLossless -> Color(0xFF00222B).copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val borderStroke = when {
        isHiRes -> BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
        isLossless -> BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
        else -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = containerBg,
        border = borderStroke,
        tonalElevation = if (isHiRes || isLossless) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Glowing status indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )

            Text(
                text = audioFormat.pillLabel,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}
