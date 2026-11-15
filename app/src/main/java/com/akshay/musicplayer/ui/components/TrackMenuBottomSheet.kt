package com.akshay.musicplayer.ui.components

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.theme.LocalAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuBottomSheet(
    track: TrackEntity,
    isDarkMode: Boolean = true,
    activePlaylistInfo: com.akshay.musicplayer.ui.screens.SelectedOnlinePlaylist? = null,
    isPlaylistContext: Boolean = false,
    onGoToArtist: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onShowEqualizer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = LocalAccentColor.current
    val sheetBg = if (isDarkMode) Color(0xFF14141E) else Color(0xFFF9F9FB)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Track Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val menuArtUri = remember(track.id, track.artworkUrl, track.albumId, track.filePath) {
                    when {
                        !track.artworkUrl.isNullOrBlank() -> track.artworkUrl
                        track.id > 0L && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http") ->
                            "content://media/external/audio/media/${track.id}"
                        track.filePath.isNotBlank() && !track.filePath.startsWith("online:") -> track.filePath
                        track.albumId > 0L -> "content://media/external/audio/albumart/${track.albumId}"
                        else -> null
                    }
                }
                SmartArtworkImage(
                    artworkUrl = menuArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    thumbnailQuality = "Medium (480p)",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDarkMode) Color.DarkGray else Color.LightGray)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(2.dp))

            // Semantic Action Items with Category Tinting
            // 1. Library & Playlists (Accent)
            TrackMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to Playlist",
                subtitle = "Save to user or offline collection",
                tint = accent,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            // 2. Audio & DSP (Purple/Lavender)
            TrackMenuItem(
                icon = Icons.Default.GraphicEq,
                title = "Equalizer & DSP",
                subtitle = "Bass boost, frequency bands & studio clarity",
                tint = Color(0xFFAB47BC),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {
                    onDismiss()
                    onShowEqualizer()
                }
            )

            // 3. Explore & Navigation (Teal)
            TrackMenuItem(
                icon = Icons.Default.Person,
                title = "Go to Artist",
                subtitle = track.artist,
                tint = Color(0xFF26A69A),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {
                    onDismiss()
                    onGoToArtist()
                }
            )

            val hasValidAlbum = !track.album.isNullOrBlank() &&
                    track.album != "<unknown>" &&
                    track.album != "YouTube Music" &&
                    track.album != "Music Video" &&
                    track.album != "Curated Playlist"
            val hasDirectAlbum = !track.albumBrowseId.isNullOrBlank()
            val isPlayingFromAlbumOrPlaylist = activePlaylistInfo != null || isPlaylistContext
            val showGoToAlbum = hasDirectAlbum || isPlayingFromAlbumOrPlaylist || hasValidAlbum

            if (showGoToAlbum) {
                val albumTitle = when {
                    hasValidAlbum -> track.album
                    activePlaylistInfo != null -> activePlaylistInfo.title
                    else -> "Current Album"
                }
                TrackMenuItem(
                    icon = Icons.Default.Album,
                    title = "Go to Album",
                    subtitle = albumTitle,
                    tint = Color(0xFF42A5F5),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        onDismiss()
                        onGoToAlbum()
                    }
                )
            }

            // 4. Sharing & Social
            TrackMenuItem(
                icon = Icons.Default.Share,
                title = "Share Track",
                subtitle = "Share song details with friends",
                tint = Color(0xFFFFA726),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {
                    onDismiss()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listening to \"${track.title}\" by ${track.artist} on Mueso")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                }
            )
        }
    }
}

@Composable
private fun TrackMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 400f
        ),
        label = "TrackMenuItemScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
