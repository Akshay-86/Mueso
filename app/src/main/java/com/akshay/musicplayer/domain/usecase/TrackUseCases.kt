package com.akshay.musicplayer.domain.usecase

import com.akshay.musicplayer.data.repository.TrackRepository
import com.akshay.musicplayer.domain.models.TrackEntity

class GetLocalTracksUseCase(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(): Result<List<TrackEntity>> {
        return trackRepository.getLocalTracks().mapCatching { tracks ->
            tracks.map { track ->
                TrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    albumId = track.albumId,
                    filePath = track.data
                )
            }
        }
    }
}

class GetTrackByIdUseCase(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(id: Long): Result<TrackEntity?> {
        return trackRepository.getTrackById(id).mapCatching { track ->
            track?.let {
                TrackEntity(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    duration = it.duration,
                    albumId = it.albumId,
                    filePath = it.data
                )
            }
        }
    }
}
