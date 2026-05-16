package com.akshay.musicplayer.media.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import com.akshay.musicplayer.ui.state.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ExoPlayerController(context: Context) : MediaPlayerController {

    private val player = ExoPlayer.Builder(context).build()
    private val dataSourceFactory = DefaultDataSource.Factory(context)

    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _mediaEvents = MutableSharedFlow<PlayerEvent>()
    private var currentTrackId: Long? = null
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
            handlePositionUpdates(player.isPlaying)
            if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                scope.launch {
                    _mediaEvents.emit(PlayerEvent.TrackEnded)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            handlePositionUpdates(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
            android.util.Log.e("ExoPlayerController", "Player Error: ${error.message}", error)
            updatePlaybackState()
            handlePositionUpdates(false)
        }
    }

    init {
        player.addListener(listener)
    }

    private fun handlePositionUpdates(isPlaying: Boolean) {
        positionUpdateJob?.cancel()
        if (isPlaying) {
            positionUpdateJob = scope.launch {
                while (isActive) {
                    updatePlaybackState()
                    delay(1000)
                }
            }
        }
    }

    private fun updatePlaybackState() {
        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrackId = currentTrackId,
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0L)
        )
    }

    override fun playTrack(trackId: Long, filePath: String) {
        currentTrackId = trackId
        val mediaItem = MediaItem.fromUri(filePath)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
        
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
        updatePlaybackState()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updatePlaybackState()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updatePlaybackState()
    }

    override fun release() {
        positionUpdateJob?.cancel()
        player.removeListener(listener)
        player.release()
    }

    override fun playbackState(): Flow<PlaybackState> = _playbackState.asStateFlow()

    override fun mediaEvents(): Flow<PlayerEvent> = _mediaEvents
}
