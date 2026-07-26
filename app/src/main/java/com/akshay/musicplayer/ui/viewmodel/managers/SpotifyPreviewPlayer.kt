package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpotifyPreviewPlayer(
    private val context: Context,
    private val onlineRepository: OnlineMusicRepository,
    private val scope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null

    private val _previewTrackId = MutableStateFlow<Long?>(null)
    val previewTrackId: StateFlow<Long?> = _previewTrackId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var loadJob: Job? = null

    private fun ensurePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        _isLoading.value = false
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY && exoPlayer?.playWhenReady == true) {
                        _isLoading.value = false
                    }
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _isLoading.value = false
                        _previewTrackId.value = null
                    }
                }
            })
        }.also { exoPlayer = it }
    }

    fun togglePreview(track: TrackEntity) {
        if (_previewTrackId.value == track.id && _isPlaying.value) {
            pause()
            return
        }

        if (_previewTrackId.value == track.id && !_isPlaying.value && exoPlayer?.playbackState == androidx.media3.common.Player.STATE_READY) {
            exoPlayer?.play()
            return
        }

        playPreview(track)
    }

    fun playPreview(track: TrackEntity) {
        stop()
        _previewTrackId.value = track.id
        _isLoading.value = true

        loadJob = scope.launch(Dispatchers.IO) {
            try {
                val streamUrl = if (track.filePath.startsWith("online:")) {
                    val videoId = track.filePath.removePrefix("online:")
                    onlineRepository.getStreamUrl(videoId)
                } else {
                    track.filePath
                }

                if (streamUrl.isBlank() || streamUrl.startsWith("online:")) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _previewTrackId.value = null
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val player = ensurePlayer()
                    player.setMediaItem(MediaItem.fromUri(streamUrl))
                    player.prepare()
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _previewTrackId.value = null
                }
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    fun stop() {
        loadJob?.cancel()
        loadJob = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _isLoading.value = false
        _isPlaying.value = false
        _previewTrackId.value = null
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
