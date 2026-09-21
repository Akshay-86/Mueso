package com.akshay.musicplayer.data.remote.lossless

import android.util.Log
import com.akshay.musicplayer.domain.models.LosslessStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class LosslessMusicRepository(
    private var serverBaseUrl: String = DEFAULT_SERVER_URL
) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://qobuz.kanjijewels.com"
        const val DEFAULT_API_KEY = "lw_sec_e83b4c91a02d7e5f39641b8a5d2c70e9f1a34b82650d9c1e"
        private const val TAG = "MUESO_LOSSLESS"
        private const val MAX_DURATION_DIFF_SEC = 8

        // Quality presets matching Qobuz resolution engine
        const val QUALITY_MAX_HI_RES = 27 // Up to 24-bit / 192 kHz
        const val QUALITY_HI_RES_96 = 7   // Up to 24-bit / 96 kHz
        const val QUALITY_CD_LOSSLESS = 6 // 16-bit / 44.1 kHz FLAC
        const val QUALITY_MP3_320 = 5     // 320 kbps MP3
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    // In-memory cache for resolved stream URLs
    private val resolvedCache = ConcurrentHashMap<String, LosslessStreamResult>()

    fun updateServerBaseUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isNotBlank() && clean.startsWith("https://")) {
            // Auto-migrate legacy ClashFLAC URL to high-fidelity Qobuz backend
            if (clean.contains("clashflac.kanjijewels.com", ignoreCase = true)) {
                serverBaseUrl = DEFAULT_SERVER_URL
            } else {
                serverBaseUrl = clean
            }
            Log.d(TAG, "Lossless server base URL set to: $serverBaseUrl")
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
            // Check if server is Qobuz backend or custom ClashFLAC
            val isQobuzBackend = serverBaseUrl.contains("qobuz", ignoreCase = true) || !serverBaseUrl.contains("clashflac", ignoreCase = true)

            var result: LosslessStreamResult? = null
            if (isQobuzBackend) {
                result = resolveViaQobuzBackend(rawTitle, rawArtist, expectedDurationSeconds)
            }

            // If Qobuz didn't match or server is ClashFLAC, try secondary resolver (with strict 30s preview rejection)
            if (result == null && !isQobuzBackend) {
                result = resolveViaClashFlac(rawTitle, rawArtist, expectedDurationSeconds)
            }

            if (result != null) {
                resolvedCache[cacheKey] = result
                return@withContext result
            }

            Log.d(TAG, "No verified lossless stream match found for '$rawTitle' by '$rawArtist'. Safely falling back to YouTube.")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Lossless stream resolution failed gracefully for '$rawTitle': ${e.message}")
            null
        }
    }

    private fun resolveViaQobuzBackend(
        rawTitle: String,
        rawArtist: String,
        expectedDurationSec: Int
    ): LosslessStreamResult? {
        val cleanT = cleanForSearch(rawTitle)
        val cleanA = cleanForSearch(rawArtist)

        val individualArtists = rawArtist.split(Regex("""(?i)\s*(?:&|,|\bx\b|feat\.?|ft\.?|featuring|with|\+)\s*"""))
            .map { cleanForSearch(it) }
            .filter { it.isNotBlank() }
        val primaryArtist = individualArtists.firstOrNull() ?: cleanA

        val queries = listOfNotNull(
            "$cleanT $cleanA".trim().takeIf { it.isNotBlank() },
            if (primaryArtist.isNotBlank() && primaryArtist != cleanA) "$cleanT $primaryArtist".trim() else null,
            "$cleanA $cleanT".trim().takeIf { it.isNotBlank() },
            cleanT.takeIf { it.isNotBlank() },
            rawTitle.trim().takeIf { it.isNotBlank() }
        ).distinct()

        val candidate = findBestQobuzCandidate(queries, rawTitle, rawArtist, expectedDurationSec) ?: return null
        Log.i(TAG, "Verified Qobuz match for '$rawTitle': trackId=${candidate.id}, title='${candidate.title}', artist='${candidate.performerName}', duration=${candidate.duration}s")

        // Fetch direct CDN streaming URL across descending quality tiers (strictly lossless)
        val qualitiesToTry = listOf(QUALITY_MAX_HI_RES, QUALITY_HI_RES_96, QUALITY_CD_LOSSLESS)
        for (q in qualitiesToTry) {
            val stream = fetchQobuzStreamUrl(candidate, q)
            if (stream != null) return stream
        }
        return null
    }

    private fun fetchQobuzStreamUrl(candidate: QobuzCandidate, quality: Int): LosslessStreamResult? {
        val urlBuilder = "$serverBaseUrl/api/track/${candidate.id}/url".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("quality", quality.toString())
        urlBuilder.addQueryParameter("fallback", "true")
        if (candidate.duration > 0) {
            urlBuilder.addQueryParameter("duration", candidate.duration.toString())
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .header("User-Agent", "LastWave/4.1.0 (Android; Linux)")
        if (serverBaseUrl.contains("kanjijewels.com", ignoreCase = true)) {
            requestBuilder.header("X-API-Key", DEFAULT_API_KEY)
        }
        val request = requestBuilder.build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: return null
            val obj = JSONObject(body)
            if (!obj.optBoolean("success", false)) {
                return null
            }

            val data = obj.optJSONObject("data") ?: return null
            val streamUrl = data.optString("url", "").trim()
            if (streamUrl.isBlank() || !streamUrl.startsWith("http")) return null

            val bitDepth = data.optInt("bit_depth", 16)
            val samplingRate = data.optDouble("sampling_rate", 44.1)
            val formatId = data.optInt("format_id", quality)
            val duration = data.optInt("duration", candidate.duration)

            val bitrateKbps = when (formatId) {
                QUALITY_MAX_HI_RES, QUALITY_HI_RES_96 -> ((bitDepth * samplingRate * 2 * 1000) / 1000).toInt()
                QUALITY_CD_LOSSLESS -> 1411
                QUALITY_MP3_320 -> 320
                else -> 1411
            }

            val isHiRes = bitDepth > 16 || samplingRate > 48.0
            val codec = when {
                isHiRes -> "HI-RES FLAC"
                formatId == QUALITY_MP3_320 -> "MP3"
                else -> "FLAC"
            }

            Log.i(TAG, "Resolved Qobuz stream: trackId=${candidate.id}, codec=$codec, ${bitDepth}-bit/${samplingRate}kHz, ${bitrateKbps}kbps, url=${streamUrl.take(45)}...")

            LosslessStreamResult(
                streamUrl = streamUrl,
                codec = codec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = (samplingRate * 1000).toInt(),
                bitDepth = bitDepth,
                source = if (isHiRes) "Qobuz Studio Master" else "Qobuz CD Lossless",
                durationSeconds = duration,
                matchedTitle = candidate.title,
                matchedArtist = candidate.performerName,
                album = candidate.albumTitle
            )
        } catch (e: Exception) {
            Log.d(TAG, "fetchQobuzStreamUrl quality $quality failed for track ${candidate.id}: ${e.message}")
            null
        }
    }

    private data class QobuzCandidate(
        val id: Long,
        val title: String,
        val version: String,
        val performerName: String,
        val albumTitle: String?,
        val duration: Int
    )

    private fun findBestQobuzCandidate(
        queries: List<String>,
        targetTitle: String,
        targetArtist: String,
        expectedDurationSec: Int
    ): QobuzCandidate? {
        val normTargetTitle = normalizeText(cleanForSearch(targetTitle))
        val normTargetArtist = normalizeText(cleanForSearch(targetArtist))
        val targetTokens = normTargetArtist.split(" ").filter { it !in ARTIST_NOISE_WORDS }.toSet()

        for (query in queries) {
            val url = "$serverBaseUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=track&limit=15"
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "LastWave/4.1.0 (Android; Linux)")
            if (serverBaseUrl.contains("kanjijewels.com", ignoreCase = true)) {
                requestBuilder.header("X-API-Key", DEFAULT_API_KEY)
            }
            val request = requestBuilder.build()

            val items = try {
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }
                val body = response.body?.string() ?: continue
                val root = JSONObject(body)
                val results = root.optJSONObject("results") ?: continue
                val tracks = results.optJSONObject("tracks") ?: continue
                val itemsArr = tracks.optJSONArray("items") ?: continue
                itemsArr
            } catch (e: Exception) {
                Log.d(TAG, "Qobuz search failed for '$query': ${e.message}")
                continue
            }

            var bestCandidate: QobuzCandidate? = null
            var bestScore = -1

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                if (id <= 0L) continue

                val itemTitle = item.optString("title", "")
                val itemVersion = item.optString("version", "")
                val candDur = item.optInt("duration", 0)

                // Check duration tolerance
                if (expectedDurationSec > 0 && candDur > 0 && abs(candDur - expectedDurationSec) > MAX_DURATION_DIFF_SEC) {
                    continue
                }

                val performerObj = item.optJSONObject("performer")
                val performerName = performerObj?.optString("name", "") ?: ""
                val albumObj = item.optJSONObject("album")
                val albumTitle = if (albumObj?.has("title") == true && !albumObj.isNull("title")) albumObj.getString("title") else null

                val candNormPerformer = normalizeText(performerName)
                val performerTokens = candNormPerformer.split(" ").toSet()

                // Verify artist match
                val isArtistMatch = normTargetArtist.isBlank() ||
                        candNormPerformer == normTargetArtist ||
                        candNormPerformer.contains(normTargetArtist) ||
                        normTargetArtist.contains(candNormPerformer) ||
                        (targetTokens.isNotEmpty() && targetTokens.any { it in performerTokens })

                if (!isArtistMatch && targetArtist.isNotBlank()) continue

                // Check title variations: title alone, or title + version
                val candNormTitle = normalizeText(cleanForSearch(itemTitle))
                val candFullNormTitle = normalizeText(cleanForSearch("$itemTitle $itemVersion"))
                val rawCandNormTitle = normalizeText(itemTitle)

                val distTitle = levenshtein(normTargetTitle, candNormTitle)
                val distFull = levenshtein(normTargetTitle, candFullNormTitle)
                val distRaw = levenshtein(normTargetTitle, rawCandNormTitle)
                val minDistance = minOf(distTitle, distFull, distRaw)

                val maxFuzz = (normTargetTitle.length / 5).coerceIn(1, 2)
                val isTitleMatch = minDistance <= maxFuzz ||
                        normTargetTitle == candNormTitle ||
                        normTargetTitle == candFullNormTitle ||
                        candNormTitle.startsWith(normTargetTitle) ||
                        normTargetTitle.startsWith(candNormTitle)

                if (!isTitleMatch) continue

                var score = 1000 - minDistance * 50
                if (normTargetArtist == candNormPerformer) score += 300
                if (expectedDurationSec > 0 && candDur > 0) {
                    score += (MAX_DURATION_DIFF_SEC - abs(candDur - expectedDurationSec)) * 20
                }

                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = QobuzCandidate(
                        id = id,
                        title = itemTitle,
                        version = itemVersion,
                        performerName = performerName,
                        albumTitle = albumTitle,
                        duration = candDur
                    )
                }
            }

            if (bestCandidate != null) {
                return bestCandidate
            }
        }
        return null
    }

    private fun resolveViaClashFlac(
        rawTitle: String,
        rawArtist: String,
        expectedDurationSec: Int
    ): LosslessStreamResult? {
        val cleanTitle = cleanForSearch(rawTitle)
        val cleanArtist = cleanForSearch(rawArtist)
        val input = "$cleanTitle $cleanArtist".trim()

        val url = "$serverBaseUrl/api/resolve"
        val jsonPayload = JSONObject().apply {
            put("input", input)
            put("quality", "HD")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "LastWave/4.1.0 (Android; Linux)")
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

            val sourceType = obj.optString("source_type", "").lowercase()
            val isAmazonPreview = sourceType == "amazon" ||
                    streamUrl.contains("cloudfront.net", ignoreCase = true) ||
                    streamUrl.contains("amazon", ignoreCase = true) ||
                    streamUrl.contains("dtype=A1DL2DVDQVK3Q", ignoreCase = true)

            // Amazon stream URLs from ClashFLAC are strictly 30s preview streams.
            // Reject so that playback falls back cleanly to YouTube.
            if (isAmazonPreview) {
                Log.w(TAG, "ClashFLAC returned Amazon preview stream for '$input' (only 30s unencrypted). Rejecting for direct playback.")
                return null
            }

            val duration = obj.optInt("duration_sec", 0)
            if (expectedDurationSec > 0 && duration > 0 && abs(duration - expectedDurationSec) > MAX_DURATION_DIFF_SEC) {
                return null
            }

            val rawBitrate = obj.optInt("bitrate", 0)
            val bitrateKbps = if (rawBitrate > 0) rawBitrate / 1000 else 1411
            val codec = obj.optString("codec", "flac").lowercase()

            val isUhd = streamUrl.contains("ql=UHD", ignoreCase = true)
            val bitDepth = if (isUhd || bitrateKbps > 1500) 24 else 16
            val sampleRateHz = if (isUhd || bitrateKbps > 1500) 48000 else 44100

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
            Log.d(TAG, "resolveViaClashFlac failed for '$input': ${e.message}")
            null
        }
    }

    private val ARTIST_NOISE_WORDS = setOf("the", "and", "feat", "ft", "featuring", "with", "x", "topic")

    private fun normalizeText(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun cleanForSearch(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[-–—]\s*topic\s*$|\s+topic\s*$"""), "")
            .replace(Regex("""\s*\|.*$"""), "")
            .replace(Regex("""(?i)\s*[\[(]?\s*from\s+["“]?[^"”\r\n]+["”]?[\])]?\s*$"""), "")
            .replace(Regex("""(?i)[\[(]\s*(?:official\s+)?(?:music\s+)?(?:video|audio|lyrics?|hd|4k|remastered|visualizer)[\])]"""), " ")
            .replace(Regex("""(?i)(?:\s*[\[(])?\s*(feat\.?|ft\.?|featuring)\s+.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
