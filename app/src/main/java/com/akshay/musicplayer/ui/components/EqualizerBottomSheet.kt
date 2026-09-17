package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.media.player.AudioEffectsController
import com.akshay.musicplayer.ui.theme.LocalAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    effectsController: AudioEffectsController,
    isDarkMode: Boolean = true,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = LocalAccentColor.current
    val sheetBg = if (isDarkMode) Color(0xFF14141E) else Color(0xFFF9F9FB)
    val cardBg = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFEEEEF2)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)

    val isEqEnabled by effectsController.isEnabled.collectAsState()
    val presetName by effectsController.presetName.collectAsState()
    val bandFreqs by effectsController.bandFrequencies.collectAsState()
    val bandLevels by effectsController.bandLevels.collectAsState()
    val minLevelDb by effectsController.minLevelDb.collectAsState()
    val maxLevelDb by effectsController.maxLevelDb.collectAsState()
    val bassBoost by effectsController.bassBoostStrength.collectAsState()
    val isClarityEnabled by effectsController.isClarityEnabled.collectAsState()
    val isBitPerfectEnabled by effectsController.isBitPerfectEnabled.collectAsState()

    val presets = listOf("Flat", "Bass Boost", "Rock", "Pop", "Vocal", "Acoustic", "EDM")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Equalizer & DSP",
                            color = textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBitPerfectEnabled) "Bit-Perfect Mode (DSP Bypassed)"
                                   else if (isEqEnabled) "DSP Active • $presetName"
                                   else "Equalizer Disabled",
                            color = if (isBitPerfectEnabled) Color(0xFFFFA726) else textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isEqEnabled && !isBitPerfectEnabled,
                        onCheckedChange = { effectsController.setEqualizerEnabled(it) },
                        enabled = !isBitPerfectEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondary
                        )
                    }
                }
            }

            // Bit-Perfect Notice Card (if enabled)
            if (isBitPerfectEnabled) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C0A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bit-Perfect DAC Mode Active",
                                color = Color(0xFFFFA726),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All EQ and DSP effects are bypassed to send pure bit-for-bit audio to your output DAC.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFA726).copy(alpha = 0.2f))
                                .clickable { effectsController.setBitPerfectEnabled(false) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Disable",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Presets Horizontal Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PRESETS",
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = presetName.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) accent
                                    else cardBg
                                )
                                .clickable(enabled = !isBitPerfectEnabled) {
                                    effectsController.setEqualizerEnabled(true)
                                    effectsController.applyPreset(preset)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = preset,
                                color = if (isSelected) Color.White else textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sliders Section
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FREQUENCY BANDS",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Horizontal display with wide, finger-friendly vertical touch faders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandFreqs.forEachIndexed { index, freqHz ->
                            val levelDb = bandLevels.getOrNull(index) ?: 0
                            EqualizerFaderBar(
                                freqHz = freqHz,
                                levelDb = levelDb,
                                minDb = minLevelDb,
                                maxDb = maxLevelDb,
                                enabled = isEqEnabled && !isBitPerfectEnabled,
                                accentColor = accent,
                                isDarkMode = isDarkMode,
                                onLevelChange = { newLevel ->
                                    effectsController.setBandLevel(index, newLevel)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Bass Boost & Studio Master Clarity
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Bass Boost
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bass Boost",
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "$bassBoost%",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = bassBoost.toFloat(),
                            onValueChange = { effectsController.setBassBoost(it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = !isBitPerfectEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = textSecondary.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // Studio Master Clarity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isClarityEnabled) accent else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Studio Master Clarity",
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Expands acoustic air sparkle & dynamic punch",
                                    color = textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isClarityEnabled && !isBitPerfectEnabled,
                            onCheckedChange = { effectsController.setClarityEnabled(it) },
                            enabled = !isBitPerfectEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerFaderBar(
    freqHz: Int,
    levelDb: Int,
    minDb: Int,
    maxDb: Int,
    enabled: Boolean,
    accentColor: Color,
    isDarkMode: Boolean,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)
    val label = if (freqHz >= 1000) {
        val k = freqHz / 1000f
        if (k == k.toInt().toFloat()) "${k.toInt()}k" else "${k}k"
    } else {
        "$freqHz"
    }

    val totalRange = (maxDb - minDb).coerceAtLeast(1)
    val fraction = ((levelDb - minDb).toFloat() / totalRange).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // dB Label Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (levelDb != 0 && enabled) accentColor.copy(alpha = 0.18f)
                    else if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (levelDb > 0) "+${levelDb}dB" else "${levelDb}dB",
                color = if (levelDb != 0 && enabled) accentColor else textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Vertical Fader Touch Area (Generous studio touch targets: 28dp capsule track, 52x36dp tactile knob)
        BoxWithConstraints(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .pointerInput(enabled, minDb, maxDb) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val thumbHeightPx = 36.dp.toPx()
                        val availableTravel = (size.height - thumbHeightPx).coerceAtLeast(1f)
                        val touchY = (offset.y - thumbHeightPx / 2f).coerceIn(0f, availableTravel)
                        val frac = 1f - (touchY / availableTravel)
                        val newLevel = (minDb + frac * totalRange).roundToInt().coerceIn(minDb, maxDb)
                        onLevelChange(newLevel)
                    }
                }
                .pointerInput(enabled, minDb, maxDb) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val thumbHeightPx = 36.dp.toPx()
                        val availableTravel = (size.height - thumbHeightPx).coerceAtLeast(1f)
                        val touchY = (change.position.y - thumbHeightPx / 2f).coerceIn(0f, availableTravel)
                        val frac = 1f - (touchY / availableTravel)
                        val newLevel = (minDb + frac * totalRange).roundToInt().coerceIn(minDb, maxDb)
                        onLevelChange(newLevel)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val faderHeight = maxHeight
            val thumbHeight = 36.dp
            val travel = faderHeight - thumbHeight

            // Thick Capsule Background Track (28dp wide!)
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(faderHeight - 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkMode) Color(0xFF1B1B26) else Color(0xFFE4E4EC))
                    .border(
                        1.dp,
                        if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        RoundedCornerShape(14.dp)
                    )
            )

            // Center 0 dB Detent line
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(textSecondary.copy(alpha = 0.45f))
            )

            // Active Track Fill from center (0 dB) to thumb position
            val centerOffset = travel / 2f
            val thumbOffset = travel * (1f - fraction)
            if (enabled && levelDb != 0) {
                val fillTop = minOf(centerOffset, thumbOffset) + thumbHeight / 2f
                val fillHeight = if (centerOffset > thumbOffset) centerOffset - thumbOffset else thumbOffset - centerOffset
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = fillTop)
                        .width(28.dp)
                        .height(fillHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.85f))
                )
            }

            // Draggable Tactile Fader Knob / Thumb (52dp wide x 36dp high)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = travel * (1f - fraction))
                    .width(52.dp)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (enabled) {
                            if (isDarkMode) Color(0xFF28283C) else Color.White
                        } else {
                            if (isDarkMode) Color(0xFF181822) else Color(0xFFE0E0E6)
                        }
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (enabled) accentColor else textSecondary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Tactile horizontal LED glow strip & ridges on the fader knob
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (enabled) accentColor else textSecondary.copy(alpha = 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (enabled) accentColor.copy(alpha = 0.6f) else textSecondary.copy(alpha = 0.25f))
                    )
                }
            }
        }

        // Frequency Label
        Text(
            text = label,
            color = textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
