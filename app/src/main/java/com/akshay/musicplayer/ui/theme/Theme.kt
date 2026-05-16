package com.akshay.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.Primary,
    secondary = DarkColors.Secondary,
    tertiary = DarkColors.Tertiary,
    background = DarkColors.Background,
    surface = DarkColors.Surface,
    error = DarkColors.Error,
    onPrimary = DarkColors.OnPrimary,
    onSecondary = DarkColors.OnSecondary,
    onTertiary = DarkColors.OnTertiary,
    onBackground = DarkColors.OnBackground,
    onSurface = DarkColors.OnSurface,
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
