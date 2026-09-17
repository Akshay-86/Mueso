@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

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
    val cornerRadiusOption by viewModel.cornerRadiusOption.collectAsState()
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
    val designSystem by viewModel.designSystem.collectAsState()
    val useCustomFont by viewModel.useCustomFont.collectAsState()

    var pendingLayoutChange by remember { mutableStateOf<String?>(null) }
    var showSpotifyImport by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    val accentColor = com.akshay.musicplayer.ui.theme.LocalAccentColor.current
    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current

    val bgColor = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F7)
    val cardBg = if (isPureBlack) Color(0xFF0D0D0D) else if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val dividerColor = if (isPureBlack) Color.White.copy(alpha = 0.06f) else if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val isExpressiveSettings = com.akshay.musicplayer.ui.theme.LocalIsExpressive.current
    // M3 Expressive uses 24dp outer card corners; Classic uses 16dp
    val cardCorner = if (isExpressiveSettings) 24.dp else 16.dp

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
        } catch (e: com.google.android.gms.common.api.ApiException) {
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

    // Handle Spotify Import & About sub-screen navigation
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
                    title = { Text("App Settings", color = textPrimary, fontWeight = FontWeight.Bold) },
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
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {

            if (designSystem == "expressive") {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Material 3 Expressive Active",
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "28dp Pill Shapes • Fluid Springs • Studio Visuals",
                                    color = textSub,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // ─── 1. Google Drive Cloud Backup & Restore Section ───
            item {
                Text(
                    text = "Cloud Sync & Backup (Google Drive)",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AccentOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = googleAccount?.displayName ?: googleAccountEmail ?: googleAccount?.email ?: "Not Connected",
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) "Google Drive AppData Backup" else "Sign in to backup playlists to Google Drive",
                                    color = textSub,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) {
                            TextButton(onClick = { viewModel.signOutGoogle(context) }) {
                                Text("Sign Out", color = AccentOrange, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val repo = GoogleDriveBackupRepository(context)
                                    googleSignInLauncher.launch(repo.getSignInIntent(context))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Sign In",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) {
                        HorizontalDivider(color = dividerColor)

                        // Status Info & Backup Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (hasUnbackedUpChanges) "Changes pending backup" else "Playlists backed up",
                                    color = if (hasUnbackedUpChanges) AccentOrange else Color(0xFF4CAF50),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val lastTimeStr = if (lastBackupTimestamp > 0) {
                                    SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))
                                } else "Never"
                                val sizeStr = if (lastBackupSizeBytes > 0) {
                                    val kb = lastBackupSizeBytes / 1024.0
                                    if (kb < 1024) String.format(Locale.getDefault(), "%.1f KB", kb) else String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
                                } else "0 KB"
                                Text(
                                    text = "Last sync: $lastTimeStr • Backup Size: ~$sizeStr",
                                    color = textSub,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // SponsorBlock-Style Granular Backup Scope Customization Controls
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val sharedPrefs = context.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
                            var showBackupCustomization by remember { mutableStateOf(sharedPrefs.getBoolean("auto_cloud_backup", true)) }
                            var backupPlaylists by remember { mutableStateOf(sharedPrefs.getBoolean("backup_playlists", true)) }
                            var backupLyrics by remember { mutableStateOf(sharedPrefs.getBoolean("backup_lyrics", true)) }
                            var backupSettings by remember { mutableStateOf(sharedPrefs.getBoolean("backup_settings", true)) }

                            SettingsToggleItem(
                                title = "Auto-Sync & Cloud Backup Scope",
                                subtitle = "Enable or disable automatic background cloud sync",
                                icon = Icons.Default.CloudSync,
                                checked = showBackupCustomization,
                                isDarkMode = isDarkMode,
                                onCheckedChange = {
                                    showBackupCustomization = it
                                    sharedPrefs.edit().putBoolean("auto_cloud_backup", it).apply()
                                }
                            )

                            androidx.compose.animation.AnimatedVisibility(visible = showBackupCustomization) {
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    HorizontalDivider(color = dividerColor)
                                    SettingsToggleItem(
                                        title = "Custom Playlists",
                                        subtitle = "User created online & local playlists",
                                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                                        checked = backupPlaylists,
                                        isDarkMode = isDarkMode,
                                        onCheckedChange = {
                                            backupPlaylists = it
                                            sharedPrefs.edit().putBoolean("backup_playlists", it).apply()
                                        }
                                    )
                                    HorizontalDivider(color = dividerColor)
                                    SettingsToggleItem(
                                        title = "Custom Lyrics & Offsets",
                                        subtitle = "Saved lyrics & timestamp offsets",
                                        icon = Icons.Default.Lyrics,
                                        checked = backupLyrics,
                                        isDarkMode = isDarkMode,
                                        onCheckedChange = {
                                            backupLyrics = it
                                            sharedPrefs.edit().putBoolean("backup_lyrics", it).apply()
                                        }
                                    )
                                    HorizontalDivider(color = dividerColor)
                                    SettingsToggleItem(
                                        title = "App Preferences",
                                        subtitle = "Player settings & audio quality",
                                        icon = Icons.Default.Tune,
                                        checked = backupSettings,
                                        isDarkMode = isDarkMode,
                                        onCheckedChange = {
                                            backupSettings = it
                                            sharedPrefs.edit().putBoolean("backup_settings", it).apply()
                                        }
                                    )
                                }
                            }
                        }

                        // Action Buttons: Backup & Restore
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.performDriveBackup(context) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isBackupInProgress && !isRestoreInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isBackupInProgress) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Backup Now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.performDriveRestore(context) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isBackupInProgress && !isRestoreInProgress,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isRestoreInProgress) {
                                    CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Restore", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // Local Playlist Backup (JSON) & Thumbnail Refresh
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Local Playlists",
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Export playlists locally to JSON or import from file.",
                            color = textSub,
                            fontSize = 11.sp
                        )

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
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Failed to read file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { jsonPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ─── 2. Customization & Appearance ───
            item {
                Text(
                    text = "Customization & Appearance",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                        accentColor = AccentOrange,
                        onModeSelect = { viewModel.setThemeMode(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Pure Black (AMOLED)",
                        subtitle = "Pitch black background to save battery on OLED displays",
                        icon = Icons.Default.Contrast,
                        checked = usePureBlack,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setUsePureBlack(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    AccentColorPickerItem(
                        currentAccentId = accentColorId,
                        isDarkMode = isDarkMode,
                        onAccentSelect = { viewModel.setAccentColorId(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    FontScaleSelectorItem(
                        currentScale = fontScaleOption,
                        isDarkMode = isDarkMode,
                        accentColor = AccentOrange,
                        onScaleSelect = { viewModel.setFontScaleOption(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    CornerRadiusSelectorItem(
                        currentOption = cornerRadiusOption,
                        isDarkMode = isDarkMode,
                        accentColor = AccentOrange,
                        onOptionSelect = { viewModel.setCornerRadiusOption(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    LyricsFontSizeSelectorItem(
                        currentOption = lyricsFontSizeOption,
                        isDarkMode = isDarkMode,
                        accentColor = AccentOrange,
                        onOptionSelect = { viewModel.setLyricsFontSizeOption(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsSelectorItem(
                        title = "Player Layout Mode",
                        subtitle = "Switch between Reels full-screen swipe and Classic player with bottom tabs",
                        icon = Icons.Default.ViewCarousel,
                        currentValue = if (playerLayoutStyle == "classic") "Classic Player (Square Cover)" else "Reels Swiper (Vertical)",
                        options = listOf("Reels Swiper (Vertical)", "Classic Player (Square Cover)"),
                        isDarkMode = isDarkMode,
                        onSelect = {
                            val target = if (it.startsWith("Classic")) "classic" else "reels"
                            if (target != playerLayoutStyle) {
                                pendingLayoutChange = target
                            }
                        }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsSelectorItem(
                        title = "Design System",
                        subtitle = "Select between classic look and Material 3 Expressive tokens",
                        icon = Icons.Default.Palette,
                        currentValue = if (designSystem == "classic") "Classic Mueso" else "Material 3 Expressive",
                        options = listOf("Material 3 Expressive", "Classic Mueso"),
                        isDarkMode = isDarkMode,
                        onSelect = { viewModel.setDesignSystem(if (it.startsWith("Classic")) "classic" else "expressive") }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Use Application Font",
                        subtitle = "Bundled Google Sans Flex variable font for enhanced typography",
                        icon = Icons.Default.TextFields,
                        checked = useCustomFont,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setUseCustomFont(it) }
                    )
                }
            }

            // ─── 3. Display & Lockscreen Behavior ───
            item {
                Text(
                    text = "Display & Lockscreen Behavior",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsToggleItem(
                        title = "Show Over Lockscreen",
                        subtitle = "Display player when phone is locked",
                        icon = Icons.Default.Lock,
                        checked = showOnLockscreen,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setShowOnLockscreen(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "High Refresh Rate",
                        subtitle = "Peak display rate for ultra-smooth UI",
                        icon = Icons.Default.Bolt,
                        checked = highRefreshRate,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setHighRefreshRate(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsSelectorItem(
                        title = "Play Button Position",
                        subtitle = "Position of main play control button",
                        icon = Icons.Default.PlayCircle,
                        currentValue = playButtonPosition,
                        options = listOf("Left", "Right", "Center"),
                        isDarkMode = isDarkMode,
                        onSelect = { viewModel.setPlayButtonPosition(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsBatteryItem(isDarkMode = isDarkMode)
                }
            }

            // ─── 4. Audiophile Lossless Engine ───
            item {
                Text(
                    text = "Audiophile Lossless Engine",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsToggleItem(
                        title = "Hi-Res Lossless Streaming (FLAC)",
                        subtitle = "Stream 24-bit/96kHz studio master audio via Lossless CDN with auto-fallback to YouTube",
                        icon = Icons.Default.GraphicEq,
                        checked = losslessStreamingEnabled,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setLosslessStreamingEnabled(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Studio Master Clarity",
                        subtitle = "Acoustic high-shelf harmonic excitation (+2.5dB air curve) for vocals and instruments",
                        icon = Icons.Default.Speed,
                        checked = isStudioMasterClarityEnabled,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setStudioMasterClarityEnabled(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Bit-Perfect Mode",
                        subtitle = "Bypasses all software DSP and EQ for raw uncompressed PCM bitstream to USB DACs",
                        icon = Icons.Default.Headphones,
                        checked = isBitPerfectEnabled,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setBitPerfectEnabled(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Audio Crossfade",
                        subtitle = if (crossfadeEnabled) "Smooth fade transition: ${crossfadeSeconds}s" else "Disabled (gapless playback)",
                        icon = Icons.Default.Shuffle,
                        checked = crossfadeEnabled,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsClickableItem(
                        title = "Equalizer & Audio DSP",
                        subtitle = "Hardware multi-band EQ, bass boost & acoustic presets",
                        icon = Icons.Default.Tune,
                        isDarkMode = isDarkMode,
                        onClick = { showEqualizerSheet = true }
                    )
                }
            }

            // ─── 5. Audio & Visual Quality ───
            item {
                Text(
                    text = "Audio & Visual Quality",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                        onSelect = { viewModel.setAudioQuality(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Show Synchronized Lyrics",
                        subtitle = "Display live karaoke lyrics in player",
                        icon = Icons.Default.Lyrics,
                        checked = enableLyrics,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setEnableLyrics(it) }
                    )
                }
            }

            // ─── 5. Downloads & Offline Storage ───
            item {
                Text(
                    text = "Downloads & Storage",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsSelectorItem(
                        title = "Download Audio Quality",
                        subtitle = "Bit rate for offline audio tracks",
                        icon = Icons.Default.MusicNote,
                        currentValue = downloadQuality,
                        options = listOf("Lossless (FLAC)", "Highest (320 kbps)", "Standard (256 kbps)", "Medium (128 kbps)"),
                        isDarkMode = isDarkMode,
                        onSelect = { viewModel.setDownloadQuality(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsFolderSelectorItem(
                        title = "Audio Download Location",
                        subtitle = "Folder where downloaded audio files save",
                        icon = Icons.Default.Folder,
                        currentFolder = downloadFolder,
                        isDarkMode = isDarkMode,
                        onFolderSelect = { viewModel.setDownloadFolder(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsToggleItem(
                        title = "Embed Lyrics in Downloads",
                        subtitle = "Embed lyrics metadata & save .lrc companion",
                        icon = Icons.Default.Lyrics,
                        checked = embedLyricsInDownload,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setEmbedLyricsInDownload(it) }
                    )
                }
            }

            // ─── 6. Smart Skip (SponsorBlock Integration) ───
            item {
                Text(
                    text = "Smart Skip (SponsorBlock API)",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsToggleItem(
                        title = "Enable SponsorBlock",
                        subtitle = "Skip sponsors, promos, and non-music filler",
                        icon = Icons.Default.Shield,
                        checked = enableSponsorBlock,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setEnableSponsorBlock(it) }
                    )
                    
                    androidx.compose.animation.AnimatedVisibility(visible = enableSponsorBlock) {
                        Column(modifier = Modifier.padding(start = 24.dp)) {
                            HorizontalDivider(color = dividerColor)
                            SettingsToggleItem(title = "Skip Sponsor Segment", subtitle = "Paid brand sponsorships", icon = Icons.Default.Shield, checked = skipSponsor, isDarkMode = isDarkMode, onCheckedChange = { viewModel.setSkipSponsor(it) })
                            HorizontalDivider(color = dividerColor)
                            SettingsToggleItem(title = "Skip Self-Promotion", subtitle = "Channel promos & merch", icon = Icons.Default.Campaign, checked = skipSelfPromo, isDarkMode = isDarkMode, onCheckedChange = { viewModel.setSkipSelfPromo(it) })
                            HorizontalDivider(color = dividerColor)
                            SettingsToggleItem(title = "Skip Interaction Prompts", subtitle = "Subscribe & like reminders", icon = Icons.Default.ThumbUp, checked = skipInteraction, isDarkMode = isDarkMode, onCheckedChange = { viewModel.setSkipInteraction(it) })
                            HorizontalDivider(color = dividerColor)
                            SettingsToggleItem(title = "Skip Intros & Outros", subtitle = "Non-music intro/outro clips", icon = Icons.Default.MusicNote, checked = skipIntroOutro, isDarkMode = isDarkMode, onCheckedChange = { viewModel.setSkipIntroOutro(it) })
                            HorizontalDivider(color = dividerColor)
                            SettingsToggleItem(title = "Skip Non-Music Filler", subtitle = "Interludes & off-topic talk", icon = Icons.Default.ChatBubble, checked = skipNonMusicOffTopic, isDarkMode = isDarkMode, onCheckedChange = { viewModel.setSkipNonMusicOffTopic(it) })
                        }
                    }
                }
            }

            // ─── 7. Import Playlist ───
            item {
                Text(
                    text = "Import Playlist",
                    color = Color(0xFF1DB954),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .clickable { showSpotifyImport = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Import from Spotify", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Paste a public Spotify playlist link", color = textSub, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
                }
            }

            // ─── 8. App Updates (GitHub Releases) ───
            item {
                val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
                val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()

                Text(
                    text = "App Updates",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(AccentOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check for Updates", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = updateStatusMessage ?: "Current version: $appVersionName",
                                color = textSub,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.checkForUpdates(context, showToast = true) },
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Check", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Update available banner & install button
                    if (updateInfo?.isNewVersionAvailable == true) {
                        HorizontalDivider(color = dividerColor)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.NewReleases, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                                Text("New Version ${updateInfo!!.tagName} Available!", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            val targetAbi = updateInfo!!.targetAbi
                            val sizeStr = updateInfo!!.apkSizeString
                            if (!targetAbi.isNullOrBlank() || !sizeStr.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = listOfNotNull(
                                            targetAbi?.let { "Architecture: $it" },
                                            sizeStr?.let { "Size: ~$it" }
                                        ).joinToString(" • "),
                                        color = textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (!updateInfo!!.releaseNotes.isNullOrBlank()) {
                                Text(
                                    updateInfo!!.releaseNotes!!,
                                    color = textSub,
                                    fontSize = 12.sp,
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
                                        Text("Downloading update...", color = textSub, fontSize = 11.sp)
                                        Text("${(prog * 100).toInt()}%", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { prog },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = AccentOrange
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.downloadAndInstallUpdate(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val abiSuffix = if (!targetAbi.isNullOrBlank()) " ($targetAbi)" else ""
                                    Text("Download & Install ${updateInfo!!.tagName}$abiSuffix", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ─── 9. About Mueso ───
            item {
                Text(
                    text = "About",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(cardBg)
                        .clickable { showAboutPage = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About Mueso", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Version $appVersionName • Features, Credits & Specs", color = textSub, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
                }
            }

            // ─── 10. Force Refresh & Developer Pre-Builds ───
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = showPreBuildOption) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(cardCorner))
                                .background(cardBg)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Developer Options (GitHub Pre-Builds)", color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Fetch & install test release APK from GitHub tag 'Pre_Builds'", color = textSub, fontSize = 11.sp)
                            Button(
                                onClick = { viewModel.installPreBuildRelease(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Pre-Build (Tag: Pre_Builds)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Refresh Playlists & Rescan Songs", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        text = "Mueso Player $appVersionName • Open Source MIT",
                        color = textSub,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

        }
    }

    if (showSpotifyImport) {
        SpotifyImportScreen(
            viewModel = viewModel,
            onBackClick = { showSpotifyImport = false }
        )
    }

    if (showAboutPage) {
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

    if (pendingLayoutChange != null) {
        val targetStyle = pendingLayoutChange!!
        val targetName = if (targetStyle == "classic") "Classic Player (Square Cover & Bottom Navigation)" else "Reels Swiper (Vertical Full-Screen)"
        AlertDialog(
            onDismissRequest = { pendingLayoutChange = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = cardBg,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "App Restart Required",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Switching to $targetName reinitializes the core navigation shell and layout architecture.\n\nMueso will restart immediately to apply this change cleanly.",
                    color = textSub,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPlayerLayoutStyle(targetStyle)
                        pendingLayoutChange = null
                        val activity = context as? Activity
                        if (activity != null) {
                            val intent = activity.intent
                            activity.finish()
                            activity.startActivity(intent)
                        } else {
                            val pm = context.packageManager
                            val intent = pm.getLaunchIntentForPackage(context.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Restart Now", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLayoutChange = null }) {
                    Text("Cancel", color = textSub)
                }
            }
        )
    }
}
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

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
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(text = title, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(text = subtitle, color = textSub, fontSize = 12.sp, lineHeight = 16.sp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    isDarkMode: Boolean,
    badge: String? = null,
    accentColor: Color = AccentOrange,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    val iconTint by animateColorAsState(
        targetValue = if (checked) accentColor else (if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color(0xFF8E8E93)),
        animationSpec = tween(250),
        label = "iconTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                Text(subtitle, color = textSub, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}

@Composable
private fun SettingsSelectorItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentValue: String,
    options: List<String>,
    isDarkMode: Boolean,
    accentColor: Color = AccentOrange,
    onSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = textSub, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedContent(
                targetState = currentValue,
                transitionSpec = {
                    (fadeIn(tween(200))).togetherWith(fadeOut(tween(150)))
                },
                label = "ValueTextAnimation"
            ) { targetText ->
                Text(
                    text = targetText,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Integrated Segmented Selection Row with smooth animated color & scale micro-animations
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == currentValue ||
                    (option.contains("FLAC", ignoreCase = true) && currentValue.contains("FLAC", ignoreCase = true)) ||
                    (option.contains("Lossless", ignoreCase = true) && currentValue.contains("Lossless", ignoreCase = true)) ||
                    (option.contains("1080p") && currentValue.contains("1080p")) ||
                    (option.contains("320") && currentValue.contains("320")) ||
                    (option.contains("256") && currentValue.contains("256")) ||
                    (option.contains("160") && currentValue.contains("160")) ||
                    (option.contains("128") && currentValue.contains("128")) ||
                    (option.contains("96") && currentValue.contains("96")) ||
                    (option.contains("720p") && currentValue.contains("720p")) ||
                    (option.contains("480p") && currentValue.contains("480p"))

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(250),
                    label = "pillBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(250),
                    label = "pillTextColor"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.97f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "pillScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onSelect(option) }
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    val modes = listOf(
        Triple("system", "System", Icons.Default.BrightnessAuto),
        Triple("dark", "Dark", Icons.Default.DarkMode),
        Triple("light", "Light", Icons.Default.LightMode)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Column {
                Text("Theme Mode", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Choose dark, light, or follow system", color = textSub, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { (id, label, icon) ->
                val isSelected = currentMode.equals(id, ignoreCase = true)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(250),
                    label = "themeModeBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(250),
                    label = "themeModeText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onModeSelect(id) }
                        .padding(vertical = 8.dp),
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
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val activeAccent = com.akshay.musicplayer.ui.theme.LocalAccentColor.current
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = activeAccent, modifier = Modifier.size(20.dp))
                Column {
                    Text("Accent Color Palette", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Custom highlight color across the entire app", color = textSub, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val activeName = com.akshay.musicplayer.ui.theme.ThemePresets.getPalette(currentAccentId).name
            Text(activeName, color = activeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            if (isCustomSelected) {
                item {
                    val customPalette = com.akshay.musicplayer.ui.theme.ThemePresets.getPalette(currentAccentId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCustomDialog = true }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(customPalette.primary, customPalette.secondary)))
                                .border(2.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
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

            items(com.akshay.musicplayer.ui.theme.ThemePresets.AllPalettes) { palette ->
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary)))
                            .then(
                                if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = palette.name.substringBefore(" "),
                        color = if (isSelected) activeAccent else textSub,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
                            .border(1.5.dp, if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Custom Color",
                            tint = activeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isCustomSelected) "Edit" else "Custom",
                        color = textSub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
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
    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
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
    val currentHex = String.format(java.util.Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))

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
                // Live Color Swatch Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
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

                // Quick Palette Grid
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
                                    hexInputText = String.format(java.util.Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                                }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
                                )
                        )
                    }
                }

                // Sliders for RGB
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
                            hexInputText = String.format(java.util.Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5252), activeTrackColor = Color(0xFFFF5252))
                    )
                    Slider(
                        value = green,
                        onValueChange = {
                            green = it
                            hexInputText = String.format(java.util.Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF69F0AE), activeTrackColor = Color(0xFF69F0AE))
                    )
                    Slider(
                        value = blue,
                        onValueChange = {
                            blue = it
                            hexInputText = String.format(java.util.Locale.US, "%02X%02X%02X", red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF448AFF), activeTrackColor = Color(0xFF448AFF))
                    )
                }

                // Hex Code Input
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
private fun FontScaleSelectorItem(
    currentScale: String,
    isDarkMode: Boolean,
    accentColor: Color,
    onScaleSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    val options = listOf(
        Pair("compact", "Compact (88%)"),
        Pair("standard", "Standard (100%)"),
        Pair("comfortable", "Comfort (112%)"),
        Pair("large", "Large (125%)")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FormatSize, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Column {
                    Text("UI & Font Scale", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Scale font size and UI proportions", color = textSub, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = options.firstOrNull { it.first == currentScale }?.second ?: "Standard (100%)",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (id, label) ->
                val isSelected = currentScale.equals(id, ignoreCase = true)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(250),
                    label = "fontScaleBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(250),
                    label = "fontScaleText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onScaleSelect(id) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.substringBefore(" "),
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Live text preview card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Preview:", color = textSub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("🎵 Playing Starboy • The Weeknd (3:50)", color = textPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CornerRadiusSelectorItem(
    currentOption: String,
    isDarkMode: Boolean,
    accentColor: Color,
    onOptionSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    val options = listOf(
        Pair("rounded", "Rounded (16dp)"),
        Pair("squircle", "Squircle (10dp)"),
        Pair("sharp", "Sharp (4dp)")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CropSquare, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Column {
                    Text("Corner Style", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Border radius for cards, dialogs, and sheets", color = textSub, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = options.firstOrNull { it.first == currentOption }?.second?.substringBefore(" ") ?: "Rounded",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (id, label) ->
                val isSelected = currentOption.equals(id, ignoreCase = true)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(250),
                    label = "cornerBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(250),
                    label = "cornerText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onOptionSelect(id) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsFontSizeSelectorItem(
    currentOption: String,
    isDarkMode: Boolean,
    accentColor: Color,
    onOptionSelect: (String) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)
    val containerBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    val options = listOf(
        Pair("compact", "Compact"),
        Pair("standard", "Standard"),
        Pair("large", "Large")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FormatQuote, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Column {
                    Text("Lyrics Text Size", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Size of synced lyrics in player view", color = textSub, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = options.firstOrNull { it.first == currentOption }?.second ?: "Standard",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (id, label) ->
                val isSelected = currentOption.equals(id, ignoreCase = true)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.Transparent,
                    animationSpec = tween(250),
                    label = "lyricsSizeBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else textSub,
                    animationSpec = tween(250),
                    label = "lyricsSizeText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onOptionSelect(id) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Live lyrics preview card
        val sampleSizeSp = when (currentOption) {
            "compact" -> 18.sp
            "large" -> 26.sp
            else -> 22.sp
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "I'm tryna put you in the worst mood, ah",
                    color = accentColor,
                    fontSize = sampleSizeSp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "P1 cleaner than your church shoes, ah",
                    color = textSub.copy(alpha = 0.6f),
                    fontSize = (sampleSizeSp.value * 0.75f).sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SettingsFolderSelectorItem(
    title: String = "Audio Download Location",
    subtitle: String = "Folder where downloaded audio files save",
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Folder,
    currentFolder: String,
    isDarkMode: Boolean,
    onFolderSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

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

            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) { null }

            onFolderSelect(uri.toString())
            showDialog = false
        }
    }

    val displayFolder = when {
        currentFolder == "Internal App Storage" -> "Internal App Storage"
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = textSub, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { showDialog = true }) {
                Text(
                    text = displayFolder,
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Choose Download Location", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 1. Default Public Music Folder (Music/Mueso)
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
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = if (isPublicDefault) AccentOrange else textSub)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Public Music Folder (Music/Mueso)", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Visible in all music apps & indexed by MediaStore", color = textSub, fontSize = 12.sp)
                            }
                            if (isPublicDefault) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            }
                        }

                        // 2. Android Native Folder Picker
                        val isCustom = !isPublicDefault
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCustom) AccentOrange.copy(alpha = 0.12f) else if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                                .clickable {
                                    folderPickerLauncher.launch(null)
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = if (isCustom) AccentOrange else textSub)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Choose Custom Folder", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    if (isCustom) "Selected: $displayFolder" else "Pick with Android file manager",
                                    color = textSub,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCustom) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Close", color = textSub)
                    }
                }
            )
        }
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
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                com.akshay.musicplayer.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = if (isIgnoring) Color(0xFF4CAF50) else AccentOrange,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Background Playback",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isIgnoring) Color(0xFF4CAF50).copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isIgnoring) "UNRESTRICTED" else "OPTIMIZED",
                        color = if (isIgnoring) Color(0xFF4CAF50) else AccentOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isIgnoring) "Battery optimization disabled. Playback stays alive when screen is off." else "Battery restricted. Tap to allow unrestricted background playback.",
                color = textSub,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
