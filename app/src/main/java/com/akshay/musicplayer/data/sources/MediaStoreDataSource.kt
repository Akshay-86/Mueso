package com.akshay.musicplayer.data.sources

import com.akshay.musicplayer.data.models.Track

interface MediaStoreDataSource {
    suspend fun getLocalTracks(): Result<List<Track>>
    suspend fun getTrackById(id: Long): Result<Track?>
}
