package com.akshay.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF512F),
    secondary = Color(0xFFDD2476),
    tertiary = Color(0xFF1E1E2E),
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A2E),
    error = Color(0xFFFF453A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF512F),
    secondary = Color(0xFFDD2476),
    tertiary = Color(0xFFE5E5EA),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFFF3B30),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF1C1C1E),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
