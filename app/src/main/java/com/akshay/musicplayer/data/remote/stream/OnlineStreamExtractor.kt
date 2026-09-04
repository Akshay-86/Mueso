package com.akshay.musicplayer.data.remote.stream

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

object OnlineStreamExtractor {
    private const val TAG = "MUESO_STREAM_EXTRACTOR"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val streamCache = ConcurrentHashMap<String, String>()
    private val resolveMutex = Mutex()
    private val pendingRequests = ConcurrentHashMap<String, Continuation<String?>>()

    private var persistentWebView: WebView? = null
    @Volatile
    private var isBundleLoaded = false

    fun cacheStreamUrl(videoId: String, url: String) {
        if (videoId.isNotBlank() && url.isNotBlank()) {
            val cleanUrl = cleanStreamUrl(url)
            streamCache[videoId] = cleanUrl
            val clen = extractClen(cleanUrl)
            Log.d(TAG, "Cached stream URL for videoId=$videoId (clen=${if (clen != null) "${clen / (1024*1024)}MB" else "unknown"})")
        }
    }

    fun isStreamUrlValid(url: String): Boolean {
        if (url.isBlank() || !url.startsWith("http")) return false
        val expireStr = try {
            Uri.parse(url).getQueryParameter("expire")
        } catch (_: Exception) { null }
        if (expireStr != null) {
            val expireSec = expireStr.toLongOrNull() ?: 0L
            val currentSec = System.currentTimeMillis() / 1000L
            if (currentSec >= expireSec - 60L) {
                return false
            }
        }
        return true
    }

    fun getCachedStreamUrl(videoId: String): String? {
        val url = streamCache[videoId] ?: return null
        if (!isStreamUrlValid(url)) {
            streamCache.remove(videoId)
            return null
        }
        return url
    }

    fun invalidateCache(videoId: String) {
        streamCache.remove(videoId)
    }

    fun cleanStreamUrl(url: String): String {
        return url.replace(Regex("[?&]range=[0-9]+-[0-9]+"), "")
            .replace(Regex("[?&]rn=[0-9]+"), "")
            .replace(Regex("[?&]rbuf=[0-9]+"), "")
    }

    fun extractClen(url: String): Long? {
        return try {
            Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureInitialized(context: Context) {
        if (persistentWebView != null) return

        try {
            val wv = WebView(context.applicationContext)
            persistentWebView = wv

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            class AndroidBridge {
                @JavascriptInterface
                fun onReady() {
                    Log.d(TAG, "Headless resolver JS bundle loaded and ready")
                    isBundleLoaded = true
                }

                @JavascriptInterface
                fun onResolved(id: String, streamUrl: String, mimeType: String) {
                    Log.d(TAG, "JS resolver successfully resolved audio stream for $id (mime=$mimeType)")
                    val clean = cleanStreamUrl(streamUrl)
                    cacheStreamUrl(id, clean)
                    val cont = pendingRequests.remove(id)
                    cont?.resume(clean)
                }

                @JavascriptInterface
                fun onError(id: String, error: String) {
                    Log.w(TAG, "JS resolver error for $id: $error")
                    val cont = pendingRequests.remove(id)
                    cont?.resume(null)
                }
            }

            wv.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                    Log.d("MUESO_WV_CONSOLE", "${cm?.message()} (${cm?.sourceId()}:${cm?.lineNumber()})")
                    return true
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    if (reqUrl.contains("yt_resolver.bundle.js")) {
                        return try {
                            val stream = context.assets.open("yt_resolver.bundle.js")
                            WebResourceResponse("application/javascript", "UTF-8", stream)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load bundle from assets", e)
                            null
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("if (window.YtResolver && window.AndroidBridge) { window.AndroidBridge.onReady(); }", null)
                }
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <script src="https://www.youtube.com/yt_resolver.bundle.js" onload="if (window.AndroidBridge && window.AndroidBridge.onReady) window.AndroidBridge.onReady();"></script>
                </head>
                <body>
                    <script>
                        window.resolveTrack = async function(videoId) {
                            try {
                                let attempts = 0;
                                while (!window.YtResolver && attempts < 50) {
                                    await new Promise(r => setTimeout(r, 100));
                                    attempts++;
                                }
                                if (!window.YtResolver) {
                                    throw new Error("YtResolver bundle not loaded after 5s");
                                }
                                console.log("[YT_RESOLVER] resolving for " + videoId);
                                const result = await window.YtResolver.resolveDownloadUrl(videoId);
                                console.log("[YT_RESOLVER] resolved successfully for " + videoId);
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onResolved(videoId, result.url, result.mimeType || "audio/mp4");
                                }
                            } catch (e) {
                                console.error("[YT_RESOLVER] resolveTrack error: " + (e.stack || e.message || e));
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onError(videoId, e.message || String(e));
                                }
                            }
                        };
                        window.addEventListener("load", function() {
                            if (window.YtResolver && window.AndroidBridge && window.AndroidBridge.onReady) {
                                window.AndroidBridge.onReady();
                            }
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()

            wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            Log.d(TAG, "Persistent headless resolver WebView created and loading bundle")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing headless resolver WebView", e)
        }
    }

    /**
     * Resolves the playable audio stream URL for [videoId].
     * 1. Checks in-memory cache first.
     * 2. Executes the bundled YouTube/BotGuard/Innertube decipherer inside a persistent headless WebView.
     */
    suspend fun extractAudioStream(context: Context, videoId: String): String? {
        if (videoId.isBlank()) return null

        // 1. Instant check in cache
        val cached = streamCache[videoId]
        if (!cached.isNullOrBlank()) {
            Log.d(TAG, "Found in-memory cached stream for $videoId")
            return cached
        }

        // 2. Headless WebView extraction via bundled Innertube + BotGuard (one at a time, matching Zuno's withDownloadLock)
        return resolveMutex.withLock {
            val cachedInLock = streamCache[videoId]
            if (!cachedInLock.isNullOrBlank()) {
                return@withLock cachedInLock
            }

            withContext(Dispatchers.Main) {
                ensureInitialized(context)

                // Wait up to 5s if bundle is still loading for the very first time
                var waitCount = 0
                while (!isBundleLoaded && waitCount < 50) {
                    delay(100L)
                    waitCount++
                }

                withTimeoutOrNull(25000L) {
                    suspendCancellableCoroutine { continuation ->
                        pendingRequests[videoId] = continuation
                        continuation.invokeOnCancellation {
                            pendingRequests.remove(videoId)
                        }

                        persistentWebView?.evaluateJavascript("window.resolveTrack('$videoId');", null)
                    }
                }
            }
        }
    }
}
