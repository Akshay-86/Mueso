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

    private var authCookie: String? = null

    fun setAuthCookie(cookie: String?) {
        this.authCookie = cookie
        Log.d(TAG, "Auth cookie updated (hasCookie=${!cookie.isNullOrBlank()})")
    }

    fun invalidateCache(videoId: String) {
        streamCache.remove(videoId)
    }

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
        val thirdParty: JSONObject? = null,
        val requiresAuth: Boolean = false
    )

    private val strategies = listOf(
        // Strategy 1: Authenticated YouTube Music (WEB_REMIX) - Works directly without PO token when logged in (matching Zuno)
        ClientStrategy(
            label = "WEB_REMIX_AUTH",
            apiUrl = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "WEB_REMIX",
            clientVersion = "1.20250506.00.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            clientId = 67,
            extraClientFields = mapOf(
                "platform" to "DESKTOP",
                "osName" to "Windows",
                "osVersion" to "10.0",
                "browserName" to "Chrome",
                "browserVersion" to "135.0.0.0"
            ),
            extraHeaders = mapOf(
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/"
            ),
            requiresAuth = true
        ),
        // Strategy 2: iOS Client (from Zuno's create_ios_context) - Returns direct pre-signed audio URLs without botguard block
        ClientStrategy(
            label = "IOS",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "IOS",
            clientVersion = "20.11.6",
            userAgent = "com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)",
            clientId = 5,
            extraClientFields = mapOf(
                "deviceModel" to "iPhone10,4",
                "osName" to "iPhone",
                "osVersion" to "16.7.7.20H330"
            )
        ),
        // Strategy 3: Android Client (from Zuno's create_android_context)
        ClientStrategy(
            label = "ANDROID",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID",
            clientVersion = "21.03.36",
            userAgent = "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip",
            clientId = 3,
            extraClientFields = mapOf(
                "platform" to "MOBILE",
                "osName" to "Android",
                "osVersion" to "16",
                "androidSdkVersion" to 36
            )
        ),
        // Strategy 4: TV Client (from Zuno's create_tv_context)
        ClientStrategy(
            label = "TVHTML5",
            apiUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "TVHTML5",
            clientVersion = "7.20260311.12.00",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            clientId = 7,
            extraClientFields = mapOf(
                "platform" to "TV",
                "osName" to "Linux"
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

            if (strategy.requiresAuth) {
                val cookie = authCookie
                if (cookie.isNullOrBlank()) return null
                requestBuilder.header("Cookie", cookie)
                val sapisid = extractCookieValue(cookie, "SAPISID")
                    ?: extractCookieValue(cookie, "__Secure-1PAPISID")
                    ?: extractCookieValue(cookie, "__Secure-3PAPISID")
                if (!sapisid.isNullOrBlank()) {
                    try {
                        val hash = getSapisidHash(sapisid, "https://music.youtube.com")
                        requestBuilder.header("Authorization", "SAPISIDHASH $hash")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to compute SAPISIDHASH", e)
                    }
                }
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

        // Prefer audio/mp4 (AAC / itag 140) for universal hardware decoding and standard MP4 metadata tagging
        val mp4Audio = audioFormats.filter { it.optString("mimeType", "").contains("audio/mp4") }
        val best = mp4Audio.maxByOrNull { it.optInt("bitrate", 0) }
            ?: audioFormats.maxByOrNull { it.optInt("bitrate", 0) }
            ?: audioFormats.first()
        val url = best.optString("url", "")
        val bitrate = best.optInt("bitrate", 0)
        val mime = best.optString("mimeType", "")
        val itag = best.optInt("itag", 0)
        Log.d(TAG, "Player $label: best audio itag=$itag, bitrate=${bitrate/1000}kbps, mime=$mime for $videoId")
        return url
    }

    private fun extractCookieValue(cookie: String, key: String): String? {
        val regex = Regex("(?:^|;\\s*)$key=([^;]+)")
        return regex.find(cookie)?.groupValues?.getOrNull(1)
    }

    private fun getSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp $sapisid $origin"
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(payload.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "${timestamp}_$hash"
    }
}
