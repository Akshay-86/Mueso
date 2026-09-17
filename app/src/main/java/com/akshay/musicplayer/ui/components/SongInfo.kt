package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun SongInfo(
    track: TrackEntity,
    onArtistClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isExpressive = com.akshay.musicplayer.ui.theme.LocalIsExpressive.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = track.title,
            style = if (isExpressive) {
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
            } else {
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            },
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = track.artist,
            fontSize = if (isExpressive) 17.sp else 16.sp,
            fontWeight = if (isExpressive) FontWeight.Medium else FontWeight.Normal,
            color = Color.White.copy(alpha = if (isExpressive) 0.85f else 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onArtistClick() }
                .padding(vertical = 2.dp)
        )
    }
}
