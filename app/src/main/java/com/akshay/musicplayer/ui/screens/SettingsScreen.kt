@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.theme.ThemePresets
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val AccentOrange: Color
    @Composable
    get() = LocalAccentColor.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val usePureBlack by viewModel.usePureBlack.collectAsState()
    val accentColorId by viewModel.accentColorId.collectAsState()
    val fontScaleOption by viewModel.fontScaleOption.collectAsState()
    val lyricsFontSizeOption by viewModel.lyricsFontSizeOption.collectAsState()

    val googleAccount by viewModel.googleAccount.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val hasUnbackedUpChanges by viewModel.hasUnbackedUpChanges.collectAsState()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsState()
    val lastBackupSizeBytes by viewModel.lastBackupSizeBytes.collectAsState()
    val isBackupInProgress by viewModel.isBackupInProgress.collectAsState()
    val isRestoreInProgress by viewModel.isRestoreInProgress.collectAsState()

    val showOnLockscreen by viewModel.showOnLockscreen.collectAsState()
    val highRefreshRate by viewModel.highRefreshRate.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val downloadFolder by viewModel.downloadFolder.collectAsState()
    val embedLyricsInDownload by viewModel.embedLyricsInDownload.collectAsState()
    val enableLyrics by viewModel.enableLyrics.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()

    val playButtonPosition by viewModel.playButtonPosition.collectAsState()
    var showPreBuildOption by remember { mutableStateOf(false) }
    val enableSponsorBlock by viewModel.enableSponsorBlock.collectAsState()
    val skipSponsor by viewModel.skipSponsor.collectAsState()
    val skipSelfPromo by viewModel.skipSelfPromo.collectAsState()
    val skipInteraction by viewModel.skipInteraction.collectAsState()
    val skipIntroOutro by viewModel.skipIntroOutro.collectAsState()
    val skipNonMusicOffTopic by viewModel.skipNonMusicOffTopic.collectAsState()

    // Audiophile & Appearance States
    val losslessStreamingEnabled by viewModel.losslessStreamingEnabled.collectAsState()
    val losslessServerUrl by viewModel.losslessServerUrl.collectAsState()
    val isStudioMasterClarityEnabled by viewModel.isStudioMasterClarityEnabled.collectAsState()
    val isBitPerfectEnabled by viewModel.isBitPerfectEnabled.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val playerLayoutStyle by viewModel.playerLayoutStyle.collectAsState()

    var showSpotifyImport by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showLosslessServerDialog by remember { mutableStateOf(false) }

    val accentColor = LocalAccentColor.current
    val isPureBlack = LocalIsPureBlack.current

    val bgColor = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF0C0C0E) else Color(0xFFF2F2F7)
    val cardBg = if (isPureBlack) Color(0xFF0D0D0D) else if (isDarkMode) Color(0xFF18181A) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)
    val dividerColor = if (isPureBlack) Color.White.copy(alpha = 0.05f) else if (isDarkMode) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
    val cardCorner = 16.dp

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val email = account?.email

            if (!email.isNullOrBlank()) {
                android.widget.Toast.makeText(context, "Verifying Google account $email...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.connectAndBackupGoogleAccount(context, email) { success, msg ->
                    if (success) {
                        viewModel.setGoogleAccount(account)
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Backup failed: $msg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (_: com.google.android.gms.common.api.ApiException) {
            android.widget.Toast.makeText(context, "Sign-in cancelled", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initGoogleDriveAccount(context)
    }

    val listState = rememberLazyListState()

    val appVersionName = remember(context) {
        try {
            val name = com.akshay.musicplayer.BuildConfig.VERSION_NAME
            if (name.startsWith("v", ignoreCase = true)) name else "v$name"
        } catch (_: Exception) {
            "v1.1.0"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetUpdateState()
        }
    }

    // Handle sub-screen back navigation
    androidx.activity.compose.BackHandler(enabled = showSpotifyImport) {
        showSpotifyImport = false
    }
    androidx.activity.compose.BackHandler(enabled = showAboutPage) {
        showAboutPage = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Settings",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
                )
            },
            containerColor = bgColor
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {

                // ═══════════════════════════════════════════════════════════
                // 1. APPEARANCE & THEME
                // ═══════════════════════════════════════════════════════════
                item {
                    SettingsSectionHeader("Appearance & Interface", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        ThemeModeSelectorItem(
                            currentMode = themeMode,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onModeSelect = { viewModel.setThemeMode(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Pure Black (AMOLED)",
                            subtitle = "Pitch black background for battery saving on OLED screens",
                            icon = Icons.Default.Contrast,
                            checked = usePureBlack,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setUsePureBlack(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        AccentColorPickerItem(
                            currentAccentId = accentColorId,
                            isDarkMode = isDarkMode,
                            onAccentSelect = { viewModel.setAccentColorId(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsSelectorItem(
                            title = "Player Layout Style",
                            subtitle = "Switch between Reels swiper and Classic album player",
                            icon = Icons.Default.ViewCarousel,
                            currentValue = if (playerLayoutStyle == "classic") "Classic Player" else "Reels Swiper",
                            options = listOf("Reels Swiper", "Classic Player"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = {
                                val target = if (it.startsWith("Classic")) "classic" else "reels"
                                if (target != playerLayoutStyle) {
                                    viewModel.setPlayerLayoutStyle(target)
                                }
                            }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsSelectorItem(
                            title = "Interface Text Scaling",
                            subtitle = "Adjust application text size",
                            icon = Icons.Default.FormatSize,
                            currentValue = when (fontScaleOption) {
                                "small" -> "Small (0.9x)"
                                "large" -> "Large (1.1x)"
                                "extra_large" -> "Extra Large (1.2x)"
                                else -> "Normal (1.0x)"
                            },
                            options = listOf("Small (0.9x)", "Normal (1.0x)", "Large (1.1x)", "Extra Large (1.2x)"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = {
                                val key = when {
                                    it.startsWith("Small") -> "small"
                                    it.startsWith("Large") -> "large"
                                    it.startsWith("Extra") -> "extra_large"
                                    else -> "normal"
                                }
                                viewModel.setFontScaleOption(key)
                            }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsSelectorItem(
                            title = "Lyrics Font Size",
                            subtitle = "Size of synchronized karaoke lyrics",
                            icon = Icons.Default.TextFields,
                            currentValue = when (lyricsFontSizeOption) {
                                "compact" -> "Compact"
                                "large" -> "Large"
                                "extra_large" -> "Extra Large"
                                else -> "Normal"
                            },
                            options = listOf("Compact", "Normal", "Large", "Extra Large"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = {
                                val key = when {
                                    it.startsWith("Compact") -> "compact"
                                    it.startsWith("Large") -> "large"
                                    it.startsWith("Extra") -> "extra_large"
                                    else -> "normal"
                                }
                                viewModel.setLyricsFontSizeOption(key)
                            }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 2. AUDIO & PLAYBACK ENGINE
                // ═══════════════════════════════════════════════════════════
                item {
                    SettingsSectionHeader("Audio & Playback", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        SettingsSelectorItem(
                            title = "Streaming Audio Quality",
                            subtitle = "Online playback bit rate",
                            icon = Icons.Default.GraphicEq,
                            currentValue = audioQuality,
                            options = listOf("High (320 kbps)", "Medium (160 kbps)", "Low (96 kbps)"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = { viewModel.setAudioQuality(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsClickableItem(
                            title = "Equalizer & Audio DSP",
                            subtitle = "Hardware multi-band EQ, bass boost & acoustic presets",
                            icon = Icons.Default.Tune,
                            isDarkMode = isDarkMode,
                            onClick = { showEqualizerSheet = true }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Hi-Res Lossless Streaming (FLAC)",
                            subtitle = if (losslessStreamingEnabled) "Matches tracks against Qobuz lossless catalog • Auto-fallback to YouTube if track or server unavailable" else "Stream 24-bit/96kHz audio with auto-fallback to YouTube",
                            icon = Icons.Default.HighQuality,
                            checked = losslessStreamingEnabled,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setLosslessStreamingEnabled(it) }
                        )
                        if (losslessStreamingEnabled) {
                            HorizontalDivider(color = dividerColor)
                            SettingsClickableItem(
                                title = "Lossless Backend Server",
                                subtitle = losslessServerUrl.ifBlank { com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL },
                                icon = Icons.Default.Storage,
                                isDarkMode = isDarkMode,
                                onClick = { showLosslessServerDialog = true }
                            )
                        }
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Studio Master Clarity",
                            subtitle = "Acoustic high-shelf excitation (+2.5dB air curve) for vocals",
                            icon = Icons.Default.Speed,
                            checked = isStudioMasterClarityEnabled,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setStudioMasterClarityEnabled(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Bit-Perfect Mode",
                            subtitle = "Bypass all DSP for pure PCM bitstream to external USB DACs",
                            icon = Icons.Default.Headphones,
                            checked = isBitPerfectEnabled,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setBitPerfectEnabled(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Audio Crossfade",
                            subtitle = if (crossfadeEnabled) "${crossfadeSeconds}s smooth transition between tracks" else "Gapless track playback",
                            icon = Icons.Default.Shuffle,
                            checked = crossfadeEnabled,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                        )
                        AnimatedVisibility(visible = crossfadeEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Transition Duration", color = textSub, fontSize = 12.sp)
                                    Text("${crossfadeSeconds}s", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = crossfadeSeconds.toFloat(),
                                    onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                                    valueRange = 1f..12f,
                                    steps = 10,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
                                    )
                                )
                            }
                        }
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Synchronized Karaoke Lyrics",
                            subtitle = "Display real-time synchronized lyrics in player",
                            icon = Icons.Default.Lyrics,
                            checked = enableLyrics,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setEnableLyrics(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Show Over Lockscreen",
                            subtitle = "Keep full player view available when phone is locked",
                            icon = Icons.Default.Lock,
                            checked = showOnLockscreen,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setShowOnLockscreen(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "High Refresh Rate (120Hz)",
                            subtitle = "Peak display refresh rate for ultra-smooth UI animations",
                            icon = Icons.Default.Bolt,
                            checked = highRefreshRate,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setHighRefreshRate(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsSelectorItem(
                            title = "Play Button Position",
                            subtitle = "Placement of playback control button",
                            icon = Icons.Default.PlayCircle,
                            currentValue = playButtonPosition,
                            options = listOf("Left", "Right", "Center"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = { viewModel.setPlayButtonPosition(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsBatteryItem(isDarkMode = isDarkMode)
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 3. SMART SKIP (SPONSORBLOCK)
                // ═══════════════════════════════════════════════════════════
                item {
                    SettingsSectionHeader("Smart Skip (SponsorBlock)", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        SettingsToggleItem(
                            title = "Enable SponsorBlock",
                            subtitle = "Skip sponsored segments, self-promos, and non-music filler",
                            icon = Icons.Default.Shield,
                            checked = enableSponsorBlock,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setEnableSponsorBlock(it) }
                        )

                        AnimatedVisibility(visible = enableSponsorBlock) {
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                HorizontalDivider(color = dividerColor)
                                SettingsToggleItem(title = "Skip Sponsor Segments", subtitle = "Paid brand sponsorships", icon = Icons.Default.Shield, checked = skipSponsor, isDarkMode = isDarkMode, accentColor = accentColor, onCheckedChange = { viewModel.setSkipSponsor(it) })
                                HorizontalDivider(color = dividerColor)
                                SettingsToggleItem(title = "Skip Self-Promotion", subtitle = "Channel promos & merch", icon = Icons.Default.Campaign, checked = skipSelfPromo, isDarkMode = isDarkMode, accentColor = accentColor, onCheckedChange = { viewModel.setSkipSelfPromo(it) })
                                HorizontalDivider(color = dividerColor)
                                SettingsToggleItem(title = "Skip Interaction Prompts", subtitle = "Subscribe & like reminders", icon = Icons.Default.ThumbUp, checked = skipInteraction, isDarkMode = isDarkMode, accentColor = accentColor, onCheckedChange = { viewModel.setSkipInteraction(it) })
                                HorizontalDivider(color = dividerColor)
                                SettingsToggleItem(title = "Skip Intros & Outros", subtitle = "Non-music intro/outro video clips", icon = Icons.Default.MusicNote, checked = skipIntroOutro, isDarkMode = isDarkMode, accentColor = accentColor, onCheckedChange = { viewModel.setSkipIntroOutro(it) })
                                HorizontalDivider(color = dividerColor)
                                SettingsToggleItem(title = "Skip Non-Music Filler", subtitle = "Interludes & off-topic dialogue", icon = Icons.Default.ChatBubble, checked = skipNonMusicOffTopic, isDarkMode = isDarkMode, accentColor = accentColor, onCheckedChange = { viewModel.setSkipNonMusicOffTopic(it) })
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 4. DOWNLOADS & OFFLINE STORAGE
                // ═══════════════════════════════════════════════════════════
                item {
                    SettingsSectionHeader("Downloads & Storage", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        SettingsSelectorItem(
                            title = "Download Audio Quality",
                            subtitle = "Audio bit rate for offline tracks",
                            icon = Icons.Default.MusicNote,
                            currentValue = downloadQuality,
                            options = listOf("Lossless (FLAC)", "Highest (320 kbps)", "Standard (256 kbps)", "Medium (128 kbps)"),
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onSelect = { viewModel.setDownloadQuality(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsFolderSelectorItem(
                            title = "Download Location",
                            subtitle = "Folder where downloaded tracks are saved",
                            icon = Icons.Default.Folder,
                            currentFolder = downloadFolder,
                            isDarkMode = isDarkMode,
                            onFolderSelect = { viewModel.setDownloadFolder(it) }
                        )
                        HorizontalDivider(color = dividerColor)
                        SettingsToggleItem(
                            title = "Embed Lyrics in Downloads",
                            subtitle = "Store synced lyrics in ID3 tags & save .lrc files",
                            icon = Icons.Default.Lyrics,
                            checked = embedLyricsInDownload,
                            isDarkMode = isDarkMode,
                            accentColor = accentColor,
                            onCheckedChange = { viewModel.setEmbedLyricsInDownload(it) }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 5. CLOUD BACKUP & PLAYLISTS
                // ═══════════════════════════════════════════════════════════
                item {
                    SettingsSectionHeader("Cloud Backup & Playlists", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        // Google Drive Account Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        text = googleAccount?.displayName ?: googleAccountEmail ?: googleAccount?.email ?: "Google Drive",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) "Cloud backup connected" else "Sign in to backup playlists",
                                        color = textSub,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) {
                                TextButton(onClick = { viewModel.signOutGoogle(context) }) {
                                    Text("Sign Out", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val repo = GoogleDriveBackupRepository(context)
                                        googleSignInLauncher.launch(repo.getSignInIntent(context))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Sign In", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Google Drive Connected Options
                        if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) {
                            HorizontalDivider(color = dividerColor)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val lastTimeStr = if (lastBackupTimestamp > 0) {
                                    SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))
                                } else "Never"
                                val sizeStr = if (lastBackupSizeBytes > 0) {
                                    val kb = lastBackupSizeBytes / 1024.0
                                    if (kb < 1024) String.format(Locale.getDefault(), "%.1f KB", kb) else String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
                                } else "0 KB"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (hasUnbackedUpChanges) accentColor else Color(0xFF4CAF50))
                                        )
                                        Text(
                                            text = if (hasUnbackedUpChanges) "Pending Changes" else "Synced",
                                            color = if (hasUnbackedUpChanges) accentColor else Color(0xFF4CAF50),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text("Last sync: $lastTimeStr • $sizeStr", color = textSub, fontSize = 11.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.performDriveBackup(context) { _, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isBackupInProgress && !isRestoreInProgress,
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        if (isBackupInProgress) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Backup Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.performDriveRestore(context) { _, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isBackupInProgress && !isRestoreInProgress,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        if (isRestoreInProgress) {
                                            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Restore", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = dividerColor)

                        // Local Playlists (JSON)
                        val jsonPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                coroutineScope.launch {
                                    try {
                                        val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                                        if (!content.isNullOrBlank()) {
                                            val success = viewModel.importPlaylistsFromJson(context, content)
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Playlists imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Invalid JSON playlist format", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "Failed to read file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Local JSON Playlists", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Export or restore playlists locally", color = textSub, fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val path = viewModel.exportPlaylistsToJson(context)
                                            if (path != null) {
                                                android.widget.Toast.makeText(context, "Exported JSON to Downloads!", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Export", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { jsonPickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Import", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        HorizontalDivider(color = dividerColor)

                        // Spotify Import Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSpotifyImport = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Import from Spotify", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Import public Spotify playlist via link", color = textSub, fontSize = 11.sp)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSub, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 6. ABOUT & APP UPDATES
                // ═══════════════════════════════════════════════════════════
                item {
                    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
                    val updateInfo by viewModel.updateInfo.collectAsState()
                    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
                    val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()

                    SettingsSectionHeader("About & Updates", accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .background(cardBg)
                            .padding(vertical = 4.dp)
                    ) {
                        // Check for Updates Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Check for Updates", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = updateStatusMessage ?: "Version $appVersionName",
                                        color = textSub,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.checkForUpdates(context, showToast = true) },
                                enabled = !isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Check", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Update Available Banner
                        if (updateInfo?.isNewVersionAvailable == true) {
                            HorizontalDivider(color = dividerColor)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                    Text("Update Available: ${updateInfo!!.tagName}", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                if (!updateInfo!!.releaseNotes.isNullOrBlank()) {
                                    Text(
                                        updateInfo!!.releaseNotes!!,
                                        color = textSub,
                                        fontSize = 11.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (updateDownloadProgress != null) {
                                    val prog = updateDownloadProgress!!
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Downloading...", color = textSub, fontSize = 11.sp)
                                            Text("${(prog * 100).toInt()}%", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        LinearProgressIndicator(
                                            progress = { prog },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = accentColor
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download & Install", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = dividerColor)

                        // About Mueso Row
                        SettingsClickableItem(
                            title = "About Mueso",
                            subtitle = "Features, credits, architecture & specs",
                            icon = Icons.Default.Info,
                            isDarkMode = isDarkMode,
                            onClick = { showAboutPage = true }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 7. DEVELOPER & MAINTENANCE
                // ═══════════════════════════════════════════════════════════
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(visible = showPreBuildOption) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(cardCorner))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Developer Pre-Builds", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Install testing release from GitHub tag 'Pre_Builds'", color = textSub, fontSize = 11.sp)
                                Button(
                                    onClick = { viewModel.installPreBuildRelease(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Pre-Build", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(cardCorner))
                                .background(cardBg)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { viewModel.forceRefreshAll(context) },
                                        onLongPress = {
                                            showPreBuildOption = !showPreBuildOption
                                            val status = if (showPreBuildOption) "Pre-Build Developer Mode Enabled" else "Pre-Build Developer Mode Disabled"
                                            android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescan Songs & Refresh Library", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Text(
                            text = "Mueso Player $appVersionName • Open Source MIT",
                            color = textSub.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSpotifyImport,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250))
        ) {
            SpotifyImportScreen(
                viewModel = viewModel,
                onBackClick = { showSpotifyImport = false }
            )
        }

        AnimatedVisibility(
            visible = showAboutPage,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250))
        ) {
            AboutScreen(
                isDarkMode = isDarkMode,
                onBackClick = { showAboutPage = false }
            )
        }

        if (showEqualizerSheet) {
            com.akshay.musicplayer.ui.components.EqualizerBottomSheet(
                effectsController = viewModel.audioEffectsController,
                isDarkMode = isDarkMode,
                onDismiss = { showEqualizerSheet = false }
            )
        }

        if (showLosslessServerDialog) {
            var tempUrl by remember { mutableStateOf(losslessServerUrl.ifBlank { com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL }) }
            AlertDialog(
                onDismissRequest = { showLosslessServerDialog = false },
                title = { Text("Lossless Server URL", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter the base URL of your Qobuz or ClashFLAC resolution backend:",
                            color = textSub,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { tempUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                cursorColor = accentColor
                            )
                        )
                        Text(
                            "Note: Regional tracks or songs not present in Qobuz automatically fallback to YouTube Music audio stream.",
                            color = textSub.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setLosslessServerUrl(tempUrl.trim())
                        showLosslessServerDialog = false
                    }) {
                        Text("Save", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            tempUrl = com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL
                            viewModel.setLosslessServerUrl(tempUrl)
                            showLosslessServerDialog = false
                        }) {
                            Text("Reset", color = textSub)
                        }
                        TextButton(onClick = { showLosslessServerDialog = false }) {
                            Text("Cancel", color = textSub)
                        }
                    }
                },
                containerColor = cardBg
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPACT REUSABLE SETTINGS COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionHeader(
    title: String,
    color: Color = AccentOrange
) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF555555),
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(text = subtitle, color = textSub, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textSub,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    isDarkMode: Boolean,
    accentColor: Color = AccentOrange,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)

    val iconTint by animateColorAsState(
        targetValue = if (checked) accentColor else (if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color(0xFF8E8E93)),
        animationSpec = tween(200),
        label = "iconTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = textSub, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            ),
            modifier = Modifier.graphicsLayer(scaleX = 0.88f, scaleY = 0.88f)
        )
    }
}

@Composable
private fun SettingsSelectorItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    currentValue: String,
    options: List<String>,
    isDarkMode: Boolean,
    accentColor: Color = AccentOrange,
    onSelect: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val isPureBlack = LocalIsPureBlack.current
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF555555),
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = textSub, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentValue,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = textSub,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.equals(currentValue, ignoreCase = true) ||
                            (option.contains("Classic", ignoreCase = true) && currentValue.contains("Classic", ignoreCase = true)) ||
                            (option.contains("Reels", ignoreCase = true) && currentValue.contains("Reels", ignoreCase = true)) ||
                            (option.contains("FLAC", ignoreCase = true) && currentValue.contains("FLAC", ignoreCase = true)) ||
                            (option.contains("320") && currentValue.contains("320")) ||
                            (option.contains("256") && currentValue.contains("256")) ||
                            (option.contains("160") && currentValue.contains("160")) ||
                            (option.contains("128") && currentValue.contains("128")) ||
                            (option.contains("96") && currentValue.contains("96"))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onSelect(option)
                                    showDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) accentColor else textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = textSub, fontSize = 13.sp)
                }
            },
            containerColor = if (isPureBlack) Color(0xFF121212) else if (isDarkMode) Color(0xFF1E1E22) else Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ThemeModeSelectorItem(
    currentMode: String,
    isDarkMode: Boolean,
    accentColor: Color,
    onModeSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
    val containerShape = RoundedCornerShape(10.dp)
    val pillShape = RoundedCornerShape(8.dp)

    val modes = listOf(
        Triple("system", "System", Icons.Default.BrightnessAuto),
        Triple("dark", "Dark", Icons.Default.DarkMode),
        Triple("light", "Light", Icons.Default.LightMode)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Column {
                Text("App Theme", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Select theme color mode", color = textSub, fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(containerShape)
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { (id, label, icon) ->
                val isSelected = currentMode.equals(id, ignoreCase = true)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "themeModeBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(200),
                    label = "themeModeText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .background(bgColor)
                        .clickable { onModeSelect(id) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentColorPickerItem(
    currentAccentId: String,
    isDarkMode: Boolean,
    onAccentSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)
    val activeAccent = LocalAccentColor.current
    val isCustomSelected = currentAccentId.startsWith("custom_")
    var showCustomDialog by remember { mutableStateOf(false) }

    if (showCustomDialog) {
        CustomColorPickerDialog(
            initialColorHex = if (isCustomSelected) currentAccentId.removePrefix("custom_") else "FF512F",
            isDarkMode = isDarkMode,
            onDismiss = { showCustomDialog = false },
            onColorSelected = { newId ->
                onAccentSelect(newId)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = activeAccent, modifier = Modifier.size(20.dp))
                Column {
                    Text("Accent Color Palette", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Highlight color across the player", color = textSub, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val activeName = ThemePresets.getPalette(currentAccentId).name
            Text(activeName, color = activeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isCustomSelected) {
                item {
                    val customPalette = ThemePresets.getPalette(currentAccentId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCustomDialog = true }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(customPalette.primary, customPalette.secondary)))
                                .border(2.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Custom",
                            color = activeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(ThemePresets.AllPalettes) { palette ->
                val isSelected = currentAccentId.equals(palette.id, ignoreCase = true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAccentSelect(palette.id) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary)))
                            .then(
                                if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = palette.name.split(" ").first(),
                        color = if (isSelected) activeAccent else textSub,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Custom Color Option Button
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCustomDialog = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, textSub.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Custom Color",
                            tint = activeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Custom",
                        color = textSub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColorHex: String,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val isPureBlack = LocalIsPureBlack.current
    val dialogBg = if (isDarkMode) (if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1E1E2E)) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)

    val cleanInitial = initialColorHex.removePrefix("custom_").removePrefix("#").take(6)
    val initialInt = try {
        cleanInitial.toLong(16).toInt()
    } catch (_: Exception) {
        0xFF512F
    }

    var red by remember { mutableFloatStateOf(android.graphics.Color.red(initialInt).toFloat()) }
    var green by remember { mutableFloatStateOf(android.graphics.Color.green(initialInt).toFloat()) }
    var blue by remember { mutableFloatStateOf(android.graphics.Color.blue(initialInt).toFloat()) }

    val currentColorInt = android.graphics.Color.rgb(red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
    val currentColor = Color(currentColorInt)
    val currentHex = String.format(Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))

    var hexInputText by remember { mutableStateOf(currentHex) }

    val quickColors = listOf(
        Color(0xFFFF1744), Color(0xFFFF4081), Color(0xFFE040FB), Color(0xFF7C4DFF),
        Color(0xFF536DFE), Color(0xFF448AFF), Color(0xFF00E5FF), Color(0xFF1DE9B6),
        Color(0xFF00E676), Color(0xFF76FF03), Color(0xFFFFEA00), Color(0xFFFF9100),
        Color(0xFFFF3D00), Color(0xFF8D6E63), Color(0xFFB0BEC5), Color(0xFFFF6D00)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = currentColor)
                Text("Custom Accent Color", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(currentColor),
                    contentAlignment = Alignment.Center
                ) {
                    val luminance = (red * 0.299 + green * 0.587 + blue * 0.114)
                    Text(
                        text = "#$currentHex",
                        color = if (luminance > 160) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }

                Text("Quick Swatches", color = textSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickColors) { c ->
                        val isSelected = currentColorInt == c.toArgb()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable {
                                    red = (c.red * 255f)
                                    green = (c.green * 255f)
                                    blue = (c.blue * 255f)
                                    hexInputText = String.format(Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                                }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
                                )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Red: ${red.toInt()}", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Green: ${green.toInt()}", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Blue: ${blue.toInt()}", color = Color(0xFF448AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = red,
                        onValueChange = {
                            red = it
                            hexInputText = String.format(Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5252), activeTrackColor = Color(0xFFFF5252))
                    )
                    Slider(
                        value = green,
                        onValueChange = {
                            green = it
                            hexInputText = String.format(Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF69F0AE), activeTrackColor = Color(0xFF69F0AE))
                    )
                    Slider(
                        value = blue,
                        onValueChange = {
                            blue = it
                            hexInputText = String.format(Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF448AFF), activeTrackColor = Color(0xFF448AFF))
                    )
                }

                OutlinedTextField(
                    value = hexInputText,
                    onValueChange = { txt ->
                        val clean = txt.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexInputText = clean
                        if (clean.length == 6) {
                            try {
                                val cInt = clean.toLong(16).toInt()
                                red = android.graphics.Color.red(cInt).toFloat()
                                green = android.graphics.Color.green(cInt).toFloat()
                                blue = android.graphics.Color.blue(cInt).toFloat()
                            } catch (_: Exception) {}
                        }
                    },
                    prefix = { Text("#", color = currentColor, fontWeight = FontWeight.Bold) },
                    label = { Text("Hex Color Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = currentColor,
                        unfocusedBorderColor = textSub.copy(alpha = 0.3f),
                        cursorColor = currentColor,
                        focusedLabelColor = currentColor,
                        unfocusedLabelColor = textSub,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val luminance = (red * 0.299 + green * 0.587 + blue * 0.114)
            Button(
                onClick = {
                    onColorSelected("custom_$currentHex")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply Color", color = if (luminance > 160) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSub)
            }
        }
    )
}

@Composable
private fun SettingsFolderSelectorItem(
    title: String = "Download Location",
    subtitle: String = "Folder where downloaded audio files save",
    icon: ImageVector = Icons.Default.Folder,
    currentFolder: String,
    isDarkMode: Boolean,
    onFolderSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val isPureBlack = LocalIsPureBlack.current
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            onFolderSelect(uri.toString())
            showDialog = false
        }
    }

    val displayFolder = when {
        currentFolder == "Internal App Storage" -> "Internal Storage"
        currentFolder.startsWith("content://") -> {
            try {
                val uri = Uri.parse(currentFolder)
                val docId = DocumentsContract.getTreeDocumentId(uri)
                if (docId.startsWith("primary:")) docId.removePrefix("primary:") else docId
            } catch (_: Exception) {
                currentFolder
            }
        }
        else -> currentFolder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = textSub, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = displayFolder,
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSub, modifier = Modifier.size(16.dp))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (isPureBlack) Color(0xFF121212) else if (isDarkMode) Color(0xFF1E1E22) else Color.White,
            title = { Text("Choose Download Location", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isPublicDefault = currentFolder == "Music/Mueso" || currentFolder == "Music"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPublicDefault) AccentOrange.copy(alpha = 0.12f) else if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                            .clickable {
                                onFolderSelect("Music/Mueso")
                                showDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = if (isPublicDefault) AccentOrange else textSub)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Music/Mueso (Recommended)", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Public music directory. Visible to all media players & file managers.", color = textSub, fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                            .clickable { folderPickerLauncher.launch(null) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = AccentOrange)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Choose Custom Directory...", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Pick any folder on internal storage or SD card via system file picker.", color = textSub, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = textSub)
                }
            }
        )
    }
}

@Composable
private fun SettingsBatteryItem(
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    var isIgnoring by remember {
        mutableStateOf(com.akshay.musicplayer.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                com.akshay.musicplayer.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = if (isIgnoring) Color(0xFF4CAF50) else AccentOrange,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Background Playback", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (isIgnoring) "Unrestricted • Playback stays active when screen is off" else "Optimized • Tap to allow unrestricted playback",
                    color = textSub,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isIgnoring) Color(0xFF4CAF50).copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isIgnoring) "UNRESTRICTED" else "OPTIMIZED",
                color = if (isIgnoring) Color(0xFF4CAF50) else AccentOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
