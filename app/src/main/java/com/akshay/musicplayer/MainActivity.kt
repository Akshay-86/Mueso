package com.akshay.musicplayer

import android.content.ContentResolver
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.akshay.musicplayer.data.repository.TrackRepositoryImpl
import com.akshay.musicplayer.data.sources.LocalMediaStoreDataSource
import com.akshay.musicplayer.domain.usecase.GetLocalTracksUseCase
import com.akshay.musicplayer.media.player.ExoPlayerController
import com.akshay.musicplayer.ui.screens.MainScreen
import com.akshay.musicplayer.ui.screens.PlayerScreen
import com.akshay.musicplayer.ui.screens.SplashScreen
import com.akshay.musicplayer.ui.theme.MusicPlayerTheme
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var mediaPlayerController: ExoPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        AppContainer.initialize(applicationContext)

        // Setup ViewModel
        setupViewModel()

        try {
            val serviceIntent = android.content.Intent(this, com.akshay.musicplayer.media.service.MusicPlayerService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to startService: ${e.message}")
        }

        handleNotificationIntent(intent)

        setContent {
            val isDarkMode by playerViewModel.isDarkMode.collectAsState()
            val themeMode by playerViewModel.themeMode.collectAsState()
            val usePureBlack by playerViewModel.usePureBlack.collectAsState()
            val accentColorId by playerViewModel.accentColorId.collectAsState()
            val fontScaleOption by playerViewModel.fontScaleOption.collectAsState()
            val cornerRadiusOption by playerViewModel.cornerRadiusOption.collectAsState()
            val lyricsFontSizeOption by playerViewModel.lyricsFontSizeOption.collectAsState()

            val showOnLockscreen by playerViewModel.showOnLockscreen.collectAsState()
            val highRefreshRate by playerViewModel.highRefreshRate.collectAsState()

            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isEffectiveDark = when (com.akshay.musicplayer.ui.theme.ThemeMode.fromId(themeMode)) {
                com.akshay.musicplayer.ui.theme.ThemeMode.SYSTEM -> systemInDark
                com.akshay.musicplayer.ui.theme.ThemeMode.DARK -> true
                com.akshay.musicplayer.ui.theme.ThemeMode.LIGHT -> false
            }

            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isEffectiveDark
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        insetsController.isAppearanceLightNavigationBars = !isEffectiveDark
                    }
                }
            }

            val pendingDeleteIntent by playerViewModel.pendingDeleteIntent.collectAsState()
            val pendingWriteIntent by playerViewModel.pendingWriteIntent.collectAsState()

            val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    android.widget.Toast.makeText(this, "Song deleted from device", android.widget.Toast.LENGTH_SHORT).show()
                }
                playerViewModel.clearPendingDeleteIntent()
            }

            val writeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    android.widget.Toast.makeText(this, "Permission granted", android.widget.Toast.LENGTH_SHORT).show()
                }
                playerViewModel.clearPendingWriteIntent()
            }

            LaunchedEffect(pendingDeleteIntent) {
                pendingDeleteIntent?.let { sender ->
                    deleteLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                    )
                }
            }

            LaunchedEffect(pendingWriteIntent) {
                pendingWriteIntent?.let { sender ->
                    writeLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                    )
                }
            }

            LaunchedEffect(showOnLockscreen) {
                updateLockScreenDisplay(showOnLockscreen)
            }
            LaunchedEffect(highRefreshRate) {
                updateRefreshRate(highRefreshRate)
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            var showBatteryDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val muesoPrefs = getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
                val alreadyPrompted = muesoPrefs.getBoolean("has_prompted_battery_optimization", false)
                if (!alreadyPrompted && !com.akshay.musicplayer.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                    kotlinx.coroutines.delay(1200)
                    showBatteryDialog = true
                }
            }

            MusicPlayerTheme(
                themeMode = themeMode,
                usePureBlack = usePureBlack,
                accentColorId = accentColorId,
                fontScaleOption = fontScaleOption,
                cornerRadiusOption = cornerRadiusOption,
                lyricsFontSizeOption = lyricsFontSizeOption
            ) {
                if (showBatteryDialog) {
                    val accentColor = com.akshay.musicplayer.ui.theme.LocalAccentColor.current
                    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
                    val dialogBg = if (isPureBlack) Color(0xFF0D0D0D) else MaterialTheme.colorScheme.surface
                    val textPrimary = MaterialTheme.colorScheme.onSurface
                    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

                    AlertDialog(
                        onDismissRequest = {
                            showBatteryDialog = false
                            getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("has_prompted_battery_optimization", true).apply()
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = dialogBg,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Background Playback",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        text = {
                            Text(
                                text = "To ensure music keeps playing continuously when your screen is locked or while using other apps, please allow Mueso to run without battery restrictions.",
                                color = textSub,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showBatteryDialog = false
                                    getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("has_prompted_battery_optimization", true).apply()
                                    com.akshay.musicplayer.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Unrestricted", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showBatteryDialog = false
                                    getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("has_prompted_battery_optimization", true).apply()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Maybe Later", color = textSub)
                            }
                        }
                    )
                }

                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onAnimationFinished = {
                            showSplash = false
                        }
                    )
                } else {
                    PermissionAwarePlayerScreen()
                }
            }
        }
    }

    private fun setupViewModel() {
        val appContext = applicationContext
        val contentResolver: ContentResolver = appContext.contentResolver
        val mediaStoreDataSource = LocalMediaStoreDataSource(contentResolver, Dispatchers.IO)
        val trackRepository = TrackRepositoryImpl(mediaStoreDataSource)
        val getLocalTracksUseCase = GetLocalTracksUseCase(trackRepository)
        val db = com.akshay.musicplayer.data.db.AppDatabase.getDatabase(appContext)
        val playlistDao = db.playlistDao()
        val onlinePlaylistDao = db.onlinePlaylistDao()
        val prefs = appContext.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)

        playerViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val controller = ExoPlayerController(appContext)
                    return PlayerViewModel(
                        getLocalTracksUseCase,
                        controller,
                        playlistDao,
                        onlinePlaylistDao,
                        prefs
                    ) as T
                }
            }
        ).get(PlayerViewModel::class.java)

        mediaPlayerController = playerViewModel.mediaPlayerController as ExoPlayerController
    }

    override fun onResume() {
        super.onResume()
        if (::playerViewModel.isInitialized) {
            playerViewModel.checkAndResumePendingInstall(this)
        }
    }

    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun updateLockScreenDisplay(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    private fun updateRefreshRate(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
            val params = window.attributes
            params.preferredDisplayModeId = if (enabled && maxMode != null) maxMode.modeId else 0
            window.attributes = params
        }
    }

    @Composable
    private fun PermissionAwarePlayerScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MultiplePermissionsGate(
                permissions = listOf(
                    android.Manifest.permission.READ_MEDIA_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            MultiplePermissionsGate(
                permissions = listOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun MultiplePermissionsGate(permissions: List<String>) {
        val multiplePermissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(permissions)

        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
            if (!multiplePermissionsState.allPermissionsGranted) {
                multiplePermissionsState.launchMultiplePermissionRequest()
            } else {
                playerViewModel.restoreLastPlaybackStateOrOffline(context)
            }
        }

        if (multiplePermissionsState.allPermissionsGranted) {
            MainScreen(viewModel = playerViewModel)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Audio and Notification permissions required", color = Color.White)
                    Button(onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }) {
                        Text("Grant permissions")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun PermissionGate(permission: String) {
        val permissionState = rememberPermissionState(permission)

        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(permissionState.status.isGranted) {
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                playerViewModel.restoreLastPlaybackStateOrOffline(context)
            }
        }

        if (permissionState.status.isGranted) {
            MainScreen(viewModel = playerViewModel)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Audio permission required")
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant permission")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        when (intent.action) {
            com.akshay.musicplayer.media.notification.NotificationHelper.ACTION_OPEN_OFFLINE_LIBRARY -> {
                playerViewModel.setOfflineLibraryTab(1)
            }
            com.akshay.musicplayer.media.notification.NotificationHelper.ACTION_PLAY_DOWNLOADED -> {
                val filePath = intent.getStringExtra(com.akshay.musicplayer.media.notification.NotificationHelper.EXTRA_FILE_PATH)
                if (!filePath.isNullOrBlank()) {
                    playerViewModel.playLocalTrackByPath(filePath)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        playerViewModel.saveCurrentPlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.saveCurrentPlaybackPosition()
    }

    override fun onDestroy() {
        playerViewModel.saveCurrentPlaybackPosition()
        super.onDestroy()
        if (isFinishing) {
            mediaPlayerController.release()
        }
    }
}
