package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AccentOrange = Color(0xFFFF512F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    track: TrackEntity,
    audioFolder: String = "Music/Mueso",
    videoFolder: String = "Movies/Mueso",
    isDarkMode: Boolean = true,
    onFetchResolutions: (suspend (TrackEntity) -> List<String>)? = null,
    onDownloadAudio: () -> Unit,
    onDownloadVideo: (resolution: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val sheetBg = if (isDarkMode) Color(0xFF161622) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.55f) else Color(0xFF6E6E73)
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    var liveResolutions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingResolutions by remember { mutableStateOf(onFetchResolutions != null) }

    LaunchedEffect(track.id) {
        if (onFetchResolutions != null) {
            isLoadingResolutions = true
            val fetched = try {
                withContext(Dispatchers.IO) {
                    onFetchResolutions(track)
                }
            } catch (_: Exception) {
                emptyList()
            }
            liveResolutions = fetched.ifEmpty {
                listOf(
                    "1080p (Full HD)",
                    "720p (HD)",
                    "480p (SD)",
                    "360p"
                )
            }
            isLoadingResolutions = false
        } else {
            liveResolutions = listOf(
                "1080p (Full HD)",
                "720p (HD)",
                "480p (SD)",
                "360p"
            )
            isLoadingResolutions = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with Track Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "Track Artwork",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Options",
                        color = AccentOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(bottom = 12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // 1. Audio (Song) Option
                item {
                    Text(
                        text = "AUDIO",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    DownloadOptionItem(
                        icon = Icons.Default.MusicNote,
                        title = "Download Song (Audio)",
                        subtitle = "High Quality MP3 / M4A • Saves to $audioFolder",
                        badge = "MP3",
                        badgeColor = Color(0xFF34C759),
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = {
                            onDownloadAudio()
                            onDismiss()
                        }
                    )
                }

                // 2. Video Header
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VIDEO (MP4)",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        if (isLoadingResolutions) {
                            Text(
                                text = "Detecting qualities...",
                                color = AccentOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Loading Indicator Card
                if (isLoadingResolutions) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentOrange
                                )
                                Column {
                                    Text(
                                        text = "Querying Live Video Streams...",
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Detecting 8K, 4K, 2K, 1080p video qualities",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 3. Dynamic Live Video Resolutions
                    items(liveResolutions) { resItem ->
                        val (badgeText, badgeColor, subText) = getResolutionBadgeDetails(resItem)
                        DownloadOptionItem(
                            icon = Icons.Default.Videocam,
                            title = "$resItem Video",
                            subtitle = subText,
                            badge = badgeText,
                            badgeColor = badgeColor,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                onDownloadVideo(resItem)
                                onDismiss()
                            }
                        )
                    }
                }

                // Storage location footer note
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Videos save to $videoFolder and show in Gallery.",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getResolutionBadgeDetails(resItem: String): Triple<String, Color, String> {
    val low = resItem.lowercase()
    return when {
        low.contains("4320") || low.contains("8k") ->
            Triple("8K", Color(0xFFFF2D55), "Ultra HD Clarity • Highest available")
        low.contains("2160") || low.contains("4k") ->
            Triple("4K", Color(0xFFFF512F), "4K UHD Quality • Incredible detail")
        low.contains("1440") || low.contains("2k") || low.contains("qhd") ->
            Triple("2K", Color(0xFFFF9500), "Quad HD 1440p • Crisp visuals")
        low.contains("1080") || low.contains("fhd") || low.contains("full") ->
            Triple("1080p", Color(0xFFFF9F0A), "Full HD 1080p • Crystal clear")
        low.contains("720") || low.contains("hd") ->
            Triple("720p", Color(0xFF34C759), "Standard HD • Great size & quality")
        low.contains("480") || low.contains("sd") ->
            Triple("480p", Color(0xFF007AFF), "SD Resolution • Fast download")
        low.contains("360") ->
            Triple("360p", Color(0xFF5856D6), "Compact Size • Saves mobile data")
        low.contains("240") ->
            Triple("240p", Color(0xFFAF52DE), "Lightweight • Minimal storage")
        else ->
            Triple("Video", Color(0xFF8E8E93), "Standard Video Stream")
    }
}

@Composable
private fun DownloadOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.2f),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(
                text = badge,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}
