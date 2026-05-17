package com.akshay.musicplayer.media.player

import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class PlayerEvent {
    object TrackEnded : PlayerEvent()
}

interface MediaPlayerController {
    fun togglePlayPause()
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
}
