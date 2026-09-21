@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.media.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var isForegroundServiceStarted = false
    private var isServiceDestroying = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())


    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Mueso:PlaybackWakeLock")?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Failed to acquire wakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Failed to release wakeLock", e)
        }
    }

    private var isNoisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d("MusicPlayerService", "noisyReceiver received action: $action")
            when (action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.i("MusicPlayerService", "ACTION_AUDIO_BECOMING_NOISY -> pausing playback")
                    handleBecomingNoisy()
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_CONNECTED
                    )
                    if (state == BluetoothProfile.STATE_DISCONNECTED ||
                        state == BluetoothProfile.STATE_DISCONNECTING) {
                        Log.i("MusicPlayerService", "Bluetooth A2DP disconnected/disconnecting -> pausing playback")
                        handleBecomingNoisy()
                    }
                }
            }
        }
    }

    private fun handleBecomingNoisy() {
        try {
            if (MediaSessionBridge.isOnlinePlaying) {
                MediaSessionBridge.onPauseRequested?.invoke()
            }
            mediaSession?.player?.pause()
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error pausing on becoming noisy: ${e.message}", e)
        }
    }

    private fun registerNoisyReceiver() {
        if (isNoisyReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isNoisyReceiverRegistered = true
            Log.d("MusicPlayerService", "Successfully registered audio becoming noisy & bluetooth receiver")
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Failed to register noisy receiver", e)
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!isNoisyReceiverRegistered) return
        try {
            unregisterReceiver(noisyReceiver)
            isNoisyReceiverRegistered = false
            Log.d("MusicPlayerService", "Successfully unregistered noisy receiver")
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Failed to unregister noisy receiver", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MediaSessionBridge.isServiceRunning = true
        registerNoisyReceiver()

        // Create notification channel early so the system has it before any notification is posted
        createNotificationChannel()

        // Configure the notification provider with a proper monochrome small icon and lock screen visibility
        val defaultProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        defaultProvider.setSmallIcon(R.drawable.ic_notification)

        val customNotificationProvider = object : androidx.media3.session.MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
                onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback
            ): androidx.media3.session.MediaNotification {
                // Safe callback for asynchronous bitmap loading:
                // Instead of letting MediaNotificationManager handle bitmap completion via onNotificationChanged
                // (which calls startForegroundService() and crashes when backgrounded on Android 12+ with
                // ForegroundServiceStartNotAllowedException), we directly update the notification via NotificationManager.
                val safeCallback = androidx.media3.session.MediaNotification.Provider.Callback { notification ->
                    try {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val notif = notification.notification
                        notif.visibility = android.app.Notification.VISIBILITY_PUBLIC
                        notificationManager.notify(notification.notificationId, notif)
                    } catch (e: Exception) {
                        Log.w("MusicPlayerService", "Failed to update notification with bitmap: ${e.message}")
                    }
                }

                val mediaNotification = defaultProvider.createNotification(
                    mediaSession,
                    customLayout,
                    actionFactory,
                    safeCallback
                )
                val notif = mediaNotification.notification
                notif.visibility = android.app.Notification.VISIBILITY_PUBLIC
                return mediaNotification
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: android.os.Bundle
            ): Boolean {
                return defaultProvider.handleCustomCommand(session, action, extras)
            }
        }
        setMediaNotificationProvider(customNotificationProvider)

        setListener(object : MediaSessionService.Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Log.w("MusicPlayerService", "Foreground service start not allowed in background - handled gracefully without crash")
            }
        })

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .dns(com.akshay.musicplayer.data.remote.stream.GoogleVideoDns())
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val url = originalRequest.url
                val urlStr = url.toString()
                val requestBuilder = originalRequest.newBuilder()

                if (urlStr.contains("googlevideo.com")) {
                    val ipParam = url.queryParameter("ip")
                    val family = when {
                        ipParam == null -> null
                        ipParam.contains(":") -> 6
                        else -> 4
                    }
                    val isMobile = urlStr.contains("c=IOS") || urlStr.contains("c=ANDROID")
                    val ua = when {
                        urlStr.contains("c=IOS") -> "com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)"
                        urlStr.contains("c=ANDROID") -> "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip"
                        urlStr.contains("c=TVHTML5") -> "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
                        else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    }
                    requestBuilder.header("User-Agent", ua)
                    if (isMobile) {
                        // Mobile clients must not send browser origin/referer to googlevideo CDN
                        requestBuilder.removeHeader("Origin")
                        requestBuilder.removeHeader("Referer")
                    } else {
                        requestBuilder.header("Origin", "https://music.youtube.com")
                        requestBuilder.header("Referer", "https://music.youtube.com/")
                    }

                    // ExoPlayer does not set Range header when loading from start (position 0).
                    // Ensuring a Range header is present avoids CDN 403 errors.
                    if (originalRequest.header("Range") == null) {
                        requestBuilder.header("Range", "bytes=0-")
                    }

                    val finalReq = requestBuilder.build()
                    val response = com.akshay.musicplayer.data.remote.stream.GoogleVideoDnsHelper.withPreferredFamily(family) {
                        chain.proceed(finalReq)
                    }
                    if (!response.isSuccessful && response.code == 403) {
                        val errBody = try { response.peekBody(1024).string() } catch (_: Exception) { "none" }
                        android.util.Log.w("MUESO_HTTP", "HTTP 403 from googlevideo! Family=$family, IP=$ipParam, UA=$ua, Body: $errBody")
                        com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.clearAllCache()
                    }
                    response
                } else {
                    requestBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    val finalReq = requestBuilder.build()
                    chain.proceed(finalReq)
                }
            }
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .build()

        val extractorsFactory = com.akshay.musicplayer.media.player.ClearDrmExtractorsFactory()
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger())

        val effectsController = com.akshay.musicplayer.media.player.AudioEffectsController.getInstance(this)
        effectsController.attachSession(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effectsController.attachSession(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    acquireWakeLock()
                } else if (!MediaSessionBridge.isOnlinePlaying) {
                    releaseWakeLock()
                }
            }
        })

        MediaSessionBridge.onOnlinePlayingChanged = { isOnline ->
            if (isOnline) {
                acquireWakeLock()
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else if (!player.isPlaying) {
                releaseWakeLock()
            }
        }


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
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_PREPARE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                        .add(Player.COMMAND_SET_MEDIA_ITEM)
                        .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_MEDIA_ITEMS_METADATA)
                        .build()
                    return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    if (controller.packageName == packageName) {
                        MediaSessionBridge.isSyncing = true
                    }
                    return playerCommand
                }

                override fun onPlayerInteractionFinished(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommands: Player.Commands
                ) {
                    if (controller.packageName == packageName) {
                        mainHandler.postDelayed({
                            MediaSessionBridge.isSyncing = false
                        }, 200L)
                    }
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

        MediaSessionBridge.onQueueOrCommandsChanged = {
            val session = mediaSession
            if (session != null) {
                mainHandler.post {
                    try {
                        onUpdateNotification(session, false)
                    } catch (e: Exception) {
                        Log.w("MusicPlayerService", "Failed to update notification on commands change: ${e.message}")
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                manager.deleteNotificationChannel("music_playback_channel")
                manager.deleteNotificationChannel("music_playback_channel_v2")
            } catch (e: Exception) {
                Log.w("MusicPlayerService", "Could not delete legacy notification channel: ${e.message}")
            }

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Controls for the currently playing music"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Suppressed foreground exception in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        try {
            if (isForegroundServiceStarted) {
                // Service is already in foreground and stopForeground() is overridden to keep it there.
                // Pass startInForegroundRequired = false so MediaNotificationManager safely updates the
                // notification via NotificationManagerCompat.notify() and never calls startForegroundService()
                // from the background looper.
                super.onUpdateNotification(session, false)
            } else {
                try {
                    super.onUpdateNotification(session, startInForegroundRequired)
                    isForegroundServiceStarted = true
                } catch (e: Exception) {
                    Log.w("MusicPlayerService", "Failed to startForeground via MediaNotificationManager, falling back to safe update: ${e.message}")
                    super.onUpdateNotification(session, false)
                }
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "Suppressed foreground exception in onUpdateNotification: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || (!player.playWhenReady && !MediaSessionBridge.isOnlinePlaying)) {
            isServiceDestroying = true
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        isServiceDestroying = true
        unregisterNoisyReceiver()
        MediaSessionBridge.isServiceRunning = false
        releaseWakeLock()
        MediaSessionBridge.onOnlinePlayingChanged = null
        MediaSessionBridge.onQueueOrCommandsChanged = null
        com.akshay.musicplayer.media.player.AudioEffectsController.getInstance(this).release()
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "music_playback_channel_v3"
        private const val NOTIFICATION_ID = 1001
    }
}

object MediaSessionBridge {
    @Volatile var isServiceRunning: Boolean = false
    @Volatile var isOnlinePlaying: Boolean = false
        set(value) {
            field = value
            onOnlinePlayingChanged?.invoke(value)
        }
    @Volatile var isSyncing: Boolean = false
    @Volatile var onlineDurationMs: Long = 0L
    @Volatile var onlinePositionMs: Long = 0L
    var onOnlinePlayingChanged: ((Boolean) -> Unit)? = null
    var onSeekRequested: ((Long) -> Unit)? = null
    var onNextRequested: (() -> Unit)? = null
    var onPreviousRequested: (() -> Unit)? = null
    var onPlayRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var hasNextItem: (() -> Boolean)? = null
    var hasPreviousItem: (() -> Boolean)? = null
    var onQueueOrCommandsChanged: (() -> Unit)? = null
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
        if (MediaSessionBridge.isOnlinePlaying) {
            if (!MediaSessionBridge.isSyncing) {
                MediaSessionBridge.onPlayRequested?.invoke()
            }
            super.play()
            return
        }
        super.play()
    }

    override fun pause() {
        if (MediaSessionBridge.isOnlinePlaying) {
            if (!MediaSessionBridge.isSyncing) {
                MediaSessionBridge.onPauseRequested?.invoke()
            }
            super.pause()
            return
        }
        super.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            updateAudioFocus()
        }
        if (MediaSessionBridge.isOnlinePlaying) {
            if (!MediaSessionBridge.isSyncing) {
                if (playWhenReady) {
                    MediaSessionBridge.onPlayRequested?.invoke()
                } else {
                    MediaSessionBridge.onPauseRequested?.invoke()
                }
            }
            super.setPlayWhenReady(playWhenReady)
            return
        }
        super.setPlayWhenReady(playWhenReady)
    }

    override fun seekToNext() {
        MediaSessionBridge.onNextRequested?.let {
            it.invoke()
            return
        }
        super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        MediaSessionBridge.onNextRequested?.let {
            it.invoke()
            return
        }
        super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        MediaSessionBridge.onPreviousRequested?.let {
            it.invoke()
            return
        }
        super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        MediaSessionBridge.onPreviousRequested?.let {
            it.invoke()
            return
        }
        super.seekToPreviousMediaItem()
    }

    override fun hasNextMediaItem(): Boolean {
        MediaSessionBridge.hasNextItem?.let { return it.invoke() }
        return super.hasNextMediaItem()
    }

    override fun hasPreviousMediaItem(): Boolean {
        MediaSessionBridge.hasPreviousItem?.let { return it.invoke() }
        return super.hasPreviousMediaItem()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return getAvailableCommands().contains(command)
    }

    override fun getAvailableCommands(): Player.Commands {
        val baseCommands = super.getAvailableCommands().buildUpon()
        if (MediaSessionBridge.hasNextItem?.invoke() == true) {
            baseCommands.add(Player.COMMAND_SEEK_TO_NEXT)
            baseCommands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (MediaSessionBridge.hasPreviousItem?.invoke() == true) {
            baseCommands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            baseCommands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if (MediaSessionBridge.isOnlinePlaying) {
            baseCommands.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            baseCommands.add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        }
        baseCommands.add(Player.COMMAND_PLAY_PAUSE)
        baseCommands.add(Player.COMMAND_PREPARE)
        baseCommands.add(Player.COMMAND_STOP)
        return baseCommands.build()
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
