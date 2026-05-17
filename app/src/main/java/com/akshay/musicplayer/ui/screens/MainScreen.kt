package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    // 0: Online Library, 1: Online Player, 2: Offline Player, 3: Offline Library
    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    // Derived state for the active tab highlighting
    val isOnlineActive = pagerState.currentPage <= 1

    androidx.activity.compose.BackHandler(enabled = pagerState.currentPage != 2 && selectedPlaylist == null) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(2)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> PlaceholderScreen("Online Library")
                1 -> PlaceholderScreen("Online Player")
                2 -> PlayerScreen(viewModel = viewModel)
                3 -> OfflineLibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    onPlaylistClick = { selectedPlaylist = it }
                )
            }
        }

        // Custom Top Navigation Bar overlay
        TopNavigationBar(
            isOnlineActive = isOnlineActive,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Overlay Playlist Detail Screen
        if (selectedPlaylist != null) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist!!,
                viewModel = viewModel,
                onBack = { selectedPlaylist = null },
                onNavigateToPlayer = {
                    selectedPlaylist = null
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                }
            )
        }
    }
}

@Composable
fun TopNavigationBar(
    isOnlineActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp), // Adjust padding for status bar
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Online",
                color = if (isOnlineActive) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = if (isOnlineActive) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Offline",
                color = if (!isOnlineActive) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = if (!isOnlineActive) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        IconButton(onClick = { /* TODO: Search */ }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.White
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
    }
}
