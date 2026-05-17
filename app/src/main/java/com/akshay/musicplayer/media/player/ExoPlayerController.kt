package com.akshay.musicplayer.media.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import android.util.Log
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
import kotlinx.coroutines.flow.StateFlow
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
    private var pendingRestore: (() -> Unit)? = null
    @Volatile private var isRestoring = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isRestoring) {
                if (playbackState == Player.STATE_READY) {
                    isRestoring = false
                    updatePlaybackState()
                }
                handlePositionUpdates(mediaController?.isPlaying == true)
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { _mediaEvents.emit(PlayerEvent.TrackEnded) }
                }
                return
            }
            updatePlaybackState()
            handlePositionUpdates(mediaController?.isPlaying == true)
            if (playbackState == Player.STATE_ENDED) {
                scope.launch { _mediaEvents.emit(PlayerEvent.TrackEnded) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isRestoring) {
                updatePlaybackState()
            }
            handlePositionUpdates(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val oldId = currentTrackId
            currentTrackId = mediaItem?.mediaId?.toLongOrNull() ?: currentTrackId
            Log.d("MUESO_SYNC", "ExoPlayer onMediaItemTransition: oldId=$oldId, newId=$currentTrackId, reason=$reason")
            if (!isRestoring) {
                updatePlaybackState()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
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
            pendingRestore?.invoke()
            pendingRestore = null
            if (!isRestoring) {
                updatePlaybackState()
            }
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

    override fun setPlaylistAndPlay(tracks: List<TrackEntity>, startIndex: Int) {
        if (tracks.isEmpty()) return
        currentTrackId = tracks[startIndex].id
        Log.d("MUESO_SYNC", "ExoPlayer setPlaylistAndPlay: starting at index=$startIndex, trackId=$currentTrackId")

        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(Uri.parse("content://media/external/audio/albumart/${track.albumId}"))
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()

            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.filePath)
                .setMediaMetadata(metadata)
                .build()
        }

        mediaController?.let { controller ->
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentTrackId = tracks[startIndex].id,
                currentPositionMs = 0L,
                durationMs = controller.duration.coerceAtLeast(0L)
            )
        }
    }

    override fun restoreQueue(tracks: List<TrackEntity>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) return
        currentTrackId = tracks[startIndex].id

        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(Uri.parse("content://media/external/audio/albumart/${track.albumId}"))
                .build()

            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.filePath)
                .setMediaMetadata(metadata)
                .build()
        }

        val action: () -> Unit = {
            mediaController?.let { controller ->
                isRestoring = true
                controller.setMediaItems(mediaItems, startIndex, startPositionMs)
                controller.prepare()

                _playbackState.value = PlaybackState(
                    isPlaying = controller.isPlaying,
                    currentTrackId = tracks[startIndex].id,
                    currentPositionMs = startPositionMs,
                    durationMs = controller.duration.coerceAtLeast(0L)
                )

                scope.launch {
                    delay(3000)
                    if (isRestoring) {
                        isRestoring = false
                        updatePlaybackState()
                    }
                }
            }
        }

        if (mediaController != null) {
            action()
        } else {
            pendingRestore = action
        }
    }

    override fun seekToNext() {
        mediaController?.seekToNextMediaItem()
        updatePlaybackState()
    }

    override fun seekToPrevious() {
        mediaController?.seekToPreviousMediaItem()
        updatePlaybackState()
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

    override fun setRepeatMode(mode: Int) {
        mediaController?.repeatMode = mode
    }

    override fun getRepeatMode(): Int {
        return mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        Log.d("MUESO_SYNC", "ExoPlayer moveQueueItem: from=$fromIndex, to=$toIndex")
        mediaController?.moveMediaItem(fromIndex, toIndex)
    }

    override fun seekToIndex(index: Int) {
        Log.d("MUESO_SYNC", "ExoPlayer seekToIndex: index=$index")
        mediaController?.let { controller ->
            currentTrackId = controller.getMediaItemAt(index).mediaId.toLongOrNull() ?: currentTrackId
            controller.seekToDefaultPosition(index)
            controller.play()
            updatePlaybackState()
        }
    }

    override fun release() {
        positionUpdateJob?.cancel()
        mediaController?.removeListener(listener)
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }

    override fun playbackState(): StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override fun mediaEvents(): Flow<PlayerEvent> = _mediaEvents
}


