package com.akshay.musicplayer.data.repository

import com.akshay.musicplayer.data.models.Track
import com.akshay.musicplayer.data.sources.MediaStoreDataSource

interface TrackRepository {
    suspend fun getLocalTracks(): Result<List<Track>>
    suspend fun getTrackById(id: Long): Result<Track?>
}

class TrackRepositoryImpl(
    private val mediaStoreDataSource: MediaStoreDataSource
) : TrackRepository {

    override suspend fun getLocalTracks(): Result<List<Track>> {
        return mediaStoreDataSource.getLocalTracks()
    }

    override suspend fun getTrackById(id: Long): Result<Track?> {
        return mediaStoreDataSource.getTrackById(id)
    }
}
