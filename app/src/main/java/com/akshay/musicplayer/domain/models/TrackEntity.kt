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
        if (lines.isEmpty()) {
            return Triple(null, rawText ?: "No lyrics available", null)
        }
        var activeIdx = lines.indexOfLast { it.timestampMs <= positionMs }
        if (activeIdx < 0) activeIdx = 0
        val prev = if (activeIdx > 0) lines[activeIdx - 1].text else null
        val curr = lines[activeIdx].text
        val next = if (activeIdx < lines.size - 1) lines[activeIdx + 1].text else null
        return Triple(prev, curr, next)
    }
}

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
