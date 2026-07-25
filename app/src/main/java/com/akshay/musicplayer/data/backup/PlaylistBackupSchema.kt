package com.akshay.musicplayer.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MuesoBackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceInfo: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    val onlinePlaylists: List<BackupOnlinePlaylist> = emptyList(),
    val localPlaylists: List<BackupLocalPlaylist> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupOnlinePlaylist(
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val tracks: List<BackupTrack> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupLocalPlaylist(
    val name: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val tracks: List<BackupLocalTrackInfo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupLocalTrackInfo(
    val title: String,
    val artist: String,
    val orderIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class BackupTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val filePath: String,
    val duration: Long = 0L,
    val orderIndex: Int = 0
)
