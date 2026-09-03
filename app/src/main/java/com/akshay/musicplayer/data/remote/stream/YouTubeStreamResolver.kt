package com.akshay.musicplayer.data.remote.stream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves YouTube audio stream URLs using multiple InnerTube client strategies.
 *
 * Tries the following clients in order:
 * 1. TVHTML5_SIMPLY_EMBEDDED_PLAYER - Returns pre-signed URLs for embedded content, no login needed
 * 2. WEB (youtube.com, not music.youtube.com) - Works anonymously for regular videos
 * 3. ANDROID_MUSIC with API key - May work for some content
 *
 * Music-specific clients (ANDROID_MUSIC, IOS_MUSIC) require LOGIN for music content,
 * so we prioritize the TV embedded player which doesn't.
 */
class YouTubeStreamResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MUESO_STREAM"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Standard YouTube API key (public, used by youtube.com itself)
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    }

    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    data class CachedStream(
        val url: String,
        val expiryEpochMs: Long
    )

    /**
     * Client strategy definition for trying multiple InnerTube client types.
     */
    private data class ClientStrategy(
        val label: String,
        val apiUrl: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val clientId: Int,
        val extraClientFields: Map<String, Any> = emptyMap(),
        val extraHeaders: Map<String, String> = emptyMap(),
        val thirdParty: JSONObject? = null
    )

    private val strategies = listOf(
        // Strategy 1: VISIONOS (Apple Vision Pro) - Returns direct pre-signed URLs, no cipher, no login, no botguard required!
        ClientStrategy(
            label = "VISIONOS",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "VISIONOS",
            clientVersion = "1.02",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            clientId = 101,
            extraClientFields = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471"
            )
        ),
        // Strategy 2: TV HTML5 Embedded Player
        ClientStrategy(
            label = "TVHTML5_EMBEDDED",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) 85.0.4183.93/6.5 TV Safari/537.36",
            clientId = 85,
            thirdParty = JSONObject().apply {
                put("embedUrl", "https://www.youtube.com")
            }
        ),
        // Strategy 3: Regular WEB client on youtube.com (not music.youtube.com)
        ClientStrategy(
            label = "WEB",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "WEB",
            clientVersion = "2.20241126.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            clientId = 1
        ),
        // Strategy 4: Android client on regular YouTube (not YouTube Music)
        ClientStrategy(
            label = "ANDROID",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID",
            clientVersion = "19.09.37",
            userAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip",
            clientId = 3,
            extraClientFields = mapOf(
                "androidSdkVersion" to 34,
                "platform" to "MOBILE",
                "osName" to "Android",
                "osVersion" to "14"
            )
        ),
        // Strategy 5: iOS client
        ClientStrategy(
            label = "IOS",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "IOS",
            clientVersion = "19.45.4",
            userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
            clientId = 5,
            extraClientFields = mapOf(
                "deviceModel" to "iPhone16,2",
                "osName" to "iOS",
                "osVersion" to "18.1.0.22B83"
            )
        )
    )

    /**
     * Resolves a playable audio stream URL for the given videoId.
     * Tries multiple client strategies until one succeeds.
     */
    suspend fun resolveAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        // Check cache
        val cached = streamCache[videoId]
        if (cached != null && System.currentTimeMillis() < cached.expiryEpochMs) {
            Log.d(TAG, "Returning cached stream URL for videoId=$videoId")
            return@withContext cached.url
        }

        for (strategy in strategies) {
            val url = tryPlayerEndpoint(videoId, strategy)
            if (url != null) {
                Log.d(TAG, "Resolved via ${strategy.label} for $videoId (url length=${url.length})")
                streamCache[videoId] = CachedStream(url, System.currentTimeMillis() + 3 * 3600 * 1000L)
                return@withContext url
            }
        }

        Log.e(TAG, "All player strategies failed for videoId=$videoId")
        return@withContext null
    }

    private fun tryPlayerEndpoint(videoId: String, strategy: ClientStrategy): String? {
        try {
            val clientObj = JSONObject().apply {
                put("clientName", strategy.clientName)
                put("clientVersion", strategy.clientVersion)
                put("hl", "en")
                put("gl", "US")
                for ((key, value) in strategy.extraClientFields) {
                    put(key, value)
                }
            }

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", clientObj)
                    if (strategy.thirdParty != null) {
                        put("thirdParty", strategy.thirdParty)
                    }
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", "20696")
                    })
                })
            }

            val requestBuilder = Request.Builder()
                .url(strategy.apiUrl + "&key=$INNERTUBE_API_KEY")
                .header("Content-Type", "application/json")
                .header("User-Agent", strategy.userAgent)
                .header("X-YouTube-Client-Name", strategy.clientId.toString())
                .header("X-YouTube-Client-Version", strategy.clientVersion)

            if (strategy.label == "WEB" || strategy.label == "TVHTML5_EMBEDDED") {
                requestBuilder.header("Origin", "https://www.youtube.com")
                requestBuilder.header("Referer", "https://www.youtube.com/")
            }

            for ((name, value) in strategy.extraHeaders) {
                requestBuilder.header(name, value)
            }

            requestBuilder.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            val request = requestBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Player ${strategy.label} returned HTTP ${response.code} for $videoId")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                // Check playability
                val playability = json.optJSONObject("playabilityStatus")
                val status = playability?.optString("status")
                if (status != "OK") {
                    val reason = playability?.optString("reason")
                        ?: playability?.optJSONObject("errorScreen")
                            ?.optJSONObject("playerErrorMessageRenderer")
                            ?.optJSONObject("reason")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: "Unknown"
                    Log.w(TAG, "Player ${strategy.label}: video $videoId status=$status - $reason")
                    return null
                }

                // Extract streaming data
                val streamingData = json.optJSONObject("streamingData") ?: run {
                    Log.w(TAG, "Player ${strategy.label}: no streamingData for $videoId")
                    return null
                }

                // Try adaptive formats first (audio-only, higher quality)
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                val audioUrl = extractBestAudioUrl(adaptiveFormats, strategy.label, videoId)
                if (audioUrl != null) return audioUrl

                // Fallback to muxed formats
                val formats = streamingData.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.optJSONObject(i) ?: continue
                        val url = format.optString("url", "")
                        if (url.isNotBlank() && url.startsWith("http")) {
                            Log.d(TAG, "Player ${strategy.label}: using muxed format itag=${format.optInt("itag")} for $videoId")
                            return url
                        }
                    }
                }

                Log.w(TAG, "Player ${strategy.label}: no usable URL for $videoId")
                return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Player ${strategy.label} exception for $videoId: ${e.message}")
            return null
        }
    }

    private fun extractBestAudioUrl(adaptiveFormats: org.json.JSONArray?, label: String, videoId: String): String? {
        if (adaptiveFormats == null) return null

        val audioFormats = mutableListOf<JSONObject>()
        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType", "")
            if (mimeType.startsWith("audio/")) {
                // Only consider formats with direct URL (no cipher needed)
                val url = format.optString("url", "")
                val cipher = format.optString("signatureCipher", "")
                if (url.isNotBlank() && url.startsWith("http")) {
                    audioFormats.add(format)
                } else if (cipher.isNotBlank()) {
                    Log.d(TAG, "Player $label: skipping ciphered format itag=${format.optInt("itag")} for $videoId")
                }
            }
        }

        if (audioFormats.isEmpty()) {
            Log.d(TAG, "Player $label: no direct audio URLs found for $videoId")
            return null
        }

        // Prefer highest bitrate
        val best = audioFormats.maxByOrNull { it.optInt("bitrate", 0) } ?: audioFormats.first()
        val url = best.optString("url", "")
        val bitrate = best.optInt("bitrate", 0)
        val mime = best.optString("mimeType", "")
        val itag = best.optInt("itag", 0)
        Log.d(TAG, "Player $label: best audio itag=$itag, bitrate=${bitrate/1000}kbps, mime=$mime for $videoId")
        return url
    }
}
