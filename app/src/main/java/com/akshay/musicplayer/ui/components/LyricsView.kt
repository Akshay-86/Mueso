package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.LyricsData

@Composable
fun LyricsView(
    lyrics: LyricsData?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Active/Current Lyric
        Text(
            text = lyrics?.currentLine ?: "Searching for lyrics...",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White.copy(alpha = if (lyrics == null) 0.3f else 1f),
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Next/Upcoming Lyric
        if (lyrics?.nextLine != null || lyrics == null) {
            Text(
                text = lyrics?.nextLine ?: "...",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp
                ),
                color = Color.White.copy(alpha = 0.2f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
