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
            .setAudioAttributes(audioAttributes, false) // Do not request audio focus, so it does not revoke focus from Chromium WebView
            .setHandleAudioBecomingNoisy(false)
            .build()


        val intent = android.content.Intent(this, com.akshay.musicplayer.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = MusicForwardingPlayer(player)
        val bitmapLoader = CoilBitmapLoader(this)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(bitmapLoader)
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    if (playerCommand == androidx.media3.common.Player.COMMAND_PLAY_PAUSE) {
                        session.player.playWhenReady = !session.player.playWhenReady
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
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

class MusicForwardingPlayer(player: androidx.media3.common.Player) : androidx.media3.common.ForwardingPlayer(player) {
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
