package com.akshay.musicplayer.media.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class OnlineYouTubePlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "MUESO_YT_PLAYER"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var playerView: YouTubePlayerView? = null
    private var activePlayer: YouTubePlayer? = null
    private var isReady = false
    private var pendingVideoId: String? = null
    private var pendingStartSeconds: Float = 0f
    private var pendingCueOnly: Boolean = false
    private var isLoadingNewVideo = false

    var currentVideoId: String? = null
        private set

    var isPlaying: Boolean = false
        private set

    var durationMs: Long = 0L
        private set

    var currentPositionMs: Long = 0L
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onPositionUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onNetworkError: ((videoId: String, positionSec: Float) -> Unit)? = null

    init {
        initialize()
    }

    fun initialize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { initialize() }
            return
        }

        if (playerView != null) return

        try {
            val view = YouTubePlayerView(context)
            view.enableAutomaticInitialization = false
            view.enableBackgroundPlayback(true)

            // Configure YouTube IFrame Player options according to Zuno's reference:
            val builder = IFramePlayerOptions.Builder(context)
                .controls(0)
                .autoplay(1)

            try {
                val field = builder.javaClass.getDeclaredField("builderOptions")
                field.isAccessible = true
                val json = field.get(builder) as org.json.JSONObject
                json.put("widget_referrer", "https://music.youtube.com/")
                json.put("playsinline", 1)
                json.put("disablekb", 1)
                json.put("rel", 0)
                json.put("iv_load_policy", 3)
                json.put("modestbranding", 1)
                json.put("showinfo", 0)
                json.put("fs", 0)
                json.put("autohide", 1)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inject widget_referrer", e)
            }

            val options = builder.build()

            // Find internal WebView and set appropriate settings
            findWebView(view)?.let { wv ->
                wv.settings.apply {
                    domStorageEnabled = true
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }
                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        if (reqUrl.contains("googlevideo.com/videoplayback")) {
                            val vid = currentVideoId
                            if (vid != null) {
                                val isAudio = reqUrl.contains("mime=audio") ||
                                        reqUrl.contains("itag=140") ||
                                        reqUrl.contains("itag=251") ||
                                        reqUrl.contains("itag=139")
                                if (isAudio) {
                                    Log.d("MUESO_STREAM_CAPTURE", "Active player audio stream active for $vid")
                                    com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.cacheStreamUrl(vid, reqUrl)
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val isOnline = com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()
                        if (!isOnline && currentVideoId != null) {
                            Log.w(TAG, "WebView onReceivedError without network: ${error?.description}")
                            onNetworkError?.invoke(currentVideoId ?: "", (currentPositionMs / 1000f).coerceAtLeast(0f))
                        }
                    }
                }
                wv.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(cm: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("MUESO_WV_CONSOLE", "${cm?.message()} (${cm?.sourceId()}:${cm?.lineNumber()})")
                        return super.onConsoleMessage(cm)
                    }
                }
            }

            view.initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    Log.d(TAG, "YouTubePlayer is ready!")
                    activePlayer = youTubePlayer
                    isReady = true

                    // Apply allow attributes on the embedded iframe and clean YT embed UI styles.
                    // Also size to 200x200 and enforce 'tiny' (144p) quality to eliminate hardware VP9 frame rendering in headless mode.
                    findWebView(view)?.evaluateJavascript(
                        """
                        (function() {
                            var ifr = document.querySelector('iframe');
                            if (ifr) {
                                ifr.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture');
                                ifr.style.width = '200px';
                                ifr.style.height = '200px';
                            }
                            var style = document.createElement('style');
                            style.innerHTML = `
                                iframe, #youTubePlayerDOM {
                                    width: 200px !important;
                                    height: 200px !important;
                                    opacity: 0.01 !important;
                                    pointer-events: none !important;
                                }
                                video, .html5-video-player video {
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                }
                                .ytp-chrome-top, .ytp-title, .ytp-watermark, .ytp-pause-overlay,
                                .ytp-ce-element, .ytp-ce-covering-overlay, .ytp-cards-teaser,
                                .ytp-show-cards-title, .ytp-share-button, .ytp-youtube-button,
                                .ytp-gradient-top, .ytp-gradient-bottom, .ytp-title-channel {
                                    display: none !important;
                                    opacity: 0 !important;
                                    visibility: hidden !important;
                                    pointer-events: none !important;
                                }
                            `;
                            document.head.appendChild(style);
                            if (typeof player !== 'undefined' && player && player.setPlaybackQuality) {
                                player.setPlaybackQuality('tiny');
                            }
                        })();
                        """.trimIndent(), null
                    )

                    val pending = pendingVideoId
                    if (pending != null) {
                        val isCue = pendingCueOnly
                        pendingVideoId = null
                        pendingCueOnly = false
                        if (isCue) {
                            cueVideoInternal(pending, pendingStartSeconds)
                        } else {
                            loadVideoInternal(pending, pendingStartSeconds)
                        }
                    }
                }

                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    Log.d(TAG, "YouTubePlayer onStateChange: $state (video: $currentVideoId, loading: $isLoadingNewVideo, isPlaying: $isPlaying)")
                    when (state) {
                        PlayerConstants.PlayerState.PLAYING -> {
                            isLoadingNewVideo = false
                            isPlaying = true
                            findWebView(view)?.evaluateJavascript(
                                "if (typeof player !== 'undefined' && player && player.setPlaybackQuality) { player.setPlaybackQuality('tiny'); }",
                                null
                            )
                            onStateChanged?.invoke(true)
                        }
                        PlayerConstants.PlayerState.VIDEO_CUED -> {
                            if (isLoadingNewVideo || isPlaying) {
                                Log.d(TAG, "YouTubePlayer VIDEO_CUED -> auto-starting playback for $currentVideoId")
                                youTubePlayer.play()
                            }
                        }
                        PlayerConstants.PlayerState.PAUSED -> {
                            if (isLoadingNewVideo) {
                                Log.d(TAG, "Ignoring transient PAUSED state during video load for $currentVideoId")
                                youTubePlayer.play()
                                return
                            }
                            isPlaying = false
                            onStateChanged?.invoke(false)
                        }
                        PlayerConstants.PlayerState.ENDED -> {
                            if (isLoadingNewVideo) {
                                Log.d(TAG, "Ignoring stale ENDED state during video load for $currentVideoId")
                                return
                            }
                            Log.d(TAG, "YouTubePlayer onStateChange: ENDED (video: $currentVideoId) -> advancing to next track")
                            isLoadingNewVideo = false
                            isPlaying = false
                            onTrackEnded?.invoke()
                        }
                        else -> {}
                    }
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    currentPositionMs = (second * 1000).toLong()
                    onPositionUpdate?.invoke(currentPositionMs, durationMs)
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                    durationMs = (duration * 1000).toLong()
                    Log.d(TAG, "YouTubePlayer onVideoDuration: ${duration}s ($durationMs ms)")
                    onPositionUpdate?.invoke(currentPositionMs, durationMs)
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    if (currentVideoId == null) {
                        // Ignore harmless initial ready check error before any video has been loaded
                        return
                    }
                    val isNetworkDown = !com.akshay.musicplayer.data.remote.NetworkMonitor.isConnected()
                    val isLikelyNetworkIssue = isNetworkDown ||
                            error == PlayerConstants.PlayerError.HTML_5_PLAYER ||
                            error.name.contains("NETWORK", ignoreCase = true)

                    Log.e(TAG, "YouTubePlayer onError: $error (isNetworkDown=$isNetworkDown) for video $currentVideoId")
                    if (isLikelyNetworkIssue) {
                        onNetworkError?.invoke(currentVideoId ?: "", (currentPositionMs / 1000f).coerceAtLeast(0f))
                        return
                    }
                    onError?.invoke(error.name)
                }
            }, options)

            playerView = view
            Log.d(TAG, "YouTubePlayerView successfully created and initialized with background playback")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YouTubePlayerView", e)
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findWebView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun cueVideo(videoId: String, startSeconds: Float = 0f) {
        currentVideoId = videoId
        durationMs = 0L
        currentPositionMs = (startSeconds * 1000).toLong()
        isLoadingNewVideo = false
        isPlaying = false

        mainHandler.post {
            if (!isReady || activePlayer == null) {
                Log.d(TAG, "Player not ready yet, queuing cue videoId: $videoId at ${startSeconds}s")
                pendingVideoId = videoId
                pendingStartSeconds = startSeconds
                pendingCueOnly = true
                if (playerView == null) {
                    initialize()
                }
                return@post
            }
            cueVideoInternal(videoId, startSeconds)
        }
    }

    private fun cueVideoInternal(videoId: String, startSeconds: Float) {
        try {
            Log.d(TAG, "Cueing video: $videoId at ${startSeconds}s")
            activePlayer?.cueVideo(videoId, startSeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Error cueing video: $videoId", e)
        }
    }

    fun playVideo(videoId: String, startSeconds: Float = 0f) {
        currentVideoId = videoId
        durationMs = 0L
        currentPositionMs = (startSeconds * 1000).toLong()
        pendingCueOnly = false
        isLoadingNewVideo = true
        isPlaying = true

        mainHandler.post {
            if (!isReady || activePlayer == null) {
                Log.d(TAG, "Player not ready yet, queuing videoId: $videoId at ${startSeconds}s")
                pendingVideoId = videoId
                pendingStartSeconds = startSeconds
                pendingCueOnly = false
                if (playerView == null) {
                    initialize()
                }
                return@post
            }
            loadVideoInternal(videoId, startSeconds)
        }
    }

    fun reloadAndPlay(videoId: String, startSeconds: Float = 0f) {
        currentVideoId = videoId
        durationMs = 0L
        currentPositionMs = (startSeconds * 1000).toLong()
        pendingCueOnly = false
        isLoadingNewVideo = true
        isPlaying = true

        mainHandler.post {
            if (playerView == null || !isReady || activePlayer == null) {
                Log.d(TAG, "reloadAndPlay: player not ready, initializing fresh for $videoId at ${startSeconds}s")
                initialize()
                playVideo(videoId, startSeconds)
                return@post
            }

            playerView?.let { pv ->
                findWebView(pv)?.let { wv ->
                    Log.d(TAG, "reloadAndPlay: issuing JS loadVideoById for $videoId at ${startSeconds}s")
                    wv.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof player !== 'undefined' && player && player.loadVideoById) {
                                    player.loadVideoById({videoId: '$videoId', startSeconds: $startSeconds});
                                    if (player.setPlaybackQuality) player.setPlaybackQuality('tiny');
                                    player.playVideo();
                                    return true;
                                }
                            } catch(e) {}
                            return false;
                        })();
                        """.trimIndent()
                    ) { result ->
                        if (result != "true") {
                            Log.d(TAG, "JS loadVideoById returned $result, falling back to activePlayer.loadVideo")
                            loadVideoInternal(videoId, startSeconds)
                        }
                    }
                } ?: loadVideoInternal(videoId, startSeconds)
            } ?: loadVideoInternal(videoId, startSeconds)
        }
    }

    private fun loadVideoInternal(videoId: String, startSeconds: Float) {
        try {
            Log.d(TAG, "Loading and playing video: $videoId at ${startSeconds}s")
            activePlayer?.loadVideo(videoId, startSeconds)
            activePlayer?.play()
            playerView?.let { pv ->
                findWebView(pv)?.evaluateJavascript(
                    """
                    (function() {
                        var v = document.querySelector('video');
                        if (v) {
                            v.style.visibility = 'hidden';
                            v.style.opacity = '0';
                        }
                        if (typeof player !== 'undefined' && player) {
                            if (player.setPlaybackQuality) player.setPlaybackQuality('tiny');
                            if (player.playVideo) player.playVideo();
                        }
                    })();
                    """.trimIndent(), null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading video: $videoId", e)
        }
    }

    fun play() {
        isLoadingNewVideo = false
        mainHandler.post {
            activePlayer?.play()
            isPlaying = true
            onStateChanged?.invoke(true)
        }
    }

    fun pause() {
        isLoadingNewVideo = false
        mainHandler.post {
            activePlayer?.pause()
            isPlaying = false
            onStateChanged?.invoke(false)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(seconds: Float) {
        currentPositionMs = (seconds * 1000).toLong()
        mainHandler.post {
            activePlayer?.seekTo(seconds)
        }
    }

    fun release() {
        mainHandler.post {
            try {
                playerView?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing playerView", e)
            }
            playerView = null
            activePlayer = null
            isReady = false
            currentVideoId = null
            isPlaying = false
        }
    }
}
