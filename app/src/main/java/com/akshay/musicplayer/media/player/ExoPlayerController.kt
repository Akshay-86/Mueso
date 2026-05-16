package com.akshay.musicplayer.media.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.media.service.MusicPlayerService
import com.akshay.musicplayer.ui.state.PlaybackState
import com.google.common.util.concurrent.ListenableFuture
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

class ExoPlayerController(private val context: Context) : MediaPlayerController {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _mediaEvents = MutableSharedFlow<PlayerEvent>()
    private var currentTrackId: Long? = null
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
            handlePositionUpdates(mediaController?.isPlaying == true)
            if (playbackState == Player.STATE_ENDED) {
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
            currentTrackId = mediaItem?.mediaId?.toLongOrNull() ?: currentTrackId
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
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            mediaController?.addListener(listener)
            updatePlaybackState()
        }, ContextCompat.getMainExecutor(context))
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
        val controller = mediaController
        if (controller != null) {
            _playbackState.value = PlaybackState(
                isPlaying = controller.isPlaying,
                currentTrackId = currentTrackId,
                currentPositionMs = controller.currentPosition,
                durationMs = controller.duration.coerceAtLeast(0L)
            )
        }
    }

    override fun playTrack(track: TrackEntity) {
        currentTrackId = track.id
        
        // Metadata is crucial for the system "Now Playing" notification
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(Uri.parse("android.resource://${context.packageName}/drawable/ic_logo"))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.filePath)
            .setMediaMetadata(metadata)
            .build()

        mediaController?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            updatePlaybackState()
        }
    }

    override fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            updatePlaybackState()
        }
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        updatePlaybackState()
    }

    override fun release() {
        positionUpdateJob?.cancel()
        mediaController?.removeListener(listener)
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }

    override fun playbackState(): Flow<PlaybackState> = _playbackState.asStateFlow()

    override fun mediaEvents(): Flow<PlayerEvent> = _mediaEvents
}
