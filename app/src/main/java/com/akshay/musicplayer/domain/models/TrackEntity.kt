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
    val currentLine: String,
    val nextLine: String?
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
