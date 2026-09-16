package com.akshay.musicplayer.media.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.request.ImageRequest
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ExoPlayerController(private val context: Context) : MediaPlayerController {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _mediaEvents = MutableSharedFlow<PlayerEvent>()
    private var currentTrackId: Long? = null
    private var positionUpdateJob: Job? = null
    private var pendingRestore: (() -> Unit)? = null
    private var artworkLoadJob: Job? = null
    @Volatile private var isRestoring = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Online YouTube Player for streaming tracks that cannot be resolved as raw HTTP streams
    private val ytPlayerManager = OnlineYouTubePlayerManager(context)
    private var isPlayingOnline = false
    private var isPlayingLosslessOnline = false
    private var currentOnlineTrack: TrackEntity? = null
    private var tracksQueue: List<TrackEntity> = emptyList()
    private var currentQueueIndex: Int = 0

    private val losslessRepo = com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository()
    private val _activeAudioFormat = MutableStateFlow(com.akshay.musicplayer.domain.models.ActiveAudioFormat())
    override fun activeAudioFormat(): StateFlow<com.akshay.musicplayer.domain.models.ActiveAudioFormat> = _activeAudioFormat.asStateFlow()

    private var isSyncingToMediaSession = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val onlineRepo = OnlineMusicRepository()

    @Volatile private var isWaitingForNetwork: Boolean = false
    private var pendingNetworkRetryTrack: TrackEntity? = null
    private var pendingNetworkRetryIndex: Int = -1
    private var pendingNetworkRetryPosMs: Long = 0L
    private var networkRetryJob: Job? = null

    override fun isWaitingForNetwork(): Boolean = isWaitingForNetwork

    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d("ExoPlayerController", "noisyReceiver received action: $action")
            when (action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.i("ExoPlayerController", "ACTION_AUDIO_BECOMING_NOISY -> pausing playback")
                    pause()
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_CONNECTED
                    )
                    if (state == BluetoothProfile.STATE_DISCONNECTED ||
                        state == BluetoothProfile.STATE_DISCONNECTING) {
                        Log.i("ExoPlayerController", "Bluetooth A2DP disconnected/disconnecting -> pausing playback")
                        pause()
                    }
                }
            }
        }
    }

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
                // Control Center play/pause is handled directly via MediaSessionBridge callbacks.
                // Do not manipulate ytPlayerManager here.
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
            // Seeking for online tracks is handled via MediaSessionBridge.onSeekRequested.
            // Do not call ytPlayerManager.seekTo here to prevent loops with ExoPlayer internal seeks.
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isPlayingOnline) return

            val oldId = currentTrackId
            val newId = mediaItem?.mediaId?.toLongOrNull() ?: currentTrackId
            currentTrackId = newId
            Log.d("MUESO_SYNC", "ExoPlayer onMediaItemTransition: oldId=$oldId, newId=$currentTrackId, reason=$reason")
            val newIndex = tracksQueue.indexOfFirst { it.id == newId }
            if (newIndex >= 0) {
                currentQueueIndex = newIndex
                val currentTrack = tracksQueue[newIndex]
                if (currentTrack.filePath.startsWith("online:")) {
                    Log.d("MUESO_SYNC", "ExoPlayer transitioned into online track '${currentTrack.title}'. Handing off to online player.")
                    seekToIndex(newIndex)
                    return
                }
            }
            if (oldId != newId) {
                val currentTrack = tracksQueue.firstOrNull { it.id == currentTrackId }
                if (currentTrack != null) {
                    updateArtworkForCurrentTrack(currentTrack)
                }
            }
            if (!isRestoring) {
                updatePlaybackState()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (isPlayingOnline) return

            super.onPlayerError(error)
            Log.e("MUESO_SYNC", "ExoPlayer onPlayerError: ${error.message} [code=${error.errorCode}: ${error.errorCodeName}]", error)
            updatePlaybackState()
            handlePositionUpdates(false)

            // If an online stream fails in ExoPlayer (e.g. 403 Forbidden, expired stream, or network error),
            // seamlessly fall back to the embedded YouTube player or stage for network recovery
            val curTrack = tracksQueue.getOrNull(currentQueueIndex)
            val videoId = if (curTrack != null) onlineRepo.extractVideoId(curTrack) else ""
            val isOnlineStream = curTrack != null && (curTrack.filePath.startsWith("http") || curTrack.filePath.startsWith("online:") || isPlayingLosslessOnline) && videoId.isNotBlank()
            val isNetDown = !com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()

            if (isOnlineStream && isNetDown) {
                Log.w("MUESO_NET", "ExoPlayer stream error while offline on '${curTrack?.title}'. Queuing for network recovery.")
                handleOnlineTrackNetworkError(curTrack!!, mediaController?.currentPosition ?: 0L)
                return
            }

            if (isOnlineStream) {
                Log.w("MUESO_SYNC", "ExoPlayer stream error on '${curTrack?.title}'. Falling back to YouTube player!")
                scope.launch(Dispatchers.Main) {
                    fallbackToOnlinePlayer(curTrack!!, videoId)
                }
                return
            }

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

    private fun getBestArtworkUri(track: TrackEntity): Uri? {
        val art = track.artworkUrl
        if (!art.isNullOrBlank()) {
            return if (art.startsWith("http://") || art.startsWith("https://") || art.startsWith("content://") || art.startsWith("file://")) {
                Uri.parse(art)
            } else {
                Uri.fromFile(File(art))
            }
        }
        if (track.id > 0 && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http")) {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
        }
        if (track.filePath.isNotBlank() && !track.filePath.startsWith("online:") && !track.filePath.startsWith("http")) {
            return Uri.fromFile(File(track.filePath))
        }
        return null
    }

    private fun updateArtworkForCurrentTrack(track: TrackEntity) {
        artworkLoadJob?.cancel()
        artworkLoadJob = scope.launch(Dispatchers.IO) {
            val bytes = loadArtworkBytes(track) ?: return@launch
            withContext(Dispatchers.Main) {
                applyArtworkBytesToCurrentMediaItem(track.id, bytes)
            }
        }
    }

    private fun cropToSquare(bm: Bitmap): Bitmap {
        if (bm.width == bm.height) return bm
        val size = minOf(bm.width, bm.height)
        val x = (bm.width - size) / 2
        val y = (bm.height - size) / 2
        return Bitmap.createBitmap(bm, x, y, size, size)
    }

    private suspend fun loadArtworkBytes(track: TrackEntity): ByteArray? {
        try {
            val artUri = getBestArtworkUri(track) ?: return null
            val uriString = artUri.toString()

            // 1. If online HTTP/HTTPS URL
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                val candidates = onlineRepo.getYouTubeArtworkFallbackList(uriString)
                val loader = ImageLoader.Builder(context).allowHardware(false).build()
                for (cand in candidates) {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(cand)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(request)
                        val bm = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bm != null) {
                            val squareBm = cropToSquare(bm)
                            val stream = ByteArrayOutputStream()
                            squareBm.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            return stream.toByteArray()
                        }
                    } catch (_: Exception) {}
                }
            }

            // 2. If content URI
            if (artUri.scheme == "content") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uriString.contains("audio/media")) {
                    try {
                        val bm = context.contentResolver.loadThumbnail(artUri, Size(1024, 1024), null)
                        val squareBm = cropToSquare(bm)
                        val stream = ByteArrayOutputStream()
                        squareBm.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        return stream.toByteArray()
                    } catch (_: Exception) {}
                }
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, artUri)
                    val raw = retriever.embeddedPicture
                    retriever.release()
                    if (raw != null) {
                        try {
                            val rawBm = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                            if (rawBm != null) {
                                val squareBm = cropToSquare(rawBm)
                                val stream = ByteArrayOutputStream()
                                squareBm.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                return stream.toByteArray()
                            }
                        } catch (_: Exception) {}
                        return raw
                    }
                } catch (_: Exception) {}
            }

            // 3. If local file
            val filePath = if (artUri.scheme == "file") artUri.path else if (!uriString.contains("://")) uriString else track.filePath
            if (!filePath.isNullOrBlank() && !filePath.startsWith("online:") && !filePath.startsWith("http")) {
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val raw = retriever.embeddedPicture
                        retriever.release()
                        if (raw != null) {
                            try {
                                val rawBm = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                                if (rawBm != null) {
                                    val squareBm = cropToSquare(rawBm)
                                    val stream = ByteArrayOutputStream()
                                    squareBm.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                    return stream.toByteArray()
                                }
                            } catch (_: Exception) {}
                            return raw
                        }
                    } catch (_: Exception) {}

                    try {
                        val bm = BitmapFactory.decodeFile(file.absolutePath)
                        if (bm != null) {
                            val squareBm = cropToSquare(bm)
                            val stream = ByteArrayOutputStream()
                            squareBm.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            return stream.toByteArray()
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun applyArtworkBytesToCurrentMediaItem(trackId: Long, bytes: ByteArray) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(currentIndex)
            if (item.mediaId == trackId.toString()) {
                val updatedMetadata = item.mediaMetadata.buildUpon()
                    .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                val updatedItem = item.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()
                controller.replaceMediaItem(currentIndex, updatedItem)
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

        mediaController?.let { controller ->
            isSyncingToMediaSession = true
            com.akshay.musicplayer.media.service.MediaSessionBridge.isSyncing = true
            try {
                controller.volume = 0f

                val currentId = controller.currentMediaItem?.mediaId
                if (currentId != track.id.toString()) {
                    val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
                    val artworkUri = getBestArtworkUri(track)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album.ifBlank { "YouTube Music" })
                        .setArtworkUri(artworkUri)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(silenceUri)
                        .setMediaMetadata(metadata)
                        .build()

                    val wasPlaying = controller.isPlaying
                    controller.setMediaItem(mediaItem, /* resetPosition = */ false)
                    if (!wasPlaying) {
                        controller.prepare()
                    }
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

                updateArtworkForCurrentTrack(track)
            } finally {
                mainHandler.post {
                    isSyncingToMediaSession = false
                    com.akshay.musicplayer.media.service.MediaSessionBridge.isSyncing = false
                }
            }
        }
    }

    private fun startServiceIfForeground() {
        if (com.akshay.musicplayer.media.service.MediaSessionBridge.isServiceRunning) {
            return
        }
        try {
            val intent = android.content.Intent(context, MusicPlayerService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.d("MUESO_SYNC", "Could not startService: ${e.message}")
        }
    }

    private var fallbackJob: Job? = null
    private val failedVideoIdsMap = java.util.concurrent.ConcurrentHashMap<Long, MutableSet<String>>()
    private var trackErrorRetryCount = 0
    private var lastErrorTrackId: Long? = null

    private fun isOnlineTrack(track: TrackEntity): Boolean {
        return track.filePath.startsWith("online:") ||
                track.filePath.contains("googlevideo") ||
                track.filePath.contains("youtube") ||
                track.album == "Online Track" ||
                onlineRepo.extractVideoId(track).isNotBlank()
    }

    private fun handleOnlineTrackNetworkError(track: TrackEntity, positionMs: Long = 0L) {
        fallbackJob?.cancel()
        networkRetryJob?.cancel()
        isWaitingForNetwork = true
        pendingNetworkRetryTrack = track
        pendingNetworkRetryIndex = currentQueueIndex
        val actualPosMs = maxOf(
            positionMs,
            pendingNetworkRetryPosMs,
            ytPlayerManager.currentPositionMs,
            _playbackState.value.currentPositionMs
        )
        pendingNetworkRetryPosMs = actualPosMs
        Log.w("MUESO_NET", "Online playback stalled due to network issue for '${track.title}' at ${actualPosMs}ms. Waiting for network...")

        syncMediaSessionForOnlineTrack(track, isPlaying = false, positionMs = actualPosMs)
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentTrackId = track.id,
            currentPositionMs = actualPosMs
        )

        // Periodic background retry: checks every 6s if network returned
        networkRetryJob = scope.launch(Dispatchers.Main) {
            var attempt = 0
            while (isWaitingForNetwork && isActive) {
                delay(6000)
                attempt++
                if (com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()) {
                    Log.i("MUESO_NET", "Periodic retry detected network online! Retrying '${track.title}' (attempt $attempt)")
                    retryPendingNetworkTrack()
                    break
                } else {
                    Log.d("MUESO_NET", "Periodic retry: waiting for network (attempt $attempt)...")
                }
            }
        }
    }

    override fun retryPendingNetworkTrack() {
        val track = pendingNetworkRetryTrack ?: currentOnlineTrack ?: tracksQueue.getOrNull(currentQueueIndex) ?: return
        val targetIndex = if (pendingNetworkRetryIndex in tracksQueue.indices) pendingNetworkRetryIndex else currentQueueIndex
        val posMs = maxOf(
            pendingNetworkRetryPosMs,
            ytPlayerManager.currentPositionMs,
            _playbackState.value.currentPositionMs
        )
        Log.i("MUESO_NET", "=== Resuming pending track after network recovery: '${track.title}' at ${posMs}ms (ytPos=${ytPlayerManager.currentPositionMs}, statePos=${_playbackState.value.currentPositionMs}, savedPos=$pendingNetworkRetryPosMs) ===")

        isWaitingForNetwork = false
        networkRetryJob?.cancel()
        pendingNetworkRetryTrack = null
        pendingNetworkRetryIndex = -1
        pendingNetworkRetryPosMs = 0L

        scope.launch(Dispatchers.Main) {
            val videoId = onlineRepo.extractVideoId(track).ifBlank {
                val p = track.filePath.removePrefix("online:")
                if (p.length == 11 && !p.contains(" ") && !p.contains("/")) p else ""
            }

            if (videoId.isNotBlank()) {
                isPlayingOnline = true
                currentOnlineTrack = track
                currentQueueIndex = targetIndex
                currentTrackId = track.id
                val resumeSec = (posMs / 1000f).coerceAtLeast(0f)
                Log.i("MUESO_NET", "Resuming online track $videoId at ${resumeSec}s")
                ytPlayerManager.reloadAndPlay(videoId, resumeSec)
                syncMediaSessionForOnlineTrack(track, isPlaying = true, positionMs = posMs)
                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentTrackId = track.id,
                    currentPositionMs = posMs,
                    durationMs = track.duration.coerceAtLeast(0L)
                )
            } else {
                seekToIndex(targetIndex)
            }
        }
    }

    private fun handleOnlineTrackError(errorName: String) {
        val track = currentOnlineTrack ?: return
        val currentVid = ytPlayerManager.currentVideoId ?: onlineRepo.extractVideoId(track).ifBlank { track.filePath.removePrefix("online:") }
        if (currentVid.isBlank()) return

        val isNetworkDown = !com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()
        val isNetworkError = isNetworkDown ||
                errorName == "NETWORK_ERROR" ||
                errorName == "HTML_5_PLAYER" ||
                errorName.contains("NETWORK", ignoreCase = true)

        if (isNetworkError) {
            if (!isNetworkDown && trackErrorRetryCount < 3) {
                trackErrorRetryCount++
                val posSec = (ytPlayerManager.currentPositionMs / 1000f).coerceAtLeast(0f)
                Log.w("MUESO_NET", "Transient network/HTML5 error for '${track.title}'. Quick reload at ${posSec}s with lowest bitrate (attempt $trackErrorRetryCount)")
                ytPlayerManager.reloadAndPlay(currentVid, posSec)
                return
            }
            Log.w("MUESO_NET", "Online track '${track.title}' hit persistent network error '$errorName' (netDown=$isNetworkDown). Entering network wait/retry state.")
            handleOnlineTrackNetworkError(track, ytPlayerManager.currentPositionMs)
            return
        }

        fallbackJob?.cancel()
        fallbackJob = scope.launch(Dispatchers.Main) {
            if (lastErrorTrackId != track.id) {
                lastErrorTrackId = track.id
                trackErrorRetryCount = 0
            }

            if (trackErrorRetryCount < 2) {
                trackErrorRetryCount++
                Log.w("MUESO_SYNC", "Track '${track.title}' ($currentVid) encountered error '$errorName'. Attempting automatic reload retry (attempt $trackErrorRetryCount)...")
                delay(700L * trackErrorRetryCount)
                if (currentOnlineTrack?.id == track.id) {
                    val pos = (ytPlayerManager.currentPositionMs / 1000f).coerceAtLeast(0f)
                    ytPlayerManager.reloadAndPlay(currentVid, pos)
                    return@launch
                }
            }

            Log.w("MUESO_SYNC", "Track '${track.title}' ($currentVid) encountered error '$errorName' after retries.")
            try {
                android.widget.Toast.makeText(
                    context,
                    "Playback error for \"${track.title}\". Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {}
        }
    }

    private fun getActiveOutputDeviceName(): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Phone Speaker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> return "USB DAC / Audio"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.productName?.toString() else null
                        return if (!name.isNullOrBlank()) "Bluetooth ($name)" else "Bluetooth Audio"
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return "Wired Headphones"
                }
            }
        }
        return "Phone Speaker"
    }

    private fun playLosslessTrackInExoPlayer(track: TrackEntity, losslessResult: com.akshay.musicplayer.domain.models.LosslessStreamResult) {
        isPlayingOnline = false
        isPlayingLosslessOnline = true
        currentOnlineTrack = track
        currentTrackId = track.id
        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = false
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(Uri.parse(losslessResult.streamUrl))
            .setMimeType(MimeTypes.AUDIO_FLAC)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album.ifBlank { losslessResult.album ?: "Lossless Master" })
                    .setArtworkUri(getBestArtworkUri(track))
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

        mediaController?.let { controller ->
            controller.repeatMode = currentRepeatMode
            controller.setMediaItem(mediaItem, 0L)
            controller.prepare()
            controller.play()

            val effectiveDuration = if (losslessResult.durationSeconds > 0) losslessResult.durationSeconds * 1000L else track.duration.coerceAtLeast(0L)
            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentTrackId = track.id,
                currentPositionMs = 0L,
                durationMs = effectiveDuration
            )

            updateArtworkForCurrentTrack(track)
        }

        val isHiRes = losslessResult.bitDepth > 16 || losslessResult.sampleRateHz > 48000
        val prefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
        val isBitPerfect = prefs.getBoolean("bit_perfect_mode", false)

        _activeAudioFormat.value = com.akshay.musicplayer.domain.models.ActiveAudioFormat(
            codec = losslessResult.codec,
            bitDepth = losslessResult.bitDepth,
            sampleRateHz = losslessResult.sampleRateHz,
            bitrateKbps = losslessResult.bitrateKbps,
            isLossless = true,
            isHiRes = isHiRes,
            sourceName = losslessResult.source,
            audioOutputDevice = getActiveOutputDeviceName(),
            isBitPerfect = isBitPerfect
        )
    }

    private fun playViaOnlineYouTubePlayer(track: TrackEntity) {
        isPlayingOnline = true
        isPlayingLosslessOnline = false
        currentOnlineTrack = track
        currentTrackId = track.id

        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L

        val videoId = onlineRepo.extractVideoId(track).ifBlank { track.filePath.removePrefix("online:") }
        Log.d("MUESO_SYNC", "Playing online track '${track.title}' via OnlineYouTubePlayerManager (videoId: $videoId)")
        ytPlayerManager.playVideo(videoId, 0f)

        _playbackState.value = PlaybackState(
            isPlaying = true,
            currentTrackId = track.id,
            currentPositionMs = 0L,
            durationMs = track.duration.coerceAtLeast(0L)
        )

        syncMediaSessionForOnlineTrack(track, isPlaying = true, positionMs = 0L)

        _activeAudioFormat.value = com.akshay.musicplayer.domain.models.ActiveAudioFormat(
            codec = "OPUS",
            bitDepth = 16,
            sampleRateHz = 48000,
            bitrateKbps = 160,
            isLossless = false,
            isHiRes = false,
            sourceName = "YouTube Music",
            audioOutputDevice = getActiveOutputDeviceName(),
            isBitPerfect = false
        )
    }

    private fun fallbackToOnlinePlayer(track: TrackEntity, videoId: String) {
        isPlayingOnline = true
        currentOnlineTrack = track
        currentTrackId = track.id

        // Pause ExoPlayer to release audio focus/track
        mediaController?.pause()

        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L

        Log.d("MUESO_SYNC", "fallbackToOnlinePlayer: playing online track '${track.title}' via OnlineYouTubePlayerManager (videoId: $videoId)")
        ytPlayerManager.playVideo(videoId, 0f)

        _playbackState.value = PlaybackState(
            isPlaying = true,
            currentTrackId = track.id,
            currentPositionMs = 0L,
            durationMs = track.duration.coerceAtLeast(0L)
        )

        syncMediaSessionForOnlineTrack(track, isPlaying = true, positionMs = 0L)
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
                if (isWaitingForNetwork && posMs > pendingNetworkRetryPosMs) {
                    pendingNetworkRetryPosMs = posMs
                }
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
                handleOnlineTrackError(errName)
            }
        }

        ytPlayerManager.onNetworkError = { vid, posSec ->
            if (isPlayingOnline) {
                val track = currentOnlineTrack ?: tracksQueue.getOrNull(currentQueueIndex)
                if (track != null) {
                    handleOnlineTrackNetworkError(track, (posSec * 1000).toLong())
                }
            }
        }

        com.akshay.musicplayer.data.remote.NetworkMonitor.addOnNetworkRestoredListener {
            mainHandler.post {
                if (isWaitingForNetwork) {
                    Log.i("MUESO_NET", "Network restored callback fired in ExoPlayerController! Resuming playback...")
                    retryPendingNetworkTrack()
                }
            }
        }

        com.akshay.musicplayer.media.service.MediaSessionBridge.onNextRequested = {
            mainHandler.post { seekToNext() }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPreviousRequested = {
            mainHandler.post { seekToPrevious() }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPlayRequested = {
            mainHandler.post {
                if (isWaitingForNetwork) {
                    if (com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()) {
                        retryPendingNetworkTrack()
                    } else {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Waiting for network connection...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } catch (_: Exception) {}
                    }
                    return@post
                }
                if (isPlayingOnline) {
                    ytPlayerManager.play()
                }
            }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPauseRequested = {
            mainHandler.post {
                pause()
            }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.hasNextItem = {
            val mode = getRepeatMode()
            if (mode == Player.REPEAT_MODE_ALL) {
                tracksQueue.isNotEmpty()
            } else {
                currentQueueIndex < tracksQueue.size - 1
            }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.hasPreviousItem = {
            val mode = getRepeatMode()
            if (mode == Player.REPEAT_MODE_ALL) {
                tracksQueue.isNotEmpty()
            } else {
                currentQueueIndex > 0
            }
        }
        com.akshay.musicplayer.media.service.MediaSessionBridge.onSeekRequested = { seekMs ->
            mainHandler.post {
                seekTo(seekMs)
            }
        }

        try {
            val noisyFilter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                noisyFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isNoisyReceiverRegistered = true
            Log.d("ExoPlayerController", "Successfully registered audio becoming noisy & bluetooth receiver")
        } catch (e: Exception) {
            Log.w("ExoPlayerController", "Failed to register noisy receiver", e)
        }

        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                mediaController?.addListener(listener)
                pendingRestore?.invoke()
                pendingRestore = null
                if (!isRestoring && !isPlayingOnline) {
                    updatePlaybackState()
                }
            } catch (e: Exception) {
                Log.e("ExoPlayerController", "Failed to connect to MediaController: ${e.message}", e)
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
            val curPos = controller.currentPosition
            val dur = controller.duration.coerceAtLeast(0L)
            _playbackState.value = PlaybackState(
                isPlaying = controller.isPlaying,
                currentTrackId = currentTrackId,
                currentPositionMs = curPos,
                durationMs = dur
            )
            applyCrossfadeIfEnabled(controller, curPos, dur)
        }
    }

    private fun applyCrossfadeIfEnabled(controller: MediaController, curPos: Long, dur: Long) {
        if (dur <= 0L) return
        val prefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
        val crossfadeEnabled = prefs.getBoolean("crossfade_enabled", false)
        if (!crossfadeEnabled) {
            if (controller.volume < 1.0f) controller.volume = 1.0f
            return
        }
        val crossfadeSec = prefs.getInt("crossfade_seconds", 3).coerceIn(1, 12)
        val fadeMs = crossfadeSec * 1000L
        val remainingMs = dur - curPos

        if (curPos < fadeMs) {
            val vol = (curPos.toFloat() / fadeMs).coerceIn(0.1f, 1.0f)
            controller.volume = vol
        } else if (remainingMs in 0..fadeMs) {
            val vol = (remainingMs.toFloat() / fadeMs).coerceIn(0.05f, 1.0f)
            controller.volume = vol
        } else {
            if (controller.volume < 1.0f) controller.volume = 1.0f
        }
    }

    override fun setPlaylistAndPlay(tracks: List<TrackEntity>, startIndex: Int) {
        if (tracks.isEmpty()) return
        startServiceIfForeground()
        fallbackJob?.cancel()
        trackErrorRetryCount = 0
        lastErrorTrackId = null
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        tracksQueue = tracks
        currentQueueIndex = safeIndex
        val track = tracks[safeIndex]
        currentTrackId = track.id
        Log.d("MUESO_SYNC", "ExoPlayer setPlaylistAndPlay: starting at index=$safeIndex, trackId=$currentTrackId, path=${track.filePath.take(30)}")

        val isOnline = isOnlineTrack(track)
        if (isOnline) {
            val prefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
            val isLosslessEnabled = prefs.getBoolean("lossless_streaming_enabled", true)
            val customServer = prefs.getString("lossless_server_url", com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL)
                ?: com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL
            losslessRepo.updateServerBaseUrl(customServer)

            if (isLosslessEnabled) {
                scope.launch(Dispatchers.IO) {
                    val durationSec = if (track.duration > 0) (track.duration / 1000).toInt() else 0
                    val losslessResult = losslessRepo.resolveLosslessStream(track.title, track.artist, durationSec)
                    withContext(Dispatchers.Main) {
                        if (losslessResult != null && currentTrackId == track.id) {
                            Log.i("MUESO_LOSSLESS", "Playing '${track.title}' via Lossless FLAC directly in ExoPlayer (${losslessResult.bitrateKbps} kbps)")
                            playLosslessTrackInExoPlayer(track, losslessResult)
                            return@withContext
                        }
                        playViaOnlineYouTubePlayer(track)
                    }
                }
                return
            } else {
                playViaOnlineYouTubePlayer(track)
                return
            }
        }

        // Local or direct HTTP track -> ExoPlayer
        isPlayingOnline = false
        isPlayingLosslessOnline = false
        currentOnlineTrack = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = false
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = 0L
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L
        com.akshay.musicplayer.media.service.MediaSessionBridge.onSeekRequested = null
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        val ext = track.filePath.substringAfterLast(".", "").uppercase()
        val isFlac = ext == "FLAC"
        val isWav = ext == "WAV"
        val isBitPerfect = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE).getBoolean("bit_perfect_mode", false)
        _activeAudioFormat.value = com.akshay.musicplayer.domain.models.ActiveAudioFormat(
            codec = if (ext.isNotBlank()) ext else "MP3",
            bitDepth = if (isFlac || isWav) 24 else 16,
            sampleRateHz = 44100,
            bitrateKbps = if (isFlac) 1411 else 320,
            isLossless = isFlac || isWav,
            isHiRes = isFlac,
            sourceName = "Local Storage",
            audioOutputDevice = getActiveOutputDeviceName(),
            isBitPerfect = isBitPerfect
        )

        val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
        val mediaItems = tracks.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(getBestArtworkUri(t))
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()

            val uri = if (isOnlineTrack(t)) silenceUri else Uri.parse(t.filePath)

            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        }

        val action: () -> Unit = {
            mediaController?.let { controller ->
                controller.repeatMode = currentRepeatMode
                controller.setMediaItems(mediaItems, safeIndex, 0L)
                controller.prepare()
                controller.play()

                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentTrackId = tracks[safeIndex].id,
                    currentPositionMs = 0L,
                    durationMs = if (controller.duration > 0) controller.duration else tracks[safeIndex].duration.coerceAtLeast(0L)
                )

                updateArtworkForCurrentTrack(tracks[safeIndex])
            }
        }

        if (mediaController != null) {
            action()
        } else {
            pendingRestore = action
        }
    }

    override fun restoreQueue(tracks: List<TrackEntity>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) return
        tracksQueue = tracks
        currentQueueIndex = startIndex
        val track = tracks.getOrNull(startIndex) ?: return
        currentTrackId = track.id
        Log.d("MUESO_RESTORE", "ExoPlayerController.restoreQueue called: totalTracks=${tracks.size}, startIndex=$startIndex, startPositionMs=${startPositionMs}ms, trackId=${track.id}, trackTitle='${track.title}', path='${track.filePath}'")

        if (isOnlineTrack(track)) {
            // Online track preview restore
            isPlayingOnline = true
            currentOnlineTrack = track

            com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = true
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = track.duration.coerceAtLeast(0L)
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = startPositionMs

            val videoId = onlineRepo.extractVideoId(track).ifBlank { track.filePath.removePrefix("online:") }
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
        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = false
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = 0L
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L
        com.akshay.musicplayer.media.service.MediaSessionBridge.onSeekRequested = null
        ytPlayerManager.pause()
        mediaController?.volume = 1f

        val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
        val mediaItems = tracks.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(getBestArtworkUri(t))
                .build()

            val uri = if (isOnlineTrack(t)) silenceUri else Uri.parse(t.filePath)

            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        }

        val action: () -> Unit = {
            mediaController?.let { controller ->
                isRestoring = true
                Log.d("MUESO_RESTORE", "ExoPlayerController: applying ${mediaItems.size} items to mediaController at startIndex=$startIndex, pos=${startPositionMs}ms")
                controller.setMediaItems(mediaItems, startIndex, startPositionMs)
                controller.prepare()

                _playbackState.value = PlaybackState(
                    isPlaying = controller.isPlaying,
                    currentTrackId = track.id,
                    currentPositionMs = startPositionMs,
                    durationMs = if (controller.duration > 0) controller.duration else track.duration.coerceAtLeast(0L)
                )

                updateArtworkForCurrentTrack(track)

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
            Log.d("MUESO_RESTORE", "ExoPlayerController: mediaController is null, queuing pendingRestore")
            pendingRestore = action
        }
    }

    override fun seekToNext() {
        val nextIndex = currentQueueIndex + 1
        if (nextIndex in tracksQueue.indices) {
            seekToIndex(nextIndex)
        } else if (getRepeatMode() == Player.REPEAT_MODE_ALL && tracksQueue.isNotEmpty()) {
            seekToIndex(0)
        } else {
            Log.d("MUESO_SYNC", "Reached end of playlist in seekToNext, pausing.")
            pause()
        }
    }

    override fun seekToPrevious() {
        val prevIndex = currentQueueIndex - 1
        if (prevIndex in tracksQueue.indices) {
            seekToIndex(prevIndex)
        } else if (getRepeatMode() == Player.REPEAT_MODE_ALL && tracksQueue.isNotEmpty()) {
            seekToIndex(tracksQueue.size - 1)
        }
    }

    override fun togglePlayPause() {
        if (isWaitingForNetwork) {
            if (com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()) {
                retryPendingNetworkTrack()
            } else {
                try {
                    android.widget.Toast.makeText(
                        context,
                        "Waiting for network connection...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {}
            }
            return
        }
        if (isPlayingOnline) {
            ytPlayerManager.togglePlayPause()
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.mediaItemCount == 0 && tracksQueue.isNotEmpty()) {
                        setPlaylistAndPlay(tracksQueue, currentQueueIndex.coerceIn(tracksQueue.indices))
                    } else {
                        controller.play()
                    }
                }
                updatePlaybackState()
            }
        }
    }

    override fun pause() {
        networkRetryJob?.cancel()
        isWaitingForNetwork = false
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
            com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = positionMs
            ytPlayerManager.seekTo(positionMs / 1000f)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } else {
            mediaController?.seekTo(positionMs)
            updatePlaybackState()
        }
    }

    private var currentRepeatMode: Int = Player.REPEAT_MODE_OFF

    override fun setRepeatMode(mode: Int) {
        currentRepeatMode = mode
        if (!isPlayingOnline) {
            mediaController?.repeatMode = mode
        }
    }

    override fun getRepeatMode(): Int {
        return currentRepeatMode
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
        startServiceIfForeground()
        fallbackJob?.cancel()
        networkRetryJob?.cancel()
        isWaitingForNetwork = false
        currentQueueIndex = index
        val track = tracksQueue.getOrNull(index)
        if (track != null && isOnlineTrack(track)) {
            val isNetDown = !com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()
            if (isNetDown) {
                Log.w("MUESO_NET", "seekToIndex: online track '${track.title}' requested while offline. Staging for network restoration.")
                currentOnlineTrack = track
                currentTrackId = track.id
                handleOnlineTrackNetworkError(track, 0L)
                return
            }

            val prefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
            val isLosslessEnabled = prefs.getBoolean("lossless_streaming_enabled", true)
            val customServer = prefs.getString("lossless_server_url", com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL)
                ?: com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL
            losslessRepo.updateServerBaseUrl(customServer)

            currentTrackId = track.id

            if (isLosslessEnabled) {
                scope.launch(Dispatchers.IO) {
                    val durationSec = if (track.duration > 0) (track.duration / 1000).toInt() else 0
                    val losslessResult = losslessRepo.resolveLosslessStream(track.title, track.artist, durationSec)
                    withContext(Dispatchers.Main) {
                        if (losslessResult != null && currentTrackId == track.id) {
                            Log.i("MUESO_LOSSLESS", "seekToIndex: Playing '${track.title}' via Lossless FLAC directly in ExoPlayer (${losslessResult.bitrateKbps} kbps)")
                            playLosslessTrackInExoPlayer(track, losslessResult)
                            return@withContext
                        }
                        playViaOnlineYouTubePlayer(track)
                    }
                }
                return
            } else {
                playViaOnlineYouTubePlayer(track)
                return
            }
        }

        isPlayingOnline = false
        isPlayingLosslessOnline = false
        currentOnlineTrack = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying = false
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlineDurationMs = 0L
        com.akshay.musicplayer.media.service.MediaSessionBridge.onlinePositionMs = 0L
        ytPlayerManager.pause()
        mediaController?.volume = 1f
        mediaController?.repeatMode = currentRepeatMode

        if (track != null) {
            val ext = track.filePath.substringAfterLast(".", "").uppercase()
            val isFlac = ext == "FLAC"
            val isWav = ext == "WAV"
            val isBitPerfect = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE).getBoolean("bit_perfect_mode", false)
            _activeAudioFormat.value = com.akshay.musicplayer.domain.models.ActiveAudioFormat(
                codec = if (ext.isNotBlank()) ext else "MP3",
                bitDepth = if (isFlac || isWav) 24 else 16,
                sampleRateHz = 44100,
                bitrateKbps = if (isFlac) 1411 else 320,
                isLossless = isFlac || isWav,
                isHiRes = isFlac,
                sourceName = "Local Storage",
                audioOutputDevice = getActiveOutputDeviceName(),
                isBitPerfect = isBitPerfect
            )
        }

        mediaController?.let { controller ->
            val hasMatchingItem = index in 0 until controller.mediaItemCount &&
                    controller.getMediaItemAt(index).mediaId == track?.id?.toString()
            if (hasMatchingItem) {
                currentTrackId = track?.id ?: currentTrackId
                controller.seekToDefaultPosition(index)
                controller.play()
                updatePlaybackState()
            } else if (index in tracksQueue.indices) {
                setPlaylistAndPlay(tracksQueue, index)
            }
        } ?: run {
            if (index in tracksQueue.indices) {
                setPlaylistAndPlay(tracksQueue, index)
            }
        }
    }

    override fun updateTrackInQueue(index: Int, track: TrackEntity) {
        if (index in tracksQueue.indices) {
            val mutable = tracksQueue.toMutableList()
            mutable[index] = track
            tracksQueue = mutable
        }

        if (isPlayingOnline) return

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

        if (isPlayingOnline) return

        val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
        val action: () -> Unit = {
            mediaController?.let { controller ->
                Log.d("MUESO_SYNC", "ExoPlayer appendTracksToQueue: appending ${tracks.size} new tracks")
                val newMediaItems = tracks.map { track ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(getBestArtworkUri(track))
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(if (isOnlineTrack(track)) silenceUri else Uri.parse(track.filePath))
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

        if (isPlayingOnline) return

        val silenceUri = Uri.parse("android.resource://${context.packageName}/${com.akshay.musicplayer.R.raw.silence}")
        val action: () -> Unit = {
            mediaController?.let { controller ->
                val safeIndex = index.coerceIn(0, controller.mediaItemCount)
                Log.d("MUESO_SYNC", "ExoPlayer insertTracksToQueue: inserting ${tracks.size} tracks at index $safeIndex")
                val newMediaItems = tracks.map { track ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(getBestArtworkUri(track))
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(if (isOnlineTrack(track)) silenceUri else Uri.parse(track.filePath))
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

        if (isPlayingOnline) return

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
        com.akshay.musicplayer.media.service.MediaSessionBridge.onNextRequested = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPreviousRequested = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPlayRequested = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.onPauseRequested = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.hasNextItem = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.hasPreviousItem = null
        com.akshay.musicplayer.media.service.MediaSessionBridge.onSeekRequested = null

        ytPlayerManager.release()
        positionUpdateJob?.cancel()
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                Log.w("ExoPlayerController", "Failed to unregister noisy receiver during release", e)
            }
        }
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
}
