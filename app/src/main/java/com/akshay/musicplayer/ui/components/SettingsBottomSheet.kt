package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    skipSponsor: Boolean,
    skipSelfPromo: Boolean,
    skipInteraction: Boolean,
    skipIntroOutro: Boolean,
    skipNonMusicOffTopic: Boolean,
    audioQuality: String,
    thumbnailQuality: String,
    downloadQuality: String,
    downloadFolder: String,
    enableLyrics: Boolean,
    isDarkMode: Boolean,
    showOnLockscreen: Boolean,
    highRefreshRate: Boolean,
    onToggleSponsor: (Boolean) -> Unit,
    onToggleSelfPromo: (Boolean) -> Unit,
    onToggleInteraction: (Boolean) -> Unit,
    onToggleIntroOutro: (Boolean) -> Unit,
    onToggleNonMusicOffTopic: (Boolean) -> Unit,
    onAudioQualityChange: (String) -> Unit,
    onThumbnailQualityChange: (String) -> Unit,
    onDownloadQualityChange: (String) -> Unit,
    onDownloadFolderChange: (String) -> Unit,
    onEnableLyricsToggle: (Boolean) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onShowOnLockscreenToggle: (Boolean) -> Unit,
    onHighRefreshRateToggle: (Boolean) -> Unit,
    onForceRefresh: (android.content.Context) -> Unit,
    onOpenFullSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current

    val sheetBg = if (isDarkMode) Color(0xFF1A1A2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    var showCustomFolderDialog by remember { mutableStateOf(false) }

    if (showCustomFolderDialog) {
        var customPathInput by remember { mutableStateOf(if (downloadFolder.startsWith("/")) downloadFolder else "/storage/emulated/0/Music/MyFolder") }
        AlertDialog(
            onDismissRequest = { showCustomFolderDialog = false },
            containerColor = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF),
            title = {
                Text("Custom Download Folder", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter absolute directory path to save downloaded songs:",
                        color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                            unfocusedTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customPathInput.isNotBlank()) {
                            onDownloadFolderChange(customPathInput.trim())
                            showCustomFolderDialog = false
                        }
                    }
                ) {
                    Text("Set Path", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFolderDialog = false }) {
                    Text("Cancel", color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Quick Settings",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onOpenFullSettings != null) {
                item {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenFullSettings()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full Settings & Cloud Backup →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Theme Mode & Display Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Brightness4,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Display & Lockscreen Behavior",
                        color = AccentOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Theme & Display Toggles Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 8.dp)
                ) {
                    SettingsToggleRow(
                        title = "Dark Mode",
                        subtitle = "Use dark glassmorphism theme across the app",
                        icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        checked = isDarkMode,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onDarkModeToggle
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Show Over Lockscreen",
                        subtitle = "Display player when phone is locked",
                        icon = Icons.Default.Lock,
                        checked = showOnLockscreen,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onShowOnLockscreenToggle
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "High Refresh Rate (120Hz/Peak)",
                        subtitle = "Force peak display refresh rate for ultra-smooth UI",
                        icon = Icons.Default.Bolt,
                        checked = highRefreshRate,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onHighRefreshRateToggle
                    )
                }
            }

            // Audio & Playback Quality Section
            item {
                Text(
                    text = "Audio & Visual Quality",
                    color = AccentOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 8.dp)
                ) {
                    QualitySelectorRow(
                        title = "Streaming Audio Quality",
                        subtitle = "Online playback bit rate",
                        icon = Icons.Default.GraphicEq,
                        currentValue = audioQuality,
                        options = listOf("High (320 kbps)", "Medium (160 kbps)", "Low (96 kbps)"),
                        isDarkMode = isDarkMode,
                        onSelect = onAudioQualityChange
                    )
                    HorizontalDivider(color = dividerColor)

                    QualitySelectorRow(
                        title = "Thumbnail Quality",
                        subtitle = "Cover art resolution",
                        icon = Icons.Default.Image,
                        currentValue = thumbnailQuality,
                        options = listOf("Highest (1080p Maxres)", "High (720p)", "Medium (480p)", "Low (Fast)"),
                        isDarkMode = isDarkMode,
                        onSelect = onThumbnailQualityChange
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Show Synchronized Lyrics",
                        subtitle = "Display live karaoke lyrics in player",
                        icon = Icons.Default.Lyrics,
                        checked = enableLyrics,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onEnableLyricsToggle
                    )
                }
            }

            // Downloads & Offline Folder Section
            item {
                Text(
                    text = "Downloads & Offline Storage",
                    color = AccentOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 8.dp)
                ) {
                    QualitySelectorRow(
                        title = "Download Bitrate Quality",
                        subtitle = "Audio quality for offline tracks",
                        icon = Icons.Default.Download,
                        currentValue = downloadQuality,
                        options = listOf("Highest (320 kbps)", "High (256 kbps)", "Medium (128 kbps)"),
                        isDarkMode = isDarkMode,
                        onSelect = onDownloadQualityChange
                    )
                    HorizontalDivider(color = dividerColor)

                    QualitySelectorRow(
                        title = "Download Target Location",
                        subtitle = if (downloadFolder.startsWith("/")) "Custom: $downloadFolder" else "Folder where downloaded MP3s are saved",
                        icon = Icons.Default.Folder,
                        currentValue = if (downloadFolder in listOf("Music/Mueso", "Downloads", "Internal App Storage")) downloadFolder else "Custom Folder",
                        options = listOf("Music/Mueso", "Downloads", "Internal App Storage", "Custom Folder"),
                        isDarkMode = isDarkMode,
                        onSelect = { selected ->
                            if (selected == "Custom Folder") {
                                showCustomFolderDialog = true
                            } else {
                                onDownloadFolderChange(selected)
                            }
                        }
                    )
                }
            }

            // Smart Skip (SponsorBlock) Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Smart Skip (SponsorBlock)",
                        color = AccentOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Automatically skip non-music segments during playback",
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }

            // Toggles Grid
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 8.dp)
                ) {
                    SettingsToggleRow(
                        title = "Sponsors & Paid Ads",
                        subtitle = "Skip sponsored brand messages",
                        icon = Icons.Default.Campaign,
                        checked = skipSponsor,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onToggleSponsor
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Self-Promotion & Plugs",
                        subtitle = "Skip channel promo & merch mentions",
                        icon = Icons.Default.Campaign,
                        checked = skipSelfPromo,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onToggleSelfPromo
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Interaction Reminders",
                        subtitle = "Skip 'Like & Subscribe' requests",
                        icon = Icons.Default.ThumbUp,
                        checked = skipInteraction,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onToggleInteraction
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Intros & Outros",
                        subtitle = "Skip intro animations & end credits",
                        icon = Icons.Default.MusicNote,
                        checked = skipIntroOutro,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onToggleIntroOutro
                    )
                    HorizontalDivider(color = dividerColor)

                    SettingsToggleRow(
                        title = "Non-Music Dialogue & Filler",
                        subtitle = "Skip spoken interludes and off-topic dialogue",
                        icon = Icons.Default.ChatBubble,
                        checked = skipNonMusicOffTopic,
                        isDarkMode = isDarkMode,
                        onCheckedChange = onToggleNonMusicOffTopic
                    )
                }
            }

            // Force Refresh Button
            item {
                Button(
                    onClick = { onForceRefresh(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Force Refresh Playlists & Rescan Songs",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // About & License Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Mueso Player v0.2.5 • Open Source",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySelectorRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentValue: String,
    options: List<String>,
    isDarkMode: Boolean,
    onSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val unselectedBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val unselectedText = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3C)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { opt ->
                val isSelected = opt == currentValue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentOrange else unselectedBg)
                        .clickable { onSelect(opt) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt.substringBefore(" "),
                        color = if (isSelected) Color.White else unselectedText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    isDarkMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) AccentOrange else (if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)),
            modifier = Modifier.size(22.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentOrange,
                uncheckedThumbColor = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                uncheckedTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
            )
        )
    }
}
