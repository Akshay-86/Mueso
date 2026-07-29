package com.akshay.musicplayer.domain.models

data class TrackEntity(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long,
    val filePath: String,
    val artworkUrl: String? = null,
    val lyrics: LyricsData? = null,
    val socialMetrics: SocialMetrics? = null
)

data class LyricsData(
    val lines: List<LyricLine> = emptyList(),
    val rawText: String? = null
) {
    fun getDisplayLines(positionMs: Long): Triple<String?, String, String?> {
        val cleanRaw = if (rawText.isNullOrBlank() || rawText.equals("null", ignoreCase = true)) null else rawText
        if (lines.isEmpty()) {
            if (cleanRaw != null) {
                val rawLines = cleanRaw.lines().map { it.trim() }.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                if (rawLines.isNotEmpty()) {
                    val curr = rawLines.getOrNull(0) ?: ""
                    val next = rawLines.getOrNull(1)
                    return Triple(null, curr, next)
                }
            }
            return Triple(null, cleanRaw ?: "No lyrics available", null)
        }
        var activeIdx = lines.indexOfLast { it.timestampMs <= positionMs }
        if (activeIdx < 0) activeIdx = 0
        val prev = if (activeIdx > 0) lines[activeIdx - 1].text.takeIf { !it.equals("null", ignoreCase = true) } else null
        val curr = lines[activeIdx].text.takeIf { !it.equals("null", ignoreCase = true) } ?: ""
        val next = if (activeIdx < lines.size - 1) lines[activeIdx + 1].text.takeIf { !it.equals("null", ignoreCase = true) } else null
        return Triple(prev, curr, next)
    }
}

data class LrclibSearchResultItem(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Int,
    val isSynced: Boolean,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)

data class SocialMetrics(
    val likeCount: String,
    val commentCount: String,
    val shareCount: String,
    val isFollowed: Boolean = false
)

data class AlbumEntity(
    val id: Long,
    val title: String,
    val artist: String
)
