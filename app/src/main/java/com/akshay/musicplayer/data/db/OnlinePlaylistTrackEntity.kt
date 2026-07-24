package com.akshay.musicplayer.data.db

import androidx.room.Entity

@Entity(
    tableName = "online_playlist_tracks",
    primaryKeys = ["onlinePlaylistId", "trackId"]
)
data class OnlinePlaylistTrackEntity(
    val onlinePlaylistId: Long,
    val trackId: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val filePath: String,
    val duration: Long = 0L,
    val orderIndex: Int = 0
)
