package com.akshay.musicplayer.media.player

import com.akshay.musicplayer.ui.state.PlaybackState
import kotlinx.coroutines.flow.Flow

sealed class PlayerEvent {
    object TrackEnded : PlayerEvent()
}

interface MediaPlayerController {
    fun playTrack(trackId: Long, filePath: String)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun release()
    fun playbackState(): Flow<PlaybackState>
    fun mediaEvents(): Flow<PlayerEvent>
}
