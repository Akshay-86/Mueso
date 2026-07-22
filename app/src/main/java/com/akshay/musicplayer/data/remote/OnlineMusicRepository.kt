package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.domain.models.TrackEntity

class OnlineMusicRepository(private val apiService: VeromeApiService) {

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> {
        return try {
            val response = apiService.getTrending(country)
            if (response.success) {
                response.tracks.map { track ->
                    val highResThumb = track.thumbnail?.replace("w60-h60", "w500-h500")?.replace("s88", "s500")
                    TrackEntity(
                        id = track.videoId.hashCode().toLong(), // Generate a mock local ID
                        title = track.name,
                        artist = track.artist,
                        album = "Trending Online",
                        duration = 0L, // Online stream might not have duration upfront
                        albumId = 0L,
                        filePath = "online:${track.videoId}",
                        artworkUrl = highResThumb,
                        lyrics = null,
                        socialMetrics = null
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String? {
        return try {
            val response = apiService.getStream(videoId)
            val formats = response.streamingUrls ?: response.formats
            val format = formats?.firstOrNull { it.url != null || it.directUrl != null }
            val rawUrl = format?.url ?: format?.directUrl
            
            val streamUrl = if (rawUrl != null) {
                if (rawUrl.contains("latest_version") && !rawUrl.contains("local=true")) {
                    if (rawUrl.contains("?")) "$rawUrl&local=true" else "$rawUrl?local=true"
                } else {
                    rawUrl
                }
            } else {
                "https://yt.omada.cafe/latest_version?id=$videoId&itag=140&local=true"
            }

            Log.d("MUESO_STREAM", "Fetched stream URL for $videoId: $streamUrl")
            streamUrl
        } catch (e: Exception) {
            Log.e("MUESO_STREAM", "Error fetching stream URL for $videoId", e)
            "https://yt.omada.cafe/latest_version?id=$videoId&itag=140&local=true"
        }
    }

    suspend fun searchOnlineTracks(query: String): List<TrackEntity> {
        if (query.isBlank()) return emptyList()
        return try {
            val response = apiService.searchSongs(query)
            response.results?.filter { !it.videoId.isNullOrEmpty() }?.map { result ->
                val videoId = result.videoId!!
                val artistName = result.artists?.firstOrNull()?.name ?: "Unknown Artist"
                val rawThumb = result.thumbnails?.lastOrNull()?.url ?: result.thumbnails?.firstOrNull()?.url
                val highResThumb = rawThumb?.replace("w60-h60", "w500-h500")?.replace("s88", "s500")
                TrackEntity(
                    id = videoId.hashCode().toLong(),
                    title = result.title ?: "Unknown Title",
                    artist = artistName,
                    album = "Online Search",
                    duration = 0L,
                    albumId = 0L,
                    filePath = "online:$videoId",
                    artworkUrl = highResThumb,
                    lyrics = null,
                    socialMetrics = null
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MUESO_SEARCH", "Error searching online tracks for query: $query", e)
            emptyList()
        }
    }

}


