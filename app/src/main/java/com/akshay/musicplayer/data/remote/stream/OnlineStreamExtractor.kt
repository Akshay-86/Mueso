package com.akshay.musicplayer.data.remote.stream

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

object OnlineStreamExtractor {
    private const val TAG = "MUESO_STREAM_EXTRACTOR"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val streamCache = ConcurrentHashMap<String, String>()

    fun cacheStreamUrl(videoId: String, url: String) {
        if (videoId.isNotBlank() && url.isNotBlank()) {
            val cleanUrl = cleanStreamUrl(url)
            streamCache[videoId] = cleanUrl
            val clen = extractClen(cleanUrl)
            Log.d(TAG, "Cached stream URL for videoId=$videoId (clen=${if (clen != null) "${clen / (1024*1024)}MB" else "unknown"})")
        }
    }

    fun getCachedStreamUrl(videoId: String): String? {
        return streamCache[videoId]
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

    /**
     * Resolves the playable audio stream URL for [videoId].
     * 1. Checks in-memory cache first.
     * 2. Executes the bundled YouTube/BotGuard/Innertube decipherer inside a headless WebView.
     */
    suspend fun extractAudioStream(context: Context, videoId: String): String? {
        if (videoId.isBlank()) return null

        // 1. Instant check in cache
        val cached = streamCache[videoId]
        if (!cached.isNullOrBlank()) {
            Log.d(TAG, "Found in-memory cached stream for $videoId")
            return cached
        }

        // 2. Headless WebView extraction via bundled Innertube + BotGuard
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(15000L) {
                suspendCancellableCoroutine { continuation ->
                    var webView: WebView? = null
                    var isFinished = false

                    fun finish(result: String?) {
                        if (isFinished) return
                        isFinished = true
                        mainHandler.post {
                            try {
                                webView?.stopLoading()
                                webView?.destroy()
                            } catch (_: Exception) {}
                            webView = null
                        }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    continuation.invokeOnCancellation {
                        finish(null)
                    }

                    try {
                        val wv = WebView(context.applicationContext)
                        webView = wv

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
                            fun onResolved(id: String, streamUrl: String, mimeType: String) {
                                Log.d(TAG, "JS resolver successfully resolved audio stream for $id (mime=$mimeType)")
                                val clean = cleanStreamUrl(streamUrl)
                                cacheStreamUrl(id, clean)
                                finish(clean)
                            }

                            @JavascriptInterface
                            fun onError(id: String, error: String) {
                                Log.w(TAG, "JS resolver reported error for $id: $error")
                                finish(null)
                            }

                            @JavascriptInterface
                            fun onReady() {
                                mainHandler.post {
                                    wv.evaluateJavascript("resolveTrack('$videoId');", null)
                                }
                            }
                        }

                        wv.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

                        wv.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                if (reqUrl.contains("yt_resolver.bundle.js")) {
                                    return try {
                                        val stream = context.assets.open("yt_resolver.bundle.js")
                                        android.webkit.WebResourceResponse("application/javascript", "UTF-8", stream)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to load bundle from assets", e)
                                        null
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("if (window.resolveTrack) resolveTrack('$videoId');", null)
                            }
                        }

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <script src="https://www.youtube.com/yt_resolver.bundle.js"></script>
                            </head>
                            <body>
                                <script>
                                    window.resolveTrack = async function(videoId) {
                                        try {
                                            if (!window.YtResolver) {
                                                if (window.AndroidBridge) window.AndroidBridge.onError(videoId, "YtResolver bundle not loaded");
                                                return;
                                            }
                                            const result = await window.YtResolver.resolveDownloadUrl(videoId);
                                            if (window.AndroidBridge) {
                                                window.AndroidBridge.onResolved(videoId, result.url, result.mimeType || "audio/mp4");
                                            }
                                        } catch (e) {
                                            if (window.AndroidBridge) {
                                                window.AndroidBridge.onError(videoId, e.message || String(e));
                                            }
                                        }
                                    };
                                    if (window.AndroidBridge && window.AndroidBridge.onReady) {
                                        window.AndroidBridge.onReady();
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting headless JS resolver for $videoId", e)
                        finish(null)
                    }
                }
            }
        }
    }
}
