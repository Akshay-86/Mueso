package com.akshay.musicplayer.media.player

import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class PlayerEvent {
    object TrackEnded : PlayerEvent()
    data class PlaybackError(val message: String) : PlayerEvent()
}

interface MediaPlayerController {
    fun togglePlayPause()
    fun pause()
    fun seekToNext()
    fun seekToPrevious()
    fun seekTo(positionMs: Long)
    fun release()
    fun playbackState(): StateFlow<PlaybackState>
    fun mediaEvents(): Flow<PlayerEvent>
    fun setPlaylistAndPlay(tracks: List<TrackEntity>, startIndex: Int = 0)
    fun restoreQueue(tracks: List<TrackEntity>, startIndex: Int = 0, startPositionMs: Long = 0L)
    fun setRepeatMode(mode: Int)
    fun getRepeatMode(): Int
    fun setShuffleEnabled(enabled: Boolean)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun seekToIndex(index: Int)
    fun updateTrackInQueue(index: Int, track: TrackEntity)
    fun appendTracksToQueue(tracks: List<TrackEntity>)
    fun insertTracksToQueue(index: Int, tracks: List<TrackEntity>)
    fun clearUpcomingQueue(fromIndex: Int)
}
