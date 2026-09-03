package com.akshay.musicplayer.media.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

    // Online YouTube Player for streaming tracks that cannot be resolved as raw HTTP streams
    private val ytPlayerManager = OnlineYouTubePlayerManager(context)
    private var isPlayingOnline = false
    private var currentOnlineTrack: TrackEntity? = null
    private var tracksQueue: List<TrackEntity> = emptyList()
    private var currentQueueIndex: Int = 0

    private var isSyncingToMediaSession = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isPlayingOnline) return

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
            if (isPlayingOnline) {
                if (isSyncingToMediaSession) {
                    return
                }
                if (isPlaying != ytPlayerManager.isPlaying) {
                    Log.d("MUESO_SYNC", "Control Center requested isPlaying=$isPlaying for online track")
                    if (isPlaying) {
                        ytPlayerManager.play()
                    } else {
                        ytPlayerManager.pause()
                    }
                }
                return
            }

            if (!isRestoring) {
                updatePlaybackState()
            }
            handlePositionUpdates(isPlaying)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isPlayingOnline && reason == Player.DISCONTINUITY_REASON_SEEK) {
                val seekSec = newPosition.positionMs / 1000f
                Log.d("MUESO_SYNC", "Control center seek to ${newPosition.positionMs}ms (${seekSec}s)")
                ytPlayerManager.seekTo(seekSec)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isPlayingOnline) return

            val oldId = currentTrackId
            currentTrackId = mediaItem?.mediaId?.toLongOrNull() ?: currentTrackId
            Log.d("MUESO_SYNC", "ExoPlayer onMediaItemTransition: oldId=$oldId, newId=$currentTrackId, reason=$reason")
            if (!isRestoring) {
                updatePlaybackState()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (isPlayingOnline) return

            super.onPlayerError(error)
            updatePlaybackState()
            handlePositionUpdates(false)
            scope.launch {
                val fullMessage = buildString {
                    append(error.message ?: "Playback error")
                    append(" [code=").append(error.errorCode).append(": ").append(error.errorCodeName).append("]")
                    var cause = error.cause
                    while (cause != null) {
                        append(" | ")
                        append(cause.message ?: cause.javaClass.simpleName)
                        cause = cause.cause
                    }
                }
                _mediaEvents.emit(PlayerEvent.PlaybackError(fullMessage))
            }
        }
    }

    private fun syncMediaSessionForOnlineTrack(track: TrackEntity, isPlaying: Boolean, positionMs: Long = -1L) {
        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
        if (track.duration > 0L) {
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration
        }
        if (positionMs >= 0L) {
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = positionMs
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.onSeekRequested = { seekMs ->
            seekTo(seekMs)
        }

        mediaController?.let { controller ->
            isSyncingToMediaSession = true
            try {
                controller.volume = 0f

                val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album.ifBlank { "YouTube Music" })
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id.toString())
                    .setUri(silenceUri)
                    .setMediaMetadata(metadata)
                    .build()

                val currentId = controller.currentMediaItem?.mediaId
                if (currentId != track.id.toString()) {
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    val pos = if (positionMs >= 0) positionMs else 0L
                    controller.setMediaItem(mediaItem, pos)
                    controller.prepare()
                } else {
                    if (controller.mediaItemCount > 0) {
                        controller.replaceMediaItem(0, mediaItem)
                    }
                    controller.setPlaylistMetadata(metadata)
                }

                if (isPlaying) {
                    if (!controller.isPlaying) {
                        controller.play()
                    }
                } else {
                    if (controller.isPlaying) {
                        controller.pause()
                    }
                }
            } finally {
                mainHandler.post {
                    isSyncingToMediaSession = false
                }
            }
        }
    }

    init {
        // Initialize the online YouTube player
        ytPlayerManager.initialize()

        ytPlayerManager.onStateChanged = { playing ->
            if (isPlayingOnline) {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = playing,
                    currentTrackId = currentTrackId
                )
                currentOnlineTrack?.let { track ->
                    syncMediaSessionForOnlineTrack(track, isPlaying = playing)
                }
            }
        }

        ytPlayerManager.onPositionUpdate = { posMs, durMs ->
            if (isPlayingOnline) {
                com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = posMs
                if (durMs > 0) {
                    val wasDurationZero = com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs <= 0L
                    com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = durMs
                    if (wasDurationZero) {
                        currentOnlineTrack?.let { track ->
                            currentOnlineTrack = track.copy(duration = durMs)
                            syncMediaSessionForOnlineTrack(currentOnlineTrack!!, isPlaying = ytPlayerManager.isPlaying, positionMs = posMs)
                        }
                    }
                }
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = posMs,
                    durationMs = if (durMs > 0) durMs else _playbackState.value.durationMs
                )
            }
        }

        ytPlayerManager.onTrackEnded = {
            if (isPlayingOnline) {
                Log.d("MUESO_SYNC", "YouTubePlayer onTrackEnded -> advancing to next track")
                scope.launch { _mediaEvents.emit(PlayerEvent.TrackEnded) }
            }
        }

        ytPlayerManager.onError = { errName ->
            if (isPlayingOnline) {
                Log.w("MUESO_SYNC", "YouTubePlayer onError: $errName")
            }
        }

        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            mediaController?.addListener(listener)
            pendingRestore?.invoke()
            pendingRestore = null
            if (!isRestoring && !isPlayingOnline) {
                updatePlaybackState()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun handlePositionUpdates(isPlaying: Boolean) {
        positionUpdateJob?.cancel()
        if (isPlaying && !isPlayingOnline) {
            positionUpdateJob = scope.launch {
                while (isActive) {
                    updatePlaybackState()
                    delay(150)
                }
            }
        }
    }

    private fun updatePlaybackState() {
        if (isPlayingOnline) return

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
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        tracksQueue = tracks
        currentQueueIndex = safeIndex
        val track = tracks[safeIndex]
        currentTrackId = track.id
        Log.d("MUESO_SYNC", "ExoPlayer setPlaylistAndPlay: starting at index=$safeIndex, trackId=$currentTrackId, path=${track.filePath.take(30)}")

        val isOnline = track.filePath.startsWith("online:")
        if (isOnline) {
            isPlayingOnline = true
            currentOnlineTrack = track

            com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L

            val videoId = track.filePath.removePrefix("online:")
            Log.d("MUESO_SYNC", "Playing online track '${track.title}' via OnlineYouTubePlayerManager (videoId: $videoId)")
            ytPlayerManager.playVideo(videoId, 0f)

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentTrackId = track.id,
                currentPositionMs = 0L,
                durationMs = track.duration.coerceAtLeast(0L)
            )

            syncMediaSessionForOnlineTrack(track, isPlaying = true, positionMs = 0L)
            return
        }

        // Local or direct HTTP track -> ExoPlayer
        isPlayingOnline = false
        currentOnlineTrack = null
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        val mediaItems = tracks.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(if (t.artworkUrl != null) Uri.parse(t.artworkUrl) else Uri.parse("content://media/external/audio/albumart/${t.albumId}"))
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()

            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(t.filePath)
                .setMediaMetadata(metadata)
                .build()
        }

        mediaController?.let { controller ->
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentTrackId = tracks[safeIndex].id,
                currentPositionMs = 0L,
                durationMs = controller.duration.coerceAtLeast(0L)
            )
        }
    }

    override fun restoreQueue(tracks: List<TrackEntity>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) return
        tracksQueue = tracks
        currentQueueIndex = startIndex
        val track = tracks.getOrNull(startIndex) ?: return
        currentTrackId = track.id

        if (track.filePath.startsWith("online:")) {
            // Online track preview restore
            isPlayingOnline = true
            currentOnlineTrack = track

            com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = startPositionMs

            val videoId = track.filePath.removePrefix("online:")
            val startSec = (startPositionMs / 1000f).coerceAtLeast(0f)
            Log.d("MUESO_RESTORE", "restoreQueue online track '${track.title}' (videoId: $videoId) at ${startSec}s")
            ytPlayerManager.cueVideo(videoId, startSec)

            _playbackState.value = PlaybackState(
                isPlaying = false,
                currentTrackId = track.id,
                currentPositionMs = startPositionMs,
                durationMs = track.duration.coerceAtLeast(0L)
            )

            syncMediaSessionForOnlineTrack(track, isPlaying = false, positionMs = startPositionMs)
            return
        }

        isPlayingOnline = false
        currentOnlineTrack = null
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        val mediaItems = tracks.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(if (t.artworkUrl != null) Uri.parse(t.artworkUrl) else Uri.parse("content://media/external/audio/albumart/${t.albumId}"))
                .build()

            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(t.filePath)
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
                    currentTrackId = track.id,
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
        if (isPlayingOnline && currentQueueIndex + 1 in tracksQueue.indices) {
            seekToIndex(currentQueueIndex + 1)
        } else {
            mediaController?.seekToNextMediaItem()
            updatePlaybackState()
        }
    }

    override fun seekToPrevious() {
        if (isPlayingOnline && currentQueueIndex - 1 in tracksQueue.indices) {
            seekToIndex(currentQueueIndex - 1)
        } else {
            mediaController?.seekToPreviousMediaItem()
            updatePlaybackState()
        }
    }

    override fun togglePlayPause() {
        if (isPlayingOnline) {
            ytPlayerManager.togglePlayPause()
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
                updatePlaybackState()
            }
        }
    }

    override fun pause() {
        if (isPlayingOnline) {
            ytPlayerManager.pause()
            currentOnlineTrack?.let { syncMediaSessionForOnlineTrack(it, isPlaying = false) }
        } else {
            mediaController?.pause()
            updatePlaybackState()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (isPlayingOnline) {
            ytPlayerManager.seekTo(positionMs / 1000f)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            mediaController?.seekTo(positionMs)
        } else {
            mediaController?.seekTo(positionMs)
            updatePlaybackState()
        }
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
        if (fromIndex in tracksQueue.indices && toIndex in tracksQueue.indices) {
            val mutable = tracksQueue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            tracksQueue = mutable
        }
        mediaController?.moveMediaItem(fromIndex, toIndex)
    }

    override fun seekToIndex(index: Int) {
        Log.d("MUESO_SYNC", "seekToIndex: index=$index")
        currentQueueIndex = index
        val track = tracksQueue.getOrNull(index)
        if (track != null && track.filePath.startsWith("online:")) {
            isPlayingOnline = true
            currentOnlineTrack = track
            currentTrackId = track.id

            com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L

            val videoId = track.filePath.removePrefix("online:")
            Log.d("MUESO_SYNC", "seekToIndex online track '${track.title}' (videoId: $videoId)")
            ytPlayerManager.playVideo(videoId, 0f)

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentTrackId = track.id,
                currentPositionMs = 0L,
                durationMs = track.duration.coerceAtLeast(0L)
            )

            syncMediaSessionForOnlineTrack(track, isPlaying = true, positionMs = 0L)
            return
        }

        isPlayingOnline = false
        currentOnlineTrack = null
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        mediaController?.let { controller ->
            currentTrackId = controller.getMediaItemAt(index).mediaId.toLongOrNull() ?: currentTrackId
            controller.seekToDefaultPosition(index)
            controller.play()
            updatePlaybackState()
        }
    }

    override fun updateTrackInQueue(index: Int, track: TrackEntity) {
        if (index in tracksQueue.indices) {
            val mutable = tracksQueue.toMutableList()
            mutable[index] = track
            tracksQueue = mutable
        }

        mediaController?.let { controller ->
            if (index in 0 until controller.mediaItemCount && track.filePath.startsWith("http")) {
                Log.d("MUESO_SYNC", "ExoPlayer updateTrackInQueue: updating item at index=$index with resolved url=${track.filePath.take(30)}...")
                val oldItem = controller.getMediaItemAt(index)
                val updatedItem = MediaItem.Builder()
                    .setMediaId(track.id.toString())
                    .setUri(track.filePath)
                    .setMediaMetadata(oldItem.mediaMetadata)
                    .build()
                controller.replaceMediaItem(index, updatedItem)
                if (index == controller.currentMediaItemIndex && !isPlayingOnline && (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED)) {
                    Log.d("MUESO_SYNC", "ExoPlayer updateTrackInQueue: current item updated while IDLE/ENDED. Calling prepare() and play()...")
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    override fun appendTracksToQueue(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        tracksQueue = tracksQueue + tracks

        val action: () -> Unit = {
            mediaController?.let { controller ->
                Log.d("MUESO_SYNC", "ExoPlayer appendTracksToQueue: appending ${tracks.size} new tracks")
                val newMediaItems = tracks.map { track ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(if (track.artworkUrl != null) Uri.parse(track.artworkUrl) else Uri.parse("content://media/external/audio/albumart/${track.albumId}"))
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(if (track.filePath.startsWith("online:")) "about:blank" else track.filePath)
                        .setMediaMetadata(metadata)
                        .build()
                }
                controller.addMediaItems(newMediaItems)
            }
        }

        if (mediaController != null) {
            action()
        } else {
            val prev = pendingRestore
            pendingRestore = {
                prev?.invoke()
                action()
            }
        }
    }

    override fun insertTracksToQueue(index: Int, tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        if (index in tracksQueue.indices) {
            val mutable = tracksQueue.toMutableList()
            mutable.addAll(index, tracks)
            tracksQueue = mutable
        } else {
            tracksQueue = tracksQueue + tracks
        }

        val action: () -> Unit = {
            mediaController?.let { controller ->
                val safeIndex = index.coerceIn(0, controller.mediaItemCount)
                Log.d("MUESO_SYNC", "ExoPlayer insertTracksToQueue: inserting ${tracks.size} tracks at index $safeIndex")
                val newMediaItems = tracks.map { track ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(if (track.artworkUrl != null) Uri.parse(track.artworkUrl) else Uri.parse("content://media/external/audio/albumart/${track.albumId}"))
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(if (track.filePath.startsWith("online:")) "about:blank" else track.filePath)
                        .setMediaMetadata(metadata)
                        .build()
                }
                controller.addMediaItems(safeIndex, newMediaItems)
            }
        }

        if (mediaController != null) {
            action()
        } else {
            val prev = pendingRestore
            pendingRestore = {
                prev?.invoke()
                action()
            }
        }
    }

    override fun clearUpcomingQueue(fromIndex: Int) {
        if (fromIndex + 1 in tracksQueue.indices) {
            tracksQueue = tracksQueue.subList(0, fromIndex + 1)
        }

        val action: () -> Unit = {
            mediaController?.let { controller ->
                val count = controller.mediaItemCount
                if (fromIndex + 1 < count) {
                    Log.d("MUESO_SYNC", "ExoPlayer clearUpcomingQueue: removing items from ${fromIndex + 1} to $count")
                    controller.removeMediaItems(fromIndex + 1, count)
                }
            }
        }
        if (mediaController != null) {
            action()
        } else {
            val prev = pendingRestore
            pendingRestore = {
                prev?.invoke()
                action()
            }
        }
    }

    override fun release() {
        ytPlayerManager.release()
        positionUpdateJob?.cancel()
        try {
            mediaController?.removeListener(listener)
            mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            Log.w("MUESO_MEDIA", "Safely caught MediaController unbind exception during release", e)
        } finally {
            mediaController = null
            mediaControllerFuture = null
        }
    }

    override fun playbackState(): StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override fun mediaEvents(): Flow<PlayerEvent> = _mediaEvents

    fun getOnlinePlayerView(): com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView? = ytPlayerManager.getPlayerView()
}
