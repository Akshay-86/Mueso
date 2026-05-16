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

@Composable
fun LyricsPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Active/Current Lyric - Slightly smaller for better balance
        Text(
            text = "You were the shadow\nto my light",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Next/Upcoming Lyric
        Text(
            text = "Did you feel us",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp
            ),
            color = Color.White.copy(alpha = 0.35f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
