package com.akshay.musicplayer.data.models

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long,
    val data: String,
    val dateModified: Long
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String
)
