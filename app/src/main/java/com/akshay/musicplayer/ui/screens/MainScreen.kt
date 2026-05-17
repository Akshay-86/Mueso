package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isOnlineActive = pagerState.currentPage == 0

    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    androidx.activity.compose.BackHandler(enabled = pagerState.currentPage != 1 && selectedPlaylist == null && !isSearchActive) {
        coroutineScope.launch { pagerState.animateScrollToPage(1) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Pager always alive
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isSearchActive
        ) { page ->
            when (page) {
                0 -> PlaceholderScreen("Under development")
                1 -> PlayerScreen(viewModel = viewModel)
                2 -> OfflineLibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onPlaylistClick = { selectedPlaylist = it }
                )
            }
        }

        // Top Navigation Bar with integrated search
        TopNavigationBarWithSearch(
            isOnlineActive = isOnlineActive,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchClick = { isSearchActive = true },
            onSearchClose = {
                isSearchActive = false
                viewModel.setSearchQuery("")
            },
            onQueryChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating search results dropdown
        AnimatedVisibility(
            visible = isSearchActive && searchQuery.isNotEmpty(),
            enter = expandVertically(
                animationSpec = tween(250),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            val results by remember(searchQuery) {
                derivedStateOf { viewModel.getSearchResults() }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A2E))
            ) {
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No results for \"$searchQuery\"",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "${results.size} result${if (results.size != 1) "s" else ""}",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(count = results.size) { index ->
                                val track = results[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.playTrack(track)
                                            isSearchActive = false
                                            viewModel.setSearchQuery("")
                                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scrim when search is active
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (searchQuery.isNotEmpty()) 500.dp else 100.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    }
            )
        }

        // Overlay Playlist Detail
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

// ─── Top Navigation Bar ──────────────────────────────────────

@Composable
fun TopNavigationBarWithSearch(
    isOnlineActive: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Use a Box with weight(1f) so the layout size stays fixed during animation
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Left: Online / Offline labels (hide when search is active)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
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
            }

            // Right: Search bar
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = expandHorizontally(
                    animationSpec = tween(300),
                    expandFrom = Alignment.End
                ) + fadeIn(tween(200)),
                exit = shrinkHorizontally(
                    animationSpec = tween(250),
                    shrinkTowards = Alignment.End
                ) + fadeOut(tween(150)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentOrange),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search songs...",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        } // End AnimatedVisibility
        } // End Box

        // Search / Close toggle button
        IconButton(
            onClick = {
                if (isSearchActive) onSearchClose() else onSearchClick()
            }
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
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
            .background(BgDark)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(title, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineMedium)
    }
}
