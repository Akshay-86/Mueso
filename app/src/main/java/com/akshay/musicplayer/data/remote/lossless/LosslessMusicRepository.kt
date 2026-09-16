package com.akshay.musicplayer.data.remote.lossless

import android.util.Log
import com.akshay.musicplayer.domain.models.LosslessStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class LosslessMusicRepository(
    private var serverBaseUrl: String = DEFAULT_SERVER_URL
) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://clashflac.kanjijewels.com"
        private const val TAG = "MUESO_LOSSLESS"
        private const val MAX_DURATION_DIFF_SEC = 10
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    // In-memory cache for resolved stream URLs
    private val resolvedCache = ConcurrentHashMap<String, LosslessStreamResult>()

    fun updateServerBaseUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isNotBlank() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
            serverBaseUrl = clean
            Log.d(TAG, "Updated Lossless server base URL to: $serverBaseUrl")
        }
    }

    /**
     * Resolves a high-confidence, verified direct Lossless FLAC stream URL.
     * Returns null if not found or if the request times out, allowing seamless fallback to YouTube.
     */
    suspend fun resolveLosslessStream(
        rawTitle: String,
        rawArtist: String,
        expectedDurationSeconds: Int = 0
    ): LosslessStreamResult? = withContext(Dispatchers.IO) {
        if (rawTitle.isBlank()) return@withContext null

        val cleanTitle = cleanForSearch(rawTitle)
        val cleanArtist = cleanForSearch(rawArtist)
        val cacheKey = "$cleanTitle|$cleanArtist"

        resolvedCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Lossless cache HIT for '$cleanTitle' by '$cleanArtist'")
            return@withContext cached
        }

        try {
            // Strategy 1: Direct Resolve via /api/resolve with title and artist
            val directResult = tryResolveDirect("$cleanTitle $cleanArtist", expectedDurationSeconds)
            if (directResult != null) {
                resolvedCache[cacheKey] = directResult
                return@withContext directResult
            }

            // Strategy 2: If combined query didn't match, search catalog via /api/search and resolve top match ASIN
            val candidate = findBestCandidateFromSearch(cleanTitle, cleanArtist, expectedDurationSeconds)
            if (candidate != null) {
                val resolvedFromAsin = tryResolveDirect(candidate.asin, expectedDurationSeconds)
                if (resolvedFromAsin != null) {
                    resolvedCache[cacheKey] = resolvedFromAsin
                    return@withContext resolvedFromAsin
                }
            }

            Log.d(TAG, "No verified lossless match found for '$cleanTitle' by '$cleanArtist'. Proceeding to YouTube fallback.")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Lossless stream resolution failed gracefully for '$cleanTitle': ${e.message}")
            null
        }
    }

    private fun tryResolveDirect(input: String, expectedDurationSec: Int): LosslessStreamResult? {
        val url = "$serverBaseUrl/api/resolve"
        val jsonPayload = JSONObject().apply {
            put("input", input)
            put("quality", "HD")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "Mueso-Lossless-Client/2.0")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: return null
            val obj = JSONObject(body)

            val streamUrl = obj.optString("stream_url", "").trim()
            if (streamUrl.isBlank() || !streamUrl.startsWith("http")) return null

            val duration = obj.optInt("duration_sec", 0)
            if (expectedDurationSec > 0 && duration > 0) {
                if (abs(duration - expectedDurationSec) > MAX_DURATION_DIFF_SEC) {
                    Log.d(TAG, "Duration mismatch: expected $expectedDurationSec vs got $duration for '$input'")
                    return null
                }
            }

            val rawBitrate = obj.optInt("bitrate", 0)
            val bitrateKbps = if (rawBitrate > 0) rawBitrate / 1000 else 1411
            val codec = obj.optString("codec", "flac").lowercase()
            val isFlac = codec == "flac"

            val bitDepth = if (bitrateKbps > 1500) 24 else 16
            val sampleRateHz = if (bitrateKbps > 2300) 96000 else 48000

            Log.i(TAG, "Resolved Lossless FLAC stream: codec=$codec, bitrate=${bitrateKbps}kbps, duration=${duration}s, url=${streamUrl.take(45)}...")

            LosslessStreamResult(
                streamUrl = streamUrl,
                codec = codec.uppercase(),
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                source = "Lossless FLAC (${obj.optString("source_type", "CDN").uppercase()})",
                durationSeconds = duration,
                matchedTitle = obj.optString("title", ""),
                matchedArtist = obj.optString("artist", ""),
                album = if (obj.has("album") && !obj.isNull("album")) obj.optString("album") else null
            )
        } catch (e: Exception) {
            Log.d(TAG, "tryResolveDirect failed for '$input': ${e.message}")
            null
        }
    }

    private data class CandidateTrack(
        val asin: String,
        val title: String,
        val artist: String,
        val durationSec: Int
    )

    private fun findBestCandidateFromSearch(title: String, artist: String, expectedDurationSec: Int): CandidateTrack? {
        val query = "$title $artist".trim()
        val url = "$serverBaseUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=track&limit=5"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mueso-Lossless-Client/2.0")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: return null
            val array = JSONArray(body)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val asin = item.optString("asin", "")
                val itemTitle = item.optString("title", "")
                val itemArtist = item.optString("artist", "")
                val itemDuration = item.optInt("duration_sec", 0)

                if (asin.isBlank()) continue

                if (expectedDurationSec > 0 && itemDuration > 0) {
                    if (abs(itemDuration - expectedDurationSec) > MAX_DURATION_DIFF_SEC) continue
                }

                // Check title similarity
                if (isTitleMatch(title, itemTitle)) {
                    return CandidateTrack(asin, itemTitle, itemArtist, itemDuration)
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "findBestCandidateFromSearch failed for '$query': ${e.message}")
            null
        }
    }

    private fun isTitleMatch(target: String, candidate: String): Boolean {
        val t = target.lowercase().replace(Regex("[^a-z0-9]"), "")
        val c = candidate.lowercase().replace(Regex("[^a-z0-9]"), "")
        return t == c || t.contains(c) || c.contains(t)
    }

    private fun cleanForSearch(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[-–—]\s*topic\s*$|\s+topic\s*$"""), "")
            .replace(Regex("""\s*\|.*$"""), "")
            .replace(Regex("""(?i)[\[(]\s*(?:official\s+)?(?:music\s+)?(?:video|audio|lyrics?|hd|4k|remastered|visualizer)[\])]"""), " ")
            .replace(Regex("""(?i)(?:\s*[\[(])?\s*(feat\.?|ft\.?|featuring)\s+.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
