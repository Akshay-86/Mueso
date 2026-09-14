package com.akshay.musicplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Follow System"),
    DARK("dark", "Dark Mode"),
    LIGHT("light", "Light Mode");

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DARK
    }
}

enum class FontScaleOption(val id: String, val label: String, val scale: Float) {
    COMPACT("compact", "Compact (88%)", 0.88f),
    STANDARD("standard", "Standard (100%)", 1.0f),
    COMFORTABLE("comfortable", "Comfortable (112%)", 1.12f),
    LARGE("large", "Large (125%)", 1.25f);

    companion object {
        fun fromId(id: String): FontScaleOption = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD
    }
}

enum class CornerRadiusOption(val id: String, val label: String, val radiusDp: Dp) {
    ROUNDED("rounded", "Rounded", 16.dp),
    SQUIRCLE("squircle", "Squircle", 10.dp),
    SHARP("sharp", "Sharp", 4.dp);

    companion object {
        fun fromId(id: String): CornerRadiusOption = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ROUNDED
    }
}

enum class LyricsFontSizeOption(val id: String, val label: String, val activeSp: Float, val inactiveSp: Float) {
    COMPACT("compact", "Compact", 22f, 15f),
    STANDARD("standard", "Standard", 26f, 17f),
    LARGE("large", "Large", 32f, 21f);

    companion object {
        fun fromId(id: String): LyricsFontSizeOption = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD
    }
}

data class AccentPalette(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val isDynamic: Boolean = false
) {
    val gradient: Brush
        get() = Brush.horizontalGradient(listOf(primary, secondary))
}

object ThemePresets {
    val SunsetOrange = AccentPalette("sunset_orange", "Sunset Orange", Color(0xFFFF512F), Color(0xFFDD2476))
    val SpotifyGreen = AccentPalette("spotify_green", "Spotify Green", Color(0xFF1DB954), Color(0xFF108639))
    val ElectricViolet = AccentPalette("electric_violet", "Electric Violet", Color(0xFF8A2387), Color(0xFFE94057))
    val NeonCyan = AccentPalette("neon_cyan", "Neon Cyan", Color(0xFF00C9FF), Color(0xFF92FE9D))
    val CrimsonRed = AccentPalette("crimson_red", "Crimson Red", Color(0xFFFF416C), Color(0xFFFF4B2B))
    val OceanBlue = AccentPalette("ocean_blue", "Ocean Blue", Color(0xFF2193B0), Color(0xFF6DD5ED))
    val AmberGold = AccentPalette("amber_gold", "Amber Gold", Color(0xFFF7971E), Color(0xFFFFD200))
    val RosePink = AccentPalette("rose_pink", "Rose Pink", Color(0xFFFF758C), Color(0xFFFF7EB3))
    val EmeraldMint = AccentPalette("emerald_mint", "Emerald Mint", Color(0xFF11998E), Color(0xFF38EF7D))

    val AllPalettes = listOf(
        SunsetOrange,
        SpotifyGreen,
        ElectricViolet,
        NeonCyan,
        CrimsonRed,
        OceanBlue,
        AmberGold,
        RosePink,
        EmeraldMint
    )

    fun getPalette(id: String): AccentPalette {
        if (id.startsWith("custom_")) {
            val hex = id.removePrefix("custom_").removePrefix("#")
            try {
                val fullHex = if (hex.length == 6) "FF$hex" else hex
                val colorInt = fullHex.toLong(16).toInt()
                val primary = Color(colorInt)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(colorInt, hsv)
                hsv[0] = (hsv[0] + 25f) % 360f
                val secondary = Color(android.graphics.Color.HSVToColor(hsv))
                return AccentPalette(id, "Custom (#${hex.take(6).uppercase()})", primary, secondary)
            } catch (_: Exception) {}
        }
        return AllPalettes.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SunsetOrange
    }
}

val LocalAccentColor = staticCompositionLocalOf { Color(0xFFFF512F) }
val LocalAccentGradient = staticCompositionLocalOf {
    Brush.horizontalGradient(listOf(Color(0xFFFF512F), Color(0xFFDD2476)))
}
val LocalIsPureBlack = staticCompositionLocalOf { false }
val LocalCornerRadius = staticCompositionLocalOf { CornerRadiusOption.ROUNDED }
val LocalLyricsFontSize = staticCompositionLocalOf { LyricsFontSizeOption.STANDARD }
