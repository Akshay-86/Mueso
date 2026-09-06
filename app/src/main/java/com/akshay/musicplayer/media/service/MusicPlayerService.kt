@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.media.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.akshay.musicplayer.R

import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Create notification channel early so the system has it before any notification is posted
        createNotificationChannel()

        // Configure the notification provider with a proper monochrome small icon
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .header("User-Agent", userAgent)
                chain.proceed(requestBuilder.build())
            }
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .build()


        val intent = android.content.Intent(this, com.akshay.musicplayer.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = MusicForwardingPlayer(player, audioAttributes)
        val bitmapLoader = CoilBitmapLoader(this)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(bitmapLoader)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
                    val playerCommands = session.player.availableCommands.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_PREPARE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                        .add(Player.COMMAND_SET_MEDIA_ITEM)
                        .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .build()
                    return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val p = mediaSession.player
                    val count = p.mediaItemCount
                    return if (count > 0) {
                        val items = (0 until count).map { p.getMediaItemAt(it) }
                        val startIndex = p.currentMediaItemIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
                        val startPosition = p.currentPosition.coerceAtLeast(0L)
                        Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(items, startIndex, startPosition)
                        )
                    } else {
                        Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                        )
                    }
                }
            })
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for the currently playing music"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
    }
}

object MediaSessionBridge {
    @Volatile var isOnlinePlaying: Boolean = false
    @Volatile var onlineDurationMs: Long = 0L
    @Volatile var onlinePositionMs: Long = 0L
    var onSeekRequested: ((Long) -> Unit)? = null
}

class MusicForwardingPlayer(
    private val exoPlayer: ExoPlayer,
    private val audioAttributes: AudioAttributes
) : androidx.media3.common.ForwardingPlayer(exoPlayer) {

    private fun updateAudioFocus() {
        if (MediaSessionBridge.isOnlinePlaying) {
            exoPlayer.setAudioAttributes(audioAttributes, false)
        } else {
            exoPlayer.setAudioAttributes(audioAttributes, true)
        }
    }

    override fun play() {
        updateAudioFocus()
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            updateAudioFocus()
        }
        super.setPlayWhenReady(playWhenReady)
    }

    override fun getDuration(): Long {
        if (MediaSessionBridge.isOnlinePlaying) {
            val d = MediaSessionBridge.onlineDurationMs
            return if (d > 0L) d else androidx.media3.common.C.TIME_UNSET
        }
        return super.getDuration()
    }

    override fun getContentDuration(): Long {
        if (MediaSessionBridge.isOnlinePlaying) {
            val d = MediaSessionBridge.onlineDurationMs
            return if (d > 0L) d else androidx.media3.common.C.TIME_UNSET
        }
        return super.getContentDuration()
    }

    override fun getCurrentPosition(): Long {
        if (MediaSessionBridge.isOnlinePlaying) {
            return MediaSessionBridge.onlinePositionMs
        }
        return super.getCurrentPosition()
    }

    override fun getContentPosition(): Long {
        if (MediaSessionBridge.isOnlinePlaying) {
            return MediaSessionBridge.onlinePositionMs
        }
        return super.getContentPosition()
    }

    override fun getCurrentTimeline(): androidx.media3.common.Timeline {
        val baseTimeline = super.getCurrentTimeline()
        if (MediaSessionBridge.isOnlinePlaying && MediaSessionBridge.onlineDurationMs > 0L && !baseTimeline.isEmpty) {
            return object : androidx.media3.common.Timeline() {
                override fun getWindowCount(): Int = baseTimeline.windowCount
                override fun getPeriodCount(): Int = baseTimeline.periodCount
                override fun getIndexOfPeriod(uid: Any): Int = baseTimeline.getIndexOfPeriod(uid)
                override fun getUidOfPeriod(periodIndex: Int): Any = baseTimeline.getUidOfPeriod(periodIndex)
                override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                    val p = baseTimeline.getPeriod(periodIndex, period, setIds)
                    p.durationUs = MediaSessionBridge.onlineDurationMs * 1000L
                    return p
                }
                override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                    val w = baseTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                    w.durationUs = MediaSessionBridge.onlineDurationMs * 1000L
                    return w
                }
            }
        }
        return baseTimeline
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (MediaSessionBridge.isOnlinePlaying) {
            MediaSessionBridge.onlinePositionMs = positionMs
            MediaSessionBridge.onSeekRequested?.invoke(positionMs)
            return
        }
        super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekTo(positionMs: Long) {
        if (MediaSessionBridge.isOnlinePlaying) {
            MediaSessionBridge.onlinePositionMs = positionMs
            MediaSessionBridge.onSeekRequested?.invoke(positionMs)
            return
        }
        super.seekTo(positionMs)
    }
}
