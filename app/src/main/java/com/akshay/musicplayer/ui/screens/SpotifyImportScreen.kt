package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youtubeUserName by viewModel.youtubeUserName.collectAsState()
    val importState by viewModel.spotifyImportState.collectAsState()
    val playlistData by viewModel.spotifyPlaylistData.collectAsState()
    val matchResults by viewModel.spotifyMatchResults.collectAsState()
    val matchProgress by viewModel.spotifyMatchProgress.collectAsState()
    val errorMessage by viewModel.spotifyErrorMessage.collectAsState()
    val spotifyCreatedDestination by viewModel.spotifyCreatedDestination.collectAsState()
    val syncTargetPlaylistName by viewModel.spotifySyncTargetPlaylistName.collectAsState()
    val isSyncMode = viewModel.spotifyIsSyncMode
    val previewingTrackId by viewModel.previewingTrackId.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()

    var spotifyUrl by remember { mutableStateOf("") }
    var showDestinationDialog by remember { mutableStateOf(false) }

    val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
    val bgColor = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F7)
    val cardBg = if (isPureBlack) Color(0xFF0D0D0D) else if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val dividerColor = if (isPureBlack) Color.White.copy(alpha = 0.06f) else if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val context = androidx.compose.ui.platform.LocalContext.current

    val handleBack = {
        viewModel.stopSpotifyPreview()
        onBackClick()
    }

    androidx.activity.compose.BackHandler(onBack = { handleBack() })

    if (showDestinationDialog) {
        AlertDialog(
            onDismissRequest = { showDestinationDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Save Playlist To",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose where to create \"${playlistData?.name ?: "this playlist"}\":",
                        color = textSub,
                        fontSize = 13.sp
                    )

                    // Option 1: Mueso Online Playlist
                    DestinationOptionCard(
                        title = "Mueso Online Playlist",
                        subtitle = "Custom playlist with cloud backup",
                        icon = Icons.Default.CloudQueue,
                        iconBg = AccentOrange,
                        isDarkMode = isDarkMode,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        onClick = {
                            showDestinationDialog = false
                            viewModel.createSpotifyPlaylist()
                        }
                    )

                    // Option 2: YouTube Music Account Playlist
                    DestinationOptionCard(
                        title = "YouTube Music Playlist",
                        subtitle = if (isYouTubeLoggedIn) "Syncs directly to your YouTube account (${youtubeUserName ?: "Signed In"})"
                        else "Sign in to YouTube to sync directly",
                        icon = Icons.Default.PlayArrow,
                        iconBg = Color(0xFFFF0000),
                        badgeText = if (isYouTubeLoggedIn) "Connected" else null,
                        badgeColor = Color(0xFF34C759),
                        isDarkMode = isDarkMode,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        onClick = {
                            if (!isYouTubeLoggedIn) {
                                android.widget.Toast.makeText(context, "Please sign in to YouTube Music in the Online tab first", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                showDestinationDialog = false
                                viewModel.createSpotifyYouTubePlaylist()
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDestinationDialog = false }) {
                    Text("Cancel", color = textSub)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSyncMode) "Sync with Spotify" else "Import from Spotify",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (importState == SpotifyImportState.Ready) {
                val selectedCount = matchResults.count { it.isSelected && it.matchedTrack != null }
                if (selectedCount > 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isSyncMode) {
                                viewModel.appendSpotifySyncTracks()
                            } else {
                                showDestinationDialog = true
                            }
                        },
                        containerColor = if (isSyncMode) SpotifyGreen else AccentOrange,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        ),
                        icon = {
                            AnimatedContent(
                                targetState = isSyncMode,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn(tween(150)))
                                        .togetherWith(scaleOut(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeOut(tween(150)))
                                },
                                label = "fabIcon"
                            ) { sync ->
                                Icon(
                                    if (sync) Icons.Default.Sync else Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        text = {
                            Text(
                                text = if (isSyncMode) "Add to Playlist ($selectedCount tracks)" else "Create Playlist ($selectedCount tracks)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        },
        containerColor = bgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
        ) {
            // ─── Header / URL Section ───
            item {
                if (isSyncMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen.copy(alpha = 0.15f))
                                        .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Syncing Spotify Playlist", color = textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                                    Text("Destination: ${syncTargetPlaylistName ?: "Playlist"}", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                "Comparing your Spotify playlist with Mueso. Matching newly added tracks below:",
                                color = textSub,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen.copy(alpha = 0.15f))
                                        .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Spotify Playlist URL", color = textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                                    Text("Paste a public Spotify playlist link", color = textSub, fontSize = 12.sp)
                                }
                                if (importState == SpotifyImportState.Ready || importState == SpotifyImportState.Done) {
                                    val newImportInteraction = remember { MutableInteractionSource() }
                                    val isNewImportPressed by newImportInteraction.collectIsPressedAsState()
                                    val newImportScale by animateFloatAsState(
                                        targetValue = if (isNewImportPressed) 0.94f else 1f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                        label = "newImportScale"
                                    )
                                    Surface(
                                        onClick = {
                                            viewModel.resetSpotifyImport()
                                            spotifyUrl = ""
                                        },
                                        interactionSource = newImportInteraction,
                                        shape = RoundedCornerShape(20.dp),
                                        color = SpotifyGreen.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.35f)),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = newImportScale
                                            scaleY = newImportScale
                                        }
                                    ) {
                                        Text(
                                            "New Import",
                                            color = SpotifyGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // URL Input Field
                            val urlInteractionSource = remember { MutableInteractionSource() }
                            val isUrlFocused by urlInteractionSource.collectIsFocusedAsState()
                            val urlBorderColor by animateColorAsState(
                                targetValue = if (isUrlFocused) SpotifyGreen.copy(alpha = 0.65f)
                                else if (isDarkMode) Color.White.copy(alpha = 0.09f)
                                else Color.Black.copy(alpha = 0.08f),
                                label = "urlBorder"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f))
                                    .border(1.dp, urlBorderColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BasicTextField(
                                    value = spotifyUrl,
                                    onValueChange = { spotifyUrl = it },
                                    textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                                    singleLine = true,
                                    cursorBrush = SolidColor(SpotifyGreen),
                                    interactionSource = urlInteractionSource,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
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
                                if (spotifyUrl.isNotEmpty()) {
                                    IconButton(
                                        onClick = { spotifyUrl = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = textSub,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Guide text capsule
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SpotifyGreen.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "💡 Open Spotify → Open playlist → Click on share → Copy link.",
                                    color = SpotifyGreen.copy(alpha = 0.95f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            // Import / Cancel Button
                            val isRunning = importState == SpotifyImportState.FetchingSpotify || importState == SpotifyImportState.MatchingTracks
                            val importBtnInteraction = remember { MutableInteractionSource() }
                            val isImportBtnPressed by importBtnInteraction.collectIsPressedAsState()
                            val importBtnScale by animateFloatAsState(
                                targetValue = if (isImportBtnPressed) 0.96f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "importBtnScale"
                            )

                            Button(
                                onClick = {
                                    if (isRunning) {
                                        viewModel.resetSpotifyImport()
                                    } else {
                                        viewModel.fetchSpotifyPlaylist(context, spotifyUrl)
                                    }
                                },
                                enabled = isRunning || spotifyUrl.isNotBlank(),
                                interactionSource = importBtnInteraction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunning) Color(0xFFFF453A) else SpotifyGreen
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .graphicsLayer {
                                        scaleX = importBtnScale
                                        scaleY = importBtnScale
                                    }
                            ) {
                                AnimatedContent(
                                    targetState = isRunning,
                                    transitionSpec = {
                                        (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn(tween(150)))
                                            .togetherWith(scaleOut(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeOut(tween(150)))
                                    },
                                    label = "importBtnContent"
                                ) { running ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (running) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Text("Cancel Import", color = Color.White, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Text("Import Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Error Message ───
            if (importState == SpotifyImportState.Error && errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFF453A).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                                Text("Import Failed", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            }
                            Text(errorMessage!!, color = textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ─── Playlist Header (after fetch) ───
            if (playlistData != null && importState != SpotifyImportState.Idle && importState != SpotifyImportState.FetchingSpotify) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            SpotifyGreen.copy(alpha = 0.16f),
                                            cardBg
                                        )
                                    )
                                )
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Playlist artwork
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SpotifyGreen.copy(alpha = 0.2f))
                                        .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playlistData!!.artworkUrl != null) {
                                        com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                            artworkUrl = playlistData!!.artworkUrl!!,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            thumbnailQuality = "Medium (480p)"
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
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SpotifyGreen.copy(alpha = 0.14f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            "${playlistData!!.tracks.size} tracks from Spotify",
                                            color = SpotifyGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Matching progress
                            if (importState == SpotifyImportState.MatchingTracks) {
                                val total = playlistData!!.tracks.size
                                val progress = matchProgress.toFloat() / total.coerceAtLeast(1)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Finding YouTube alternatives...", color = textSub, fontSize = 12.sp)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SpotifyGreen.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                "$matchProgress / $total",
                                                color = SpotifyGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = SpotifyGreen,
                                        trackColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                    )
                                }
                            }

                            // Match summary & Checkbox selection
                            if (importState == SpotifyImportState.Ready || importState == SpotifyImportState.Done) {
                                val matched = matchResults.count { it.matchedTrack != null }
                                val total = matchResults.size
                                val selectedCount = matchResults.count { it.isSelected && it.matchedTrack != null }
                                val allMatchedSelected = matched > 0 && matchResults.filter { it.matchedTrack != null }.all { it.isSelected }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.025f),
                                    border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = allMatchedSelected,
                                                onCheckedChange = { isChecked ->
                                                    viewModel.selectAllSpotifyTracks(isChecked)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = SpotifyGreen,
                                                    uncheckedColor = textSub
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                "$selectedCount of $matched selected",
                                                color = textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (matched < total) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFFFF453A).copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    "${total - matched} unmatched",
                                                    color = Color(0xFFFF453A),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
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
                        onToggleSelect = { viewModel.toggleSpotifyTrackSelection(index) },
                        onPlayPreview = { track ->
                            viewModel.toggleSpotifyPreview(context, track)
                        },
                        onRetrySearch = { query -> viewModel.retrySpotifyMatch(index, query) },
                        onSelectMatch = { track -> viewModel.selectSpotifyMatch(index, track) },
                        onToggleAlternatives = { viewModel.toggleSpotifyAlternatives(index) }
                    )
                }
            }

            // ─── Success State ───
            if (importState == SpotifyImportState.Done) {
                item {
                    val isYt = spotifyCreatedDestination == "youtube"
                    val successTint = if (isSyncMode) SpotifyGreen else if (isYt) Color(0xFFFF0000) else SpotifyGreen
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = successTint.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, successTint.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(successTint.copy(alpha = 0.18f))
                                    .border(1.5.dp, successTint, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successTint, modifier = Modifier.size(36.dp))
                            }
                            Text(
                                if (isSyncMode) "Sync Complete!" else if (isYt) "Created in YouTube Music!" else "Playlist Created in Mueso!",
                                color = textPrimary,
                                fontSize = 18.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isSyncMode) "Successfully added newly matched tracks to \"${syncTargetPlaylistName ?: "playlist"}\"."
                                else if (isYt) "\"${playlistData?.name}\" has been synced directly to your YouTube Music account."
                                else "\"${playlistData?.name}\" has been saved to your Mueso Online Playlists.",
                                color = textSub,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            val doneBtnInteraction = remember { MutableInteractionSource() }
                            val isDoneBtnPressed by doneBtnInteraction.collectIsPressedAsState()
                            val doneBtnScale by animateFloatAsState(
                                targetValue = if (isDoneBtnPressed) 0.94f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "doneBtnScale"
                            )

                            Button(
                                onClick = { handleBack() },
                                interactionSource = doneBtnInteraction,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = successTint
                                ),
                                modifier = Modifier.graphicsLayer {
                                    scaleX = doneBtnScale
                                    scaleY = doneBtnScale
                                }
                            ) {
                                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ─── Creating State ───
            if (importState == SpotifyImportState.Creating) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = if (isSyncMode) SpotifyGreen else AccentOrange,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                if (isSyncMode) "Adding tracks to playlist..." else "Creating playlist...",
                                color = textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Destination Option Card for Save Dialog ───

@Composable
private fun DestinationOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    badgeText: String? = null,
    badgeColor: Color = Color(0xFF34C759),
    isDarkMode: Boolean,
    textPrimary: Color,
    textSub: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "destCardScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = textSub,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

// ─── Status Capsule Component ───

@Composable
private fun MatchStatusCapsule(
    isSearching: Boolean,
    isMatched: Boolean,
    isSelected: Boolean,
    isDarkMode: Boolean,
    accentColor: Color,
    textSub: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = when {
            isSearching -> "searching"
            isMatched && isSelected -> "matched"
            isMatched && !isSelected -> "skipped"
            else -> "unmatched"
        },
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f)))
                .togetherWith(fadeOut(tween(150)) + scaleOut(spring(dampingRatio = 0.75f, stiffness = 400f)))
                .using(SizeTransform(clip = false))
        },
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        label = "statusCapsule"
    ) { state ->
        when (state) {
            "searching" -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = accentColor,
                            strokeWidth = 1.6.dp
                        )
                        Text(
                            "Matching",
                            color = accentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            "matched" -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF34C759).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Matched",
                            color = Color(0xFF34C759),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            "skipped" -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
                ) {
                    Text(
                        "Skipped",
                        color = textSub,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFF453A).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Unmatched",
                            color = Color(0xFFFF453A),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── Tactile Preview Playback Button ───

@Composable
private fun PreviewPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else if (isPlaying) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "previewBtnScale"
    )
    val buttonBg by animateColorAsState(
        targetValue = if (isPlaying) accentColor.copy(alpha = 0.22f)
        else if (isDarkMode) Color.White.copy(alpha = 0.08f)
        else Color.Black.copy(alpha = 0.06f),
        animationSpec = tween(200),
        label = "previewBtnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) accentColor.copy(alpha = 0.5f)
        else if (isDarkMode) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "previewBtnBorder"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(buttonBg)
            .border(1.dp, borderColor, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = when {
                isLoading -> "loading"
                isPlaying -> "playing"
                else -> "idle"
            },
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn(tween(150)))
                    .togetherWith(scaleOut(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeOut(tween(150)))
            },
            label = "previewIcon"
        ) { state ->
            when (state) {
                "loading" -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                }
                "playing" -> {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause Preview",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Preview",
                        tint = if (isDarkMode) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Alternative Track Row ───

@Composable
private fun AlternativeTrackRow(
    alt: TrackEntity,
    isSelected: Boolean,
    isDarkMode: Boolean,
    textPrimary: Color,
    textSub: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "altRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .background(if (isSelected) SpotifyGreen.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            val artUrl = alt.artworkUrl
            if (artUrl != null) {
                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                    artworkUrl = artUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnailQuality = "Low (Fast)"
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = textSub, modifier = Modifier.size(16.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                alt.title,
                color = textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${alt.artist} • ${if (alt.isVideo) "Video" else "YT Music"}",
                color = textSub,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn())
                    .togetherWith(scaleOut(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeOut())
            },
            label = "altSelectedIcon"
        ) { selected ->
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
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
    onToggleSelect: () -> Unit,
    onPlayPreview: (TrackEntity) -> Unit,
    onRetrySearch: (String) -> Unit,
    onSelectMatch: (TrackEntity) -> Unit,
    onToggleAlternatives: () -> Unit
) {
    val isMatched = matchResult.matchedTrack != null
    var manualQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (matchResult.isSelected || !isMatched) 1f else 0.55f),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(
            1.dp,
            if (isDarkMode) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Top: Spotify original track ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Checkbox for selection
                Checkbox(
                    checked = matchResult.isSelected && isMatched,
                    onCheckedChange = {
                        if (isMatched) {
                            onToggleSelect()
                        } else {
                            showSearchField = true
                            manualQuery = "${matchResult.spotifyTrack.title} ${matchResult.spotifyTrack.artist}"
                            if (matchResult.alternativeResults.isEmpty()) {
                                onRetrySearch(manualQuery)
                            }
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SpotifyGreen,
                        uncheckedColor = if (isMatched) textSub else Color(0xFFFF453A).copy(alpha = 0.5f),
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(24.dp)
                )

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
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
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
                        color = SpotifyGreen.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clean Status Capsule
                MatchStatusCapsule(
                    isSearching = matchResult.isSearching,
                    isMatched = isMatched,
                    isSelected = matchResult.isSelected,
                    isDarkMode = isDarkMode,
                    accentColor = AccentOrange,
                    textSub = textSub
                )
            }

            // ── Bottom: Matched YT track (or "Not Found") ──
            if (!matchResult.isSearching) {
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 2.dp))

                if (isMatched) {
                    val ytCardInteraction = remember { MutableInteractionSource() }
                    val ytCardPressed by ytCardInteraction.collectIsPressedAsState()
                    val ytCardScale by animateFloatAsState(
                        targetValue = if (ytCardPressed) 0.98f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "ytCardScale"
                    )
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (matchResult.showAlternatives) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "arrowRotation"
                    )

                    // Matched YT track card
                    Surface(
                        onClick = { onToggleAlternatives() },
                        interactionSource = ytCardInteraction,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.025f),
                        border = BorderStroke(
                            1.dp,
                            if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = ytCardScale
                                scaleY = ytCardScale
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // YT thumbnail
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val artworkUrl = matchResult.matchedTrack!!.artworkUrl
                                if (artworkUrl != null) {
                                    com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                        artworkUrl = artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        thumbnailQuality = "Low (Fast)"
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val isVid = matchResult.matchedTrack!!.isVideo
                                    Text(if (isVid) "▶" else "♫", color = if (isVid) Color(0xFFFF0000) else Color(0xFFFF0033), fontSize = 10.sp)
                                    Text(if (isVid) "YouTube Video" else "YouTube Music", color = textSub, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
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

                            // Elevated Play Preview Button with spring touch feedback and animated icon
                            PreviewPlayButton(
                                isPlaying = isPlaying,
                                isLoading = isPreviewLoading,
                                accentColor = AccentOrange,
                                isDarkMode = isDarkMode,
                                onClick = { matchResult.matchedTrack?.let { onPlayPreview(it) } }
                            )

                            // Expand/collapse alternatives with animated rotation
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Alternatives",
                                tint = textSub,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = arrowRotation }
                            )
                        }
                    }
                } else {
                    // Not found — show search option
                    val noMatchInteraction = remember { MutableInteractionSource() }
                    val noMatchPressed by noMatchInteraction.collectIsPressedAsState()
                    val noMatchScale by animateFloatAsState(
                        targetValue = if (noMatchPressed) 0.97f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "noMatchScale"
                    )

                    Surface(
                        onClick = {
                            showSearchField = true
                            manualQuery = com.akshay.musicplayer.data.remote.OnlineMusicRepository.cleanSearchQuery(
                                matchResult.spotifyTrack.title,
                                matchResult.spotifyTrack.artist
                            )
                            if (matchResult.alternativeResults.isEmpty()) {
                                onRetrySearch(manualQuery)
                            }
                        },
                        interactionSource = noMatchInteraction,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF453A).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = noMatchScale
                                scaleY = noMatchScale
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No match found", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Tap to search YouTube and match this song", color = textSub, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Search, contentDescription = "Manual Search", tint = AccentOrange, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Manual Search Field ──
            AnimatedVisibility(
                visible = showSearchField || (matchResult.showAlternatives && matchResult.alternativeResults.isNotEmpty()),
                enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) + fadeIn(tween(150)),
                exit = shrinkVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) + fadeOut(tween(150))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Search input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                            .border(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                            val searchBtnInteraction = remember { MutableInteractionSource() }
                            val isSearchBtnPressed by searchBtnInteraction.collectIsPressedAsState()
                            val searchBtnScale by animateFloatAsState(
                                targetValue = if (isSearchBtnPressed) 0.93f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "searchBtnScale"
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = searchBtnScale
                                        scaleY = searchBtnScale
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentOrange)
                                    .clickable(interactionSource = searchBtnInteraction, indication = null) {
                                        onRetrySearch(manualQuery)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("Search", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Alternative results list
                    if (matchResult.alternativeResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkMode) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f),
                            border = BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                matchResult.alternativeResults.forEachIndexed { altIndex, alt ->
                                    val isSelected = matchResult.matchedTrack?.id == alt.id
                                    AlternativeTrackRow(
                                        alt = alt,
                                        isSelected = isSelected,
                                        isDarkMode = isDarkMode,
                                        textPrimary = textPrimary,
                                        textSub = textSub,
                                        onClick = { onSelectMatch(alt) }
                                    )

                                    if (altIndex < matchResult.alternativeResults.size - 1) {
                                        HorizontalDivider(color = dividerColor)
                                    }
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
}
