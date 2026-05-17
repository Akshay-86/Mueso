package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.theme.DarkColors
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val tracks by viewModel.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList<TrackEntity>())

    androidx.activity.compose.BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F))
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Play All Button
            if (tracks.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.playQueue(tracks)
                        onNavigateToPlayer()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkColors.Primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play All", color = Color.White)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This playlist is empty.", color = Color.Gray)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    PlaylistTrackItem(
                        track = track,
                        index = index,
                        totalTracks = tracks.size,
                        onClick = {
                            viewModel.playQueue(tracks, index)
                            onNavigateToPlayer()
                        },
                        onRemove = { viewModel.removeTrackFromPlaylist(playlist.id, track.id) },
                        onMoveUp = { viewModel.moveTrackInPlaylist(playlist.id, index, index - 1) },
                        onMoveDown = { viewModel.moveTrackInPlaylist(playlist.id, index, index + 1) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackItem(
    track: TrackEntity,
    index: Int,
    totalTracks: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (index > 0) {
                    DropdownMenuItem(
                        text = { Text("Move Up") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                        onClick = {
                            expanded = false
                            onMoveUp()
                        }
                    )
                }
                if (index < totalTracks - 1) {
                    DropdownMenuItem(
                        text = { Text("Move Down") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                        onClick = {
                            expanded = false
                            onMoveDown()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from Playlist") },
                    onClick = {
                        expanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}
