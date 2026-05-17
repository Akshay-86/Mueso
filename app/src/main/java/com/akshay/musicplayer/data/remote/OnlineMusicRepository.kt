package com.akshay.musicplayer.data.remote

import com.akshay.musicplayer.domain.models.TrackEntity

class OnlineMusicRepository(private val apiService: VeromeApiService) {

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> {
        return try {
            val response = apiService.getTrending(country)
            if (response.success) {
                response.tracks.map { track ->
                    TrackEntity(
                        id = track.videoId.hashCode().toLong(), // Generate a mock local ID
                        title = track.name,
                        artist = track.artist,
                        album = "Trending Online",
                        duration = 0L, // Online stream might not have duration upfront
                        albumId = 0L,
                        filePath = "https://verome-api.deno.dev/api/stream?id=${track.videoId}",
                        artworkUrl = track.thumbnail,
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
}
