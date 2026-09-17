package com.akshay.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

val LocalDesignSystem = androidx.compose.runtime.compositionLocalOf { "classic" }
val LocalIsExpressive = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun MusicPlayerTheme(
    themeMode: String = "dark",
    usePureBlack: Boolean = false,
    accentColorId: String = "sunset_orange",
    fontScaleOption: String = "standard",
    cornerRadiusOption: String = "rounded",
    lyricsFontSizeOption: String = "standard",
    designSystem: String = "classic",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val mode = ThemeMode.fromId(themeMode)
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val isDynamic = accentColorId.equals("dynamic", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val palette = ThemePresets.getPalette(accentColorId)

    val primaryColor = if (isDynamic) {
        if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        palette.primary
    }

    val secondaryColor = if (isDynamic) {
        if (isDark) dynamicDarkColorScheme(context).secondary else dynamicLightColorScheme(context).secondary
    } else {
        palette.secondary
    }

    val accentGradient = if (isDynamic) {
        Brush.horizontalGradient(listOf(primaryColor, secondaryColor))
    } else {
        palette.gradient
    }

    val isPureBlackActive = isDark && usePureBlack

    val colorScheme = if (isDark) {
        val bg = if (isPureBlackActive) Color(0xFF000000) else Color(0xFF0F0F0F)
        val surf = if (isPureBlackActive) Color(0xFF0D0D0D) else Color(0xFF1A1A2E)
        val tert = if (isPureBlackActive) Color(0xFF141414) else Color(0xFF1E1E2E)
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = tert,
            background = bg,
            surface = surf,
            error = Color(0xFFFF453A),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
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
    }

    val currentDensity = LocalDensity.current
    val fontScaleFactor = FontScaleOption.fromId(fontScaleOption).scale
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScaleFactor
    )

    val cornerRadius = CornerRadiusOption.fromId(cornerRadiusOption)
    val lyricsFontSize = LyricsFontSizeOption.fromId(lyricsFontSizeOption)
    val isExpressive = designSystem.equals("expressive", ignoreCase = true)

    val appShapes = if (isExpressive) {
        androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    } else {
        when (cornerRadius) {
            CornerRadiusOption.ROUNDED -> androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            )
            CornerRadiusOption.SQUIRCLE -> androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
            )
            CornerRadiusOption.SHARP -> androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
        }
    }

    CompositionLocalProvider(
        LocalAccentColor provides primaryColor,
        LocalAccentGradient provides accentGradient,
        LocalIsPureBlack provides isPureBlackActive,
        LocalCornerRadius provides cornerRadius,
        LocalLyricsFontSize provides lyricsFontSize,
        LocalDensity provides scaledDensity,
        LocalDesignSystem provides designSystem,
        LocalIsExpressive provides isExpressive
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = appShapes,
            content = content
        )
    }
}

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MusicPlayerTheme(
        themeMode = if (darkTheme) "dark" else "light",
        content = content
    )
}
