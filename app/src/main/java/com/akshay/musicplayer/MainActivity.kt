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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup ViewModel
        setupViewModel()

        setContent {
            MusicPlayerTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(onAnimationFinished = { showSplash = false })
                } else {
                    PermissionAwarePlayerScreen()
                }
            }
        }
    }

    private fun setupViewModel() {
        val contentResolver: ContentResolver = contentResolver
        val mediaStoreDataSource = LocalMediaStoreDataSource(contentResolver, Dispatchers.IO)
        val trackRepository = TrackRepositoryImpl(mediaStoreDataSource)
        val getLocalTracksUseCase = GetLocalTracksUseCase(trackRepository)
        val mediaPlayerController = ExoPlayerController(this)
        val playlistDao = com.akshay.musicplayer.data.db.AppDatabase.getDatabase(this).playlistDao()
        val prefs = getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)

        playerViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        getLocalTracksUseCase,
                        mediaPlayerController,
                        playlistDao,
                        prefs
                    ) as T
                }
            }
        ).get(PlayerViewModel::class.java)
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
            PermissionGate(permission = android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun MultiplePermissionsGate(permissions: List<String>) {
        val multiplePermissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(permissions)

        LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
            if (!multiplePermissionsState.allPermissionsGranted) {
                multiplePermissionsState.launchMultiplePermissionRequest()
            } else {
                playerViewModel.loadLocalTracks()
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

        LaunchedEffect(permissionState.status.isGranted) {
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                playerViewModel.loadLocalTracks()
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
}
