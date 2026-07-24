package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F0F0F)
private val AccentOrange = Color(0xFFFF512F)
private val TextSecondary = Color(0xFF8E8E93)

@Composable
fun OnlinePlaylistDetailScreen(
    title: String,
    subtitle: String?,
    gradientColors: List<Color>,
    tracks: List<TrackEntity>,
    isLoading: Boolean,
    isCustomUserPlaylist: Boolean = false,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: ((TrackEntity) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(searchQuery, tracks) {
        if (searchQuery.isBlank()) tracks
        else {
            val q = searchQuery.trim().lowercase()
            tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))
            // Header with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Banner Hero Cover with 2x2 Collage
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        gradientColors.firstOrNull() ?: AccentOrange,
                                        gradientColors.lastOrNull() ?: Color(0xFF1A1A2E),
                                        BgDark
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 2x2 Collage Cover Art
                            com.akshay.musicplayer.ui.components.PlaylistCollageArt(
                                tracks = tracks,
                                modifier = Modifier.size(110.dp),
                                cornerRadius = 14.dp,
                                fallbackGradient = gradientColors
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${tracks.size} tracks",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Action Controls Bar (Play All, Shuffle All)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Big Green/Orange Play All Button
                        Button(
                            onClick = onPlayAllClick,
                            enabled = tracks.isNotEmpty() && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = onShuffleAllClick,
                            enabled = tracks.isNotEmpty() && !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Loading Spinner
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(24.dp))
                                Text("Loading playlist tracks...", color = TextSecondary, fontSize = 14.sp)
                            }
                        }
                    }
                } else if (filteredTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (tracks.isEmpty()) "No tracks in this playlist yet" else "No matching tracks found",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredTracks) { index, track ->
                        OnlineTrackListItem(
                            index = index + 1,
                            totalCount = filteredTracks.size,
                            track = track,
                            isCustomUserPlaylist = isCustomUserPlaylist,
                            onClick = { onTrackClick(index) },
                            onRemove = { onRemoveTrack?.invoke(track) },
                            onMoveUp = { onMoveTrack?.invoke(index, index - 1) },
                            onMoveDown = { onMoveTrack?.invoke(index, index + 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackListItem(
    index: Int,
    totalCount: Int,
    track: TrackEntity,
    isCustomUserPlaylist: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Track number
        Text(
            text = "$index",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp)
        )

        // Thumbnail artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Overflow Options Menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1F1F2E))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Now", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                if (isCustomUserPlaylist) {
                    if (index > 1) {
                        DropdownMenuItem(
                            text = { Text("Move Up", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (index < totalCount) {
                        DropdownMenuItem(
                            text = { Text("Move Down", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist", color = Color(0xFFFF453A)) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}
