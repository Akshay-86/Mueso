package com.akshay.musicplayer.domain.models

data class TrackEntity(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long,
    val filePath: String
)

data class AlbumEntity(
    val id: Long,
    val title: String,
    val artist: String
)
