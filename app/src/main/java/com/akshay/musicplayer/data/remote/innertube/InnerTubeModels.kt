package com.akshay.musicplayer.data.remote.innertube

import com.akshay.musicplayer.domain.models.TrackEntity

data class InnerTubeTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artists: List<InnerTubeArtistRef> = emptyList(),
    val album: String? = null,
    val albumBrowseId: String? = null,
    val durationSec: Int = 0,
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false,
    val itemType: String = "Song"
) {
    fun toTrackEntity(): TrackEntity {
        val durationMs = if (durationSec > 0) durationSec * 1000L else 0L
        val isVid = itemType.equals("Video", ignoreCase = true)
        var hash = -3750763034362895579L
        for (ch in videoId) {
            hash = (hash xor ch.code.toLong()) * 1099511628211L
        }
        val positiveId = (hash and 0x7FFFFFFFFFFFFFFFL).coerceAtLeast(1L)
        return TrackEntity(
            id = positiveId,
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = album ?: if (isVid) "Music Video" else "YouTube Music",
            duration = durationMs,
            albumId = 0L,
            filePath = "online:$videoId",
            artworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg",
            isVideo = isVid,
            albumBrowseId = albumBrowseId
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
    val trackCount: Int = 0,
    val author: String = ""
) {
    val isOfficial: Boolean
        get() = author.contains("YouTube Music", ignoreCase = true) ||
                id.contains("RDCLAK", ignoreCase = true) ||
                subtitle.contains("YouTube Music", ignoreCase = true)
}

data class InnerTubeShelf(
    val title: String,
    val subtitle: String = "",
    val playlists: List<InnerTubePlaylist> = emptyList(),
    val tracks: List<InnerTubeTrack> = emptyList()
)

data class InnerTubeBrowseResult(
    val shelves: List<InnerTubeShelf> = emptyList(),
    val continuationToken: String? = null
)

fun isVideoShelf(title: String, subtitle: String = ""): Boolean {
    val t = title.trim().lowercase()
    val s = subtitle.trim().lowercase()
    val combined = "$t $s"
    return combined.contains("music video") ||
            combined.contains("music videos") ||
            combined.contains("video chart") ||
            combined.contains("video charts") ||
            combined.contains("top video") ||
            combined.contains("top videos") ||
            combined.contains("new video") ||
            combined.contains("new videos") ||
            combined.contains("trending video") ||
            combined.contains("trending videos") ||
            combined.contains("popular episode") ||
            combined.contains("episodes") ||
            t == "videos" ||
            t == "music videos" ||
            t == "video charts" ||
            (t.contains("video") && !t.contains("audio") && !t.contains("song"))
}

data class StreamFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    val contentLength: Long = 0L,
    val isAudioOnly: Boolean = true
)

data class InnerTubeAccountInfo(
    val name: String,
    val handle: String?,
    val avatarUrl: String?
)

data class InnerTubeArtist(
    val id: String,
    val name: String,
    val subscribers: String = "",
    val thumbnailUrl: String? = null
)

data class InnerTubeArtistPage(
    val id: String,
    val name: String,
    val subscribers: String = "",
    val description: String = "",
    val bannerUrl: String? = null,
    val thumbnailUrl: String? = null,
    val radioPlaylistId: String? = null,
    val radioVideoId: String? = null,
    val shufflePlaylistId: String? = null,
    val topSongs: List<InnerTubeTrack> = emptyList(),
    val albums: List<InnerTubePlaylist> = emptyList(),
    val singlesAndEPs: List<InnerTubePlaylist> = emptyList(),
    val videos: List<InnerTubeTrack> = emptyList(),
    val livePerformances: List<InnerTubeTrack> = emptyList(),
    val featuredOn: List<InnerTubePlaylist> = emptyList(),
    val playlistsByArtist: List<InnerTubePlaylist> = emptyList(),
    val similarArtists: List<InnerTubeArtist> = emptyList()
)

