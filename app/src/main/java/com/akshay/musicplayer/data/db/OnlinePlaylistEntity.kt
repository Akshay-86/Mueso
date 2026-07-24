package com.akshay.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "online_playlists")
data class OnlinePlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val dateCreated: Long = System.currentTimeMillis()
)
