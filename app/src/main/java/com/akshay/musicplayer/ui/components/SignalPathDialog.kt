package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.akshay.musicplayer.domain.models.ActiveAudioFormat

/**
 * Technical Signal Path Dialog opened when tapping the quality capsule.
 * Displays the audio decode pipeline, format parameters, and output route.
 */
@Composable
fun SignalPathDialog(
    audioFormat: ActiveAudioFormat,
    isEqualizerActive: Boolean = false,
    isClarityActive: Boolean = false,
    isBitPerfectActive: Boolean = false,
    onDismiss: () -> Unit
) {
    val isHiRes = audioFormat.isHiRes
    val isLossless = audioFormat.isLossless

    val verdictText = when {
        isBitPerfectActive -> "BIT-PERFECT DIRECT"
        isHiRes -> "HI-RES STUDIO MASTER"
        isLossless -> "LOSSLESS CD QUALITY"
        else -> "STANDARD ENCODING"
    }

    val verdictColor = when {
        isBitPerfectActive || isHiRes -> Color(0xFF00E676) // Bright emerald green
        isLossless -> Color(0xFF00E5FF)                   // Cyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val verdictBg = verdictColor.copy(alpha = 0.15f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Audio Signal Path",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = verdictBg
                    ) {
                        Text(
                            text = verdictText,
                            color = verdictColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = if (isLossless) "Bitstream decoded directly from Lossless CDN without lossy compression."
                    else "Streamed via standard dynamic adaptive streaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(18.dp))

                // Section 1: Audio Source
                SignalSectionHeader(title = "SOURCE FORMAT", icon = Icons.Default.GraphicEq)
                Spacer(Modifier.height(8.dp))
                SignalSpecRow(label = "Codec", value = audioFormat.codec)
                SignalSpecRow(label = "Sample Rate", value = "${audioFormat.sampleRateHz} Hz (${audioFormat.sampleRateHz / 1000f} kHz)")
                SignalSpecRow(label = "Bit Depth", value = "${audioFormat.bitDepth}-bit")
                SignalSpecRow(label = "Bitrate", value = "${audioFormat.bitrateKbps} kbps")
                SignalSpecRow(label = "Provider", value = audioFormat.sourceName)

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))

                // Section 2: DSP & Effects
                SignalSectionHeader(title = "PROCESSING & DSP", icon = Icons.Default.Info)
                Spacer(Modifier.height(8.dp))
                SignalSpecRow(
                    label = "Equalizer",
                    value = if (isBitPerfectActive) "Bypassed (Bit-Perfect)" else if (isEqualizerActive) "Active" else "Flat / Off",
                    valueColor = if (isEqualizerActive && !isBitPerfectActive) Color(0xFF00E5FF) else null
                )
                SignalSpecRow(
                    label = "Studio Master Clarity",
                    value = if (isBitPerfectActive) "Bypassed" else if (isClarityActive) "Active (+2.5dB Harmonic High-Shelf)" else "Off",
                    valueColor = if (isClarityActive && !isBitPerfectActive) Color(0xFF00E676) else null
                )
                SignalSpecRow(
                    label = "Bit-Perfect Mode",
                    value = if (isBitPerfectActive) "Enabled (Pure Bitstream)" else "Disabled (System Mixer)",
                    valueColor = if (isBitPerfectActive) Color(0xFF00E676) else null
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))

                // Section 3: Output Route
                SignalSectionHeader(title = "OUTPUT ROUTE", icon = Icons.Default.Headphones)
                Spacer(Modifier.height(8.dp))
                SignalSpecRow(label = "Active Device", value = audioFormat.audioOutputDevice)
                SignalSpecRow(label = "Channels", value = "${audioFormat.channelCount}.0 Stereo")

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SignalSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SignalSpecRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
