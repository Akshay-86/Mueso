package com.akshay.musicplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.remote.innertube.InnerTubeArtist
import com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.SmartArtworkImage
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val AccentOrange = Color(0xFFFF512F)
private val DarkBg = Color(0xFF0F0F13)
private val SurfaceDark = Color(0xFF1E1E2E)
private val TextMuted = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistPage: InnerTubeArtistPage?,
    isLoading: Boolean,
    isDarkMode: Boolean = true,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onArtistClick: (InnerTubeArtist) -> Unit,
    onPlaylistClick: (SelectedOnlinePlaylist) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSubColor = if (isDarkMode) TextMuted else Color(0xFF6E6E73)
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    var isBioExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) DarkBg else Color(0xFFF6F6F9))
    ) {
        if (artistPage == null && isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentOrange, strokeWidth = 3.dp)
                    Text(
                        "Loading artist details...",
                        color = textSubColor,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (artistPage != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 1. Hero Header Section
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        val banner = artistPage.bannerUrl ?: artistPage.thumbnailUrl
                        if (!banner.isNullOrBlank()) {
                            SmartArtworkImage(
                                artworkUrl = banner,
                                contentDescription = artistPage.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                thumbnailQuality = "High"
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                        )
                                    )
                            )
                        }

                        // Gradient Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            if (isDarkMode) DarkBg.copy(alpha = 0.85f) else Color(0xFFF6F6F9).copy(alpha = 0.85f),
                                            if (isDarkMode) DarkBg else Color(0xFFF6F6F9)
                                        ),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )

                        // Top Back Button
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Hero Info (Name, Audience, Action Buttons)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = artistPage.name,
                                color = textColor,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (artistPage.subscribers.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = artistPage.subscribers,
                                    color = textSubColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (artistPage.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artistPage.description,
                                    color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF3C3C43),
                                    fontSize = 13.sp,
                                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                                )
                                Text(
                                    text = if (isBioExpanded) "Show less" else "Read more",
                                    color = AccentOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { isBioExpanded = !isBioExpanded }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons (Shuffle & Radio)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.playArtistRadio(artistPage)
                                        onNavigateToPlayer()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Shuffle",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.playArtistRadio(artistPage)
                                        onNavigateToPlayer()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = textColor
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = "Radio",
                                        modifier = Modifier.size(18.dp),
                                        tint = AccentOrange
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Radio Mix",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Top Songs Section
                if (artistPage.topSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader(
                            title = "Top songs",
                            textColor = textColor,
                            actionText = if (artistPage.topSongs.size > 5) "Play all" else null,
                            onAction = {
                                viewModel.playArtistTopSongs(artistPage.topSongs, startIndex = 0)
                                onNavigateToPlayer()
                            }
                        )
                    }

                    itemsIndexed(artistPage.topSongs) { index, track ->
                        ArtistTrackRow(
                            index = index + 1,
                            track = track,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            onClick = {
                                viewModel.playArtistTopSongs(artistPage.topSongs, startIndex = index)
                                onNavigateToPlayer()
                            }
                        )
                    }
                }

                // 3. Albums Shelf
                if (artistPage.albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Albums", textColor = textColor)
                        PlaylistShelfRow(
                            playlists = artistPage.albums,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            cardBg = cardBg,
                            onPlaylistClick = { pl ->
                                onPlaylistClick(
                                    SelectedOnlinePlaylist(
                                        id = pl.id,
                                        title = pl.title,
                                        subtitle = pl.subtitle.ifBlank { artistPage.name },
                                        artworkUrl = pl.artworkUrl
                                    )
                                )
                            }
                        )
                    }
                }

                // 4. Singles & EPs Shelf
                if (artistPage.singlesAndEPs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Singles & EPs", textColor = textColor)
                        PlaylistShelfRow(
                            playlists = artistPage.singlesAndEPs,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            cardBg = cardBg,
                            onPlaylistClick = { pl ->
                                onPlaylistClick(
                                    SelectedOnlinePlaylist(
                                        id = pl.id,
                                        title = pl.title,
                                        subtitle = pl.subtitle.ifBlank { artistPage.name },
                                        artworkUrl = pl.artworkUrl
                                    )
                                )
                            }
                        )
                    }
                }

                // 5. Videos Shelf
                if (artistPage.videos.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Videos", textColor = textColor)
                        VideoShelfRow(
                            videos = artistPage.videos,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            cardBg = cardBg,
                            onVideoClick = { vid ->
                                viewModel.playTrack(vid.toTrackEntity())
                                onNavigateToPlayer()
                            }
                        )
                    }
                }

                // 6. Live Performances Shelf
                if (artistPage.livePerformances.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Live Performances", textColor = textColor)
                        VideoShelfRow(
                            videos = artistPage.livePerformances,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            cardBg = cardBg,
                            onVideoClick = { vid ->
                                viewModel.playTrack(vid.toTrackEntity())
                                onNavigateToPlayer()
                            }
                        )
                    }
                }

                // 7. Featured On & Playlists
                val allPlaylists = (artistPage.featuredOn + artistPage.playlistsByArtist).distinctBy { it.id }
                if (allPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Featured On & Playlists", textColor = textColor)
                        PlaylistShelfRow(
                            playlists = allPlaylists,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            cardBg = cardBg,
                            onPlaylistClick = { pl ->
                                onPlaylistClick(
                                    SelectedOnlinePlaylist(
                                        id = pl.id,
                                        title = pl.title,
                                        subtitle = pl.subtitle.ifBlank { artistPage.name },
                                        artworkUrl = pl.artworkUrl
                                    )
                                )
                            }
                        )
                    }
                }

                // 8. Fans Might Also Like (Similar Artists)
                if (artistPage.similarArtists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Fans might also like", textColor = textColor)
                        SimilarArtistsShelfRow(
                            artists = artistPage.similarArtists,
                            textColor = textColor,
                            textSubColor = textSubColor,
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    textColor: Color,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                color = AccentOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ArtistTrackRow(
    index: Int,
    track: InnerTubeTrack,
    textColor: Color,
    textSubColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            color = textSubColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                SmartArtworkImage(
                    artworkUrl = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    thumbnailQuality = "Low (Fast)"
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subText = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                track.album?.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (subText.isNotBlank()) {
                Text(
                    text = subText,
                    color = textSubColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (track.durationSec > 0) {
            val mins = track.durationSec / 60
            val secs = track.durationSec % 60
            val durString = "$mins:${secs.toString().padStart(2, '0')}"
            Text(
                text = durString,
                color = textSubColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaylistShelfRow(
    playlists: List<InnerTubePlaylist>,
    textColor: Color,
    textSubColor: Color,
    cardBg: Color,
    onPlaylistClick: (InnerTubePlaylist) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(playlists) { pl ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlaylistClick(pl) }
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                ) {
                    if (!pl.artworkUrl.isNullOrBlank()) {
                        SmartArtworkImage(
                            artworkUrl = pl.artworkUrl,
                            contentDescription = pl.title,
                            modifier = Modifier.fillMaxSize(),
                            thumbnailQuality = "Medium"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pl.title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (pl.subtitle.isNotBlank()) {
                    Text(
                        text = pl.subtitle,
                        color = textSubColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoShelfRow(
    videos: List<InnerTubeTrack>,
    textColor: Color,
    textSubColor: Color,
    cardBg: Color,
    onVideoClick: (InnerTubeTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(videos) { vid ->
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onVideoClick(vid) }
            ) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (!vid.artworkUrl.isNullOrBlank()) {
                        SmartArtworkImage(
                            artworkUrl = vid.artworkUrl,
                            contentDescription = vid.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = "Medium"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = vid.title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SimilarArtistsShelfRow(
    artists: List<InnerTubeArtist>,
    textColor: Color,
    textSubColor: Color,
    onArtistClick: (InnerTubeArtist) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(artists) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onArtistClick(artist) }
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artist.thumbnailUrl.isNullOrBlank()) {
                        SmartArtworkImage(
                            artworkUrl = artist.thumbnailUrl,
                            contentDescription = artist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            thumbnailQuality = "Medium"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = textSubColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (artist.subscribers.isNotBlank()) {
                    Text(
                        text = artist.subscribers,
                        color = textSubColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
