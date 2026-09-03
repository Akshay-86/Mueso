package com.akshay.musicplayer.data.remote.innertube

import com.akshay.musicplayer.domain.models.TrackEntity

data class InnerTubeTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artists: List<InnerTubeArtistRef> = emptyList(),
    val album: String? = null,
    val durationSec: Int = 0,
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false
) {
    fun toTrackEntity(): TrackEntity {
        val durationMs = if (durationSec > 0) durationSec * 1000L else 0L
        return TrackEntity(
            id = videoId.hashCode().toLong(),
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = album ?: "YouTube Music",
            duration = durationMs,
            albumId = 0L,
            filePath = "online:$videoId",
            artworkUrl = artworkUrl
        )
    }
}

data class InnerTubeArtistRef(
    val id: String,
    val name: String
)

data class InnerTubePlaylist(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int = 0
)

data class InnerTubeShelf(
    val title: String,
    val subtitle: String = "",
    val playlists: List<InnerTubePlaylist> = emptyList(),
    val tracks: List<InnerTubeTrack> = emptyList()
)

data class StreamFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    val contentLength: Long = 0L,
    val isAudioOnly: Boolean = true
)
