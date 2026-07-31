@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import java.util.Locale
import kotlinx.coroutines.launch

private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
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
    val thumbnailQuality by viewModel.thumbnailQuality.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val downloadFolder by viewModel.downloadFolder.collectAsState()
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

    var showSpotifyImport by remember { mutableStateOf(false) }

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

    val bgColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F7)
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

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

    // Handle Spotify Import sub-screen navigation
    androidx.activity.compose.BackHandler(enabled = showSpotifyImport) {
        showSpotifyImport = false
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
                        .clip(RoundedCornerShape(16.dp))
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

                        if (!googleAccountEmail.isNullOrBlank() || googleAccount != null) {
                            TextButton(onClick = { viewModel.signOutGoogle(context) }) {
                                Text("Sign Out", color = AccentOrange, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val repo = GoogleDriveBackupRepository(context)
                                    googleSignInLauncher.launch(repo.getSignInIntent(context))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Sign In", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            val sharedPrefs = context.getSharedPreferences("music_player_prefs", android.content.Context.MODE_PRIVATE)
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

            // ─── 2. Display & Lockscreen Behavior ───
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsToggleItem(
                        title = "Dark Mode",
                        subtitle = "Glassmorphic dark aesthetic",
                        icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        checked = isDarkMode,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                    HorizontalDivider(color = dividerColor)
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
                }
            }

            // ─── 3. Audio & Visual Quality ───
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
                        .clip(RoundedCornerShape(16.dp))
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
                    SettingsSelectorItem(
                        title = "Thumbnail Resolution",
                        subtitle = "Cover artwork resolution",
                        icon = Icons.Default.Image,
                        currentValue = thumbnailQuality,
                        options = listOf("Highest (1080p Maxres)", "High (720p)", "Medium (480p)", "Low (Fast)"),
                        isDarkMode = isDarkMode,
                        onSelect = { viewModel.setThumbnailQuality(it) }
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

            // ─── 4. Downloads & Offline Storage ───
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsSelectorItem(
                        title = "Download Audio Quality",
                        subtitle = "Bit rate for offline tracks",
                        icon = Icons.Default.Download,
                        currentValue = downloadQuality,
                        options = listOf("Highest (320 kbps)", "Standard (256 kbps)", "Medium (128 kbps)"),
                        isDarkMode = isDarkMode,
                        onSelect = { viewModel.setDownloadQuality(it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    SettingsFolderSelectorItem(
                        currentFolder = downloadFolder,
                        isDarkMode = isDarkMode,
                        onFolderSelect = { viewModel.setDownloadFolder(it) }
                    )
                }
            }

            // ─── 5. Smart Skip (SponsorBlock Integration) ───
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    SettingsToggleItem(
                        title = "Enable SponsorBlock",
                        subtitle = "Skip sponsors, promos, and non-music filler",
                        icon = Icons.Default.Shield,
                        checked = enableSponsorBlock,
                        isDarkMode = isDarkMode,
                        onCheckedChange = { viewModel.settingsManager.setEnableSponsorBlock(it) }
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

            // ─── 6. Import Playlist ───
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
                        .clip(RoundedCornerShape(16.dp))
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

            // ─── 7. App Updates (GitHub Releases) ───
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
                        .clip(RoundedCornerShape(16.dp))
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
                                    Text("Download & Install ${updateInfo!!.tagName}", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ─── 8. Force Refresh & Developer Pre-Builds ───
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
                                .clip(RoundedCornerShape(16.dp))
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
                            .clip(RoundedCornerShape(16.dp))
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
}
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    isDarkMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    val iconTint by animateColorAsState(
        targetValue = if (checked) AccentOrange else (if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color(0xFF8E8E93)),
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
            Column {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = textSub, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentOrange)
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
                Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = textSub, fontSize = 12.sp)
                }
            }

            AnimatedContent(
                targetState = currentValue,
                transitionSpec = {
                    (fadeIn(tween(200))).togetherWith(fadeOut(tween(150)))
                },
                label = "ValueTextAnimation"
            ) { targetText ->
                Text(
                    text = targetText,
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
                    (option.contains("1080p") && currentValue.contains("1080p")) ||
                    (option.contains("320") && currentValue.contains("320")) ||
                    (option.contains("256") && currentValue.contains("256")) ||
                    (option.contains("160") && currentValue.contains("160")) ||
                    (option.contains("128") && currentValue.contains("128")) ||
                    (option.contains("96") && currentValue.contains("96")) ||
                    (option.contains("720p") && currentValue.contains("720p")) ||
                    (option.contains("480p") && currentValue.contains("480p"))

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) AccentOrange else Color.Transparent,
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
private fun SettingsFolderSelectorItem(
    currentFolder: String,
    isDarkMode: Boolean,
    onFolderSelect: (String) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Column {
                    Text("Target Download Location", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Folder where MP3 files save", color = textSub, fontSize = 12.sp)
                }
            }
            TextButton(onClick = { showCustomDialog = true }) {
                Text(currentFolder, color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showCustomDialog) {
            AlertDialog(
                onDismissRequest = { showCustomDialog = false },
                title = { Text("Choose Download Folder", color = textPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Music/Mueso", "Downloads", "Internal App Storage").forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onFolderSelect(folder)
                                        showCustomDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(folder, color = textPrimary)
                                if (folder == currentFolder) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentOrange)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            placeholder = { Text("Or custom path e.g. /sdcard/Music", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (customInput.isNotBlank()) {
                            onFolderSelect(customInput.trim())
                        }
                        showCustomDialog = false
                    }) {
                        Text("Save Custom", color = AccentOrange)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDialog = false }) {
                        Text("Cancel", color = textSub)
                    }
                }
            )
        }
    }
}
