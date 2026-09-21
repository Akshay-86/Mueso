package com.akshay.musicplayer.data.remote.innertube

import java.text.Normalizer
import java.util.Locale

/**
 * Script-agnostic title/artist matching and filtering logic derived from LastWave.
 * Supports non-Latin scripts (CJK, Cyrillic, Indic/Devanagari, Arabic, Telugu, Tamil, etc.)
 * with Unicode-aware normalization and Root locale.
 */
object TextMatch {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val DIACRITICS = Regex("\\p{M}+")
    private val MULTI_SPACE = Regex("\\s+")

    val VARIANT_WORDS = setOf(
        "live", "remix", "karaoke", "cover", "instrumental", "slowed", "sped", "nightcore",
        "acoustic", "demo", "edit", "remaster", "remastered", "mono", "stereo"
    )

    val MATCH_NOISE_WORDS = setOf(
        "official", "audio", "video", "visualizer", "lyrics", "lyric", "full", "hd", "4k", "music"
    )

    private val FEATURING_CLAUSE = Regex("(?i)[(\\[]\\s*(feat(?:uring)?|ft)\\.?\\s+.*?[)\\]]")
    private val VERSION_CLAUSE =
        Regex("(?i)[(\\[][^)\\]]*(live|remix|acoustic|demo|edit|remaster(?:ed)?|mono|stereo)[^)\\]]*[)\\]]")

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_WORD, " ")
            .trim()
            .replace(MULTI_SPACE, " ")

    fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .filter { it.isNotBlank() && it !in MATCH_NOISE_WORDS }
        .toSet()

    fun baseTitle(value: String): String = value
        .replace(FEATURING_CLAUSE, " ")
        .replace(VERSION_CLAUSE, " ")

    fun similarity(a: String, b: String): Int {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA == normB) return 100
        if (normA.isNotBlank() && normB.isNotBlank()) {
            if (normA.contains(normB) || normB.contains(normA)) {
                val ratio = (minOf(normA.length, normB.length) * 100) / maxOf(normA.length, normB.length)
                if (ratio >= 45) return maxOf(85, ratio)
            }
        }
        val left = tokens(a)
        val right = tokens(b)
        if (left.isEmpty() || right.isEmpty()) return 0
        val common = left.intersect(right).size
        val dice = (200 * common) / (left.size + right.size)
        val subset = if (common == minOf(left.size, right.size) && common > 0) 80 else 0
        return maxOf(dice, subset)
    }

    fun isUsefulDetail(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotBlank() && trimmed !in setOf("•", "·", "|", "Song", "Video", "Track")
    }

    fun isLikelyArtistDetail(value: String): Boolean {
        val v = value.trim()
        if (!isUsefulDetail(v)) return false
        if (v.equals("Album", ignoreCase = true) ||
            v.equals("Single", ignoreCase = true) ||
            v.equals("EP", ignoreCase = true) ||
            v.equals("Playlist", ignoreCase = true) ||
            v.equals("Artist", ignoreCase = true)
        ) return false

        // Check if duration timestamp pattern (e.g. 3:45 or 1:02:15)
        if (v.matches(Regex("^\\d+:\\d+(:\\d+)?$"))) return false

        // Check if release year (e.g. 1999, 2024)
        if (v.matches(Regex("^(19|20)\\d{2}$"))) return false

        // Check if view / play counts
        if (v.contains(" view", ignoreCase = true) ||
            v.contains(" song", ignoreCase = true) ||
            v.contains(" play", ignoreCase = true) ||
            v.contains(" subscriber", ignoreCase = true)
        ) return false

        return true
    }
}
