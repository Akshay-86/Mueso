package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.akshay.musicplayer.ui.viewmodel.managers.SpotifyImportState
import com.akshay.musicplayer.ui.viewmodel.managers.TrackMatchResult

private val SpotifyGreen = Color(0xFF1DB954)
private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val importState by viewModel.spotifyImportState.collectAsState()
    val playlistData by viewModel.spotifyPlaylistData.collectAsState()
    val matchResults by viewModel.spotifyMatchResults.collectAsState()
    val matchProgress by viewModel.spotifyMatchProgress.collectAsState()
    val errorMessage by viewModel.spotifyErrorMessage.collectAsState()
    val previewingTrackId by viewModel.previewingTrackId.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()

    var spotifyUrl by remember { mutableStateOf("") }

    val bgColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F7)
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val context = androidx.compose.ui.platform.LocalContext.current

    val handleBack = {
        viewModel.stopSpotifyPreview()
        onBackClick()
    }

    androidx.activity.compose.BackHandler(onBack = { handleBack() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Spotify", color = textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            // ─── URL Input Section ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Spotify Playlist URL", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Paste a public Spotify playlist link", color = textSub, fontSize = 12.sp)
                        }
                        if (importState == SpotifyImportState.Ready || importState == SpotifyImportState.Done) {
                            TextButton(onClick = {
                                viewModel.resetSpotifyImport()
                                spotifyUrl = ""
                            }) {
                                Text("New Import", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // URL Input Field
                    BasicTextField(
                        value = spotifyUrl,
                        onValueChange = { spotifyUrl = it },
                        textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(SpotifyGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (spotifyUrl.isEmpty()) {
                                    Text(
                                        "https://open.spotify.com/playlist/...",
                                        color = textSub.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // Guide text
                    Text(
                        "💡 Open Spotify → Open playlist → Click on share → Copy link.",
                        color = SpotifyGreen.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Import / Cancel Button
                    val isRunning = importState == SpotifyImportState.FetchingSpotify || importState == SpotifyImportState.MatchingTracks
                    Button(
                        onClick = {
                            if (isRunning) {
                                viewModel.resetSpotifyImport()
                            } else {
                                viewModel.fetchSpotifyPlaylist(context, spotifyUrl)
                            }
                        },
                        enabled = isRunning || spotifyUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFFF453A) else SpotifyGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRunning) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Import", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─── Error Message ───
            if (importState == SpotifyImportState.Error && errorMessage != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFF453A).copy(alpha = 0.12f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                            Text("Import Failed", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(errorMessage!!, color = textPrimary, fontSize = 13.sp)
                    }
                }
            }

            // ─── Playlist Header (after fetch) ───
            if (playlistData != null && importState != SpotifyImportState.Idle && importState != SpotifyImportState.FetchingSpotify) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        SpotifyGreen.copy(alpha = 0.18f),
                                        if (isDarkMode) Color(0xFF1C1C1E) else Color.White
                                    )
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Playlist artwork
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SpotifyGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlistData!!.artworkUrl != null) {
                                    com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                        artworkUrl = playlistData!!.artworkUrl!!,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(32.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlistData!!.name,
                                    color = textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${playlistData!!.tracks.size} tracks from Spotify",
                                    color = SpotifyGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Matching progress
                        if (importState == SpotifyImportState.MatchingTracks) {
                            val total = playlistData!!.tracks.size
                            val progress = matchProgress.toFloat() / total.coerceAtLeast(1)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Finding YouTube alternatives...", color = textSub, fontSize = 12.sp)
                                    Text("$matchProgress / $total", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = SpotifyGreen,
                                    trackColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                )
                            }
                        }

                        // Match summary
                        if (importState == SpotifyImportState.Ready || importState == SpotifyImportState.Done) {
                            val matched = matchResults.count { it.matchedTrack != null }
                            val total = matchResults.size
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                                    Text("$matched of $total tracks matched", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (matched < total) {
                                    Text("${total - matched} unmatched", color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // ─── Track Match List ───
            if (matchResults.isNotEmpty()) {
                itemsIndexed(matchResults) { index, matchResult ->
                    val isThisPreviewing = previewingTrackId == matchResult.matchedTrack?.id
                    TrackMatchItem(
                        index = index,
                        matchResult = matchResult,
                        isDarkMode = isDarkMode,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        dividerColor = dividerColor,
                        isPlaying = isThisPreviewing && isPreviewPlaying,
                        isPreviewLoading = isThisPreviewing && isPreviewLoading,
                        onPlayPreview = { track ->
                            viewModel.toggleSpotifyPreview(context, track)
                        },
                        onRetrySearch = { query -> viewModel.retrySpotifyMatch(index, query) },
                        onSelectMatch = { track -> viewModel.selectSpotifyMatch(index, track) },
                        onToggleAlternatives = { viewModel.toggleSpotifyAlternatives(index) }
                    )
                }
            }

            // ─── Create Playlist Button ───
            if (importState == SpotifyImportState.Ready) {
                item {
                    val matched = matchResults.count { it.matchedTrack != null }
                    Button(
                        onClick = { viewModel.createSpotifyPlaylist() },
                        enabled = matched > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Create Playlist ($matched tracks)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ─── Success State ───
            if (importState == SpotifyImportState.Done) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpotifyGreen.copy(alpha = 0.12f))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(48.dp))
                        Text("Playlist Created!", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "\"${playlistData?.name}\" has been added to your Online Playlists",
                            color = textSub,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back to Settings", color = textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ─── Creating State ───
            if (importState == SpotifyImportState.Creating) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(32.dp))
                            Text("Creating playlist...", color = textSub, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Individual Track Match Item ───

@Composable
private fun TrackMatchItem(
    index: Int,
    matchResult: TrackMatchResult,
    isDarkMode: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSub: Color,
    dividerColor: Color,
    isPlaying: Boolean,
    isPreviewLoading: Boolean,
    onPlayPreview: (TrackEntity) -> Unit,
    onRetrySearch: (String) -> Unit,
    onSelectMatch: (TrackEntity) -> Unit,
    onToggleAlternatives: () -> Unit
) {
    val isMatched = matchResult.matchedTrack != null
    var manualQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Top: Spotify original track ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Index number
            Text(
                "${index + 1}",
                color = textSub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(22.dp)
            )

            // Spotify icon badge
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    matchResult.spotifyTrack.title,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    matchResult.spotifyTrack.artist,
                    color = SpotifyGreen.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Status badge
            when {
                matchResult.isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentOrange,
                        strokeWidth = 2.dp
                    )
                }
                isMatched -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Matched", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                }
                else -> {
                    Icon(Icons.Default.Error, contentDescription = "Not Found", tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Bottom: Matched YT track (or "Not Found") ──
        if (!matchResult.isSearching) {
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 2.dp))

            if (isMatched) {
                // Matched YT track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                        .clickable { onToggleAlternatives() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // YT thumbnail
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val artworkUrl = matchResult.matchedTrack!!.artworkUrl
                        if (artworkUrl != null) {
                            com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                artworkUrl = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("▶", color = Color(0xFFFF0000), fontSize = 10.sp)
                            Text("YouTube Match", color = textSub, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            matchResult.matchedTrack!!.title,
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            matchResult.matchedTrack!!.artist,
                            color = textSub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Play Preview Button
                    IconButton(
                        onClick = { matchResult.matchedTrack?.let { onPlayPreview(it) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isPreviewLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentOrange,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Preview Track",
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Expand/collapse alternatives
                    Icon(
                        if (matchResult.showAlternatives) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Alternatives",
                        tint = textSub,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Not found — show search option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFF453A).copy(alpha = 0.08f))
                        .clickable {
                            showSearchField = true
                            manualQuery = "${matchResult.spotifyTrack.title} ${matchResult.spotifyTrack.artist}"
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                    Text("No match found", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Search, contentDescription = "Manual Search", tint = textSub, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Manual Search Field ──
        AnimatedVisibility(visible = showSearchField || (matchResult.showAlternatives && matchResult.alternativeResults.isNotEmpty())) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Search input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = textSub, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = manualQuery,
                        onValueChange = { manualQuery = it },
                        textStyle = TextStyle(color = textPrimary, fontSize = 13.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(AccentOrange),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (manualQuery.isEmpty()) Text("Search YouTube...", color = textSub.copy(alpha = 0.5f), fontSize = 13.sp)
                                inner()
                            }
                        }
                    )
                    if (manualQuery.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentOrange)
                                .clickable { onRetrySearch(manualQuery) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Search", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Alternative results list
                if (matchResult.alternativeResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        matchResult.alternativeResults.forEachIndexed { altIndex, alt ->
                            val isSelected = matchResult.matchedTrack?.id == alt.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMatch(alt) }
                                    .background(if (isSelected) SpotifyGreen.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val artUrl = alt.artworkUrl
                                    if (artUrl != null) {
                                        com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                            artworkUrl = artUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = textSub, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(alt.title, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(alt.artist, color = textSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                                }
                            }

                            if (altIndex < matchResult.alternativeResults.size - 1) {
                                HorizontalDivider(color = dividerColor)
                            }
                        }
                    }
                }

                // Searching indicator
                if (matchResult.isSearching) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Searching...", color = textSub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
