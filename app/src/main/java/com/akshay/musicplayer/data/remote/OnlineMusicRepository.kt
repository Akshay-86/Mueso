package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.domain.models.LrcParser
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)

class OnlineMusicRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun getExtractorModule(py: com.chaquo.python.Python): com.chaquo.python.PyObject {
        val module = py.getModule("extractor")
        return try {
            val hasSearch = module.get("search_tracks") != null
            val hasExtract = module.get("extract_stream") != null
            if (!hasSearch || !hasExtract) {
                Log.w("MUESO_PYTHON", "Extractor module missing attributes (hasSearch=$hasSearch, hasExtract=$hasExtract). Reloading module...")
                val importlib = py.getModule("importlib")
                importlib.callAttr("reload", module)
            } else {
                module
            }
        } catch (e: Exception) {
            Log.w("MUESO_PYTHON", "Error checking/reloading extractor module, using default instance", e)
            module
        }
    }

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> {
        Log.d("MUESO_TRENDING", "Fetching trending top telugu songs via yt-dlp...")
        return searchOnlineTracks("trending top telugu songs")
    }

    private fun cleanTitleAndArtist(title: String, artist: String): Pair<String, String> {
        var cTitle = title
        var cArtist = artist

        if (cTitle.contains(" - ")) {
            val parts = cTitle.split(" - ", limit = 2)
            if (cArtist == "Unknown Artist" || cArtist.isBlank() || cArtist.contains("Music") || cArtist.contains("Station") || cArtist.contains("Topic") || cArtist.contains("Records") || cArtist.contains("Channel") || cArtist.contains("VEVO") || cArtist.contains("YouTube") || cArtist.contains("&")) {
                cArtist = parts[0].trim()
            }
            cTitle = parts[1].trim()
        }

        val noiseRegex = Regex(
            "(?i)(\\(.*?\\)|\\[.*?\\]|\\{.*?\\}|4k|8k|1080p|720p|video song|lyrical song|lyrical video|official video|full video song|full video|audio song|official audio|visualizer|hd|4k video|remix|slowed|reverb|cover|version)",
            RegexOption.IGNORE_CASE
        )
        cTitle = noiseRegex.replace(cTitle, " ").trim()
        cArtist = noiseRegex.replace(cArtist, " ").trim()

        cTitle = cTitle.replace(Regex("[^a-zA-Z0-9\\s\\u0C00-\\u0C7F\\u0900-\\u097F]"), " ").replace(Regex("\\s+"), " ").trim()
        cArtist = cArtist.replace(Regex("[^a-zA-Z0-9\\s\\u0C00-\\u0C7F\\u0900-\\u097F]"), " ").replace(Regex("\\s+"), " ").trim()

        return Pair(cTitle, cArtist)
    }

    suspend fun fetchLyrics(title: String, artist: String): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val (cleanTitle, cleanArtist) = cleanTitleAndArtist(title, artist)
            Log.d("MUESO_LYRICS", "Original ('$title', '$artist') -> Cleaned ('$cleanTitle', '$cleanArtist')")

            val firstWord = cleanTitle.split(" ").firstOrNull { it.length > 2 } ?: cleanTitle

            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val encFirstWord = URLEncoder.encode(firstWord, "UTF-8")

            // Stage 1: Verome API with clean title & artist
            val url1 = "https://verome-api.deno.dev/api/lyrics?title=$encTitle&artist=$encArtist"
            var result = tryFetchFromUrl(url1, cleanTitle)
            if (result != null) return@withContext result

            // Stage 2: Verome API with FIRST WORD of title
            val url2 = "https://verome-api.deno.dev/api/lyrics?title=$encFirstWord"
            result = tryFetchFromUrl(url2, firstWord)
            if (result != null) return@withContext result

            // Stage 3: LRCLIB Search API with title & artist query
            val queryStr = if (cleanArtist.isNotBlank() && cleanArtist != "Unknown Artist") "$cleanTitle $cleanArtist" else cleanTitle
            result = tryLrclibSearch(queryStr)
            if (result != null) return@withContext result

            // Stage 4: LRCLIB Search API with clean title
            result = tryLrclibSearch(cleanTitle)
            if (result != null) return@withContext result

            // Stage 5: LRCLIB Search API with FIRST WORD of title
            if (firstWord != cleanTitle && firstWord.length > 2) {
                result = tryLrclibSearch(firstWord)
                if (result != null) return@withContext result
            }

            null
        } catch (e: Exception) {
            Log.e("MUESO_LYRICS", "Error fetching lyrics from API pipeline for $title", e)
            null
        }
    }

    private fun tryFetchFromUrl(url: String, title: String): LyricsData? {
        return try {
            Log.d("MUESO_LYRICS", "Fetching lyrics from URL: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val syncedText = jsonObj.optString("syncedLyrics", "")
                        val plainText = jsonObj.optString("plainLyrics", "")

                        if (syncedText.isNotBlank()) {
                            val parsedLines = LrcParser.parse(syncedText)
                            if (parsedLines.isNotEmpty()) {
                                Log.d("MUESO_LYRICS", "Parsed ${parsedLines.size} synced LRC lines for '$title'")
                                return LyricsData(lines = parsedLines)
                            }
                        } else if (plainText.isNotBlank()) {
                            Log.d("MUESO_LYRICS", "Plain lyrics found for '$title'")
                            return LyricsData(rawText = plainText)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w("MUESO_LYRICS", "Failed to fetch from URL: $url", e)
            null
        }
    }

    private fun tryLrclibSearch(query: String): LyricsData? {
        return try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"
            Log.d("MUESO_LYRICS", "Searching LRCLIB API for query: '$query'")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuesoMusicPlayer/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("[")) {
                        val jsonArr = JSONArray(jsonStr)
                        var bestSynced: String? = null
                        var bestPlain: String? = null

                        for (i in 0 until jsonArr.length()) {
                            val item = jsonArr.optJSONObject(i) ?: continue
                            val synced = item.optString("syncedLyrics", "")
                            val plain = item.optString("plainLyrics", "")

                            if (synced.isNotBlank() && bestSynced == null) {
                                bestSynced = synced
                            } else if (plain.isNotBlank() && bestPlain == null) {
                                bestPlain = plain
                            }
                        }

                        if (!bestSynced.isNullOrBlank()) {
                            val parsedLines = LrcParser.parse(bestSynced)
                            if (parsedLines.isNotEmpty()) {
                                Log.d("MUESO_LYRICS", "Successfully parsed ${parsedLines.size} synced lines via LRCLIB search for '$query'")
                                return LyricsData(lines = parsedLines)
                            }
                        } else if (!bestPlain.isNullOrBlank()) {
                            Log.d("MUESO_LYRICS", "Found plain lyrics via LRCLIB search for '$query'")
                            return LyricsData(rawText = bestPlain)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w("MUESO_LYRICS", "LRCLIB search error for query '$query'", e)
            null
        }
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        Log.d("MUESO_STREAM", "--------------------------------------------------")
        Log.d("MUESO_STREAM", "[1/4] getStreamUrl requested for videoId: '$videoId'")
        try {
            val isStarted = com.chaquo.python.Python.isStarted()
            if (!isStarted) {
                Log.e("MUESO_STREAM", "[ERROR] Python is NOT started! Returning empty string.")
                return@withContext ""
            }

            val py = com.chaquo.python.Python.getInstance()
            var pyModule = getExtractorModule(py)
            
            val result = try {
                Log.d("MUESO_STREAM", "[2/4] Executing pyModule.callAttr('extract_stream', '$videoId')...")
                pyModule.callAttr("extract_stream", videoId)
            } catch (pyErr: com.chaquo.python.PyException) {
                Log.w("MUESO_STREAM", "PyException on extract_stream, reloading python module and retrying...", pyErr)
                val importlib = py.getModule("importlib")
                pyModule = importlib.callAttr("reload", py.getModule("extractor"))
                pyModule.callAttr("extract_stream", videoId)
            }

            val streamUrl = result?.toString()?.takeIf { it.startsWith("http") }

            if (!streamUrl.isNullOrEmpty()) {
                Log.d("MUESO_STREAM", "[3/4] SUCCESS! Direct Audio Stream URL extracted: $streamUrl")
                return@withContext streamUrl
            } else {
                Log.e("MUESO_STREAM", "[3/4] FAILURE: Python extract_stream returned empty or non-http string for videoId: '$videoId'")
                return@withContext ""
            }
        } catch (e: Exception) {
            Log.e("MUESO_STREAM", "[4/4] CRITICAL ERROR extracting stream via yt-dlp for videoId: '$videoId'", e)
            return@withContext ""
        }
    }

    suspend fun searchOnlineTracks(query: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        Log.d("MUESO_SEARCH", "--------------------------------------------------")
        Log.d("MUESO_SEARCH", "[1/4] searchOnlineTracks requested for query: '$query'")
        if (query.isBlank()) {
            Log.d("MUESO_SEARCH", "[2/4] Query is blank, returning empty list.")
            return@withContext emptyList()
        }
        return@withContext try {
            val isStarted = com.chaquo.python.Python.isStarted()
            if (!isStarted) {
                Log.e("MUESO_SEARCH", "[ERROR] Python is NOT started! Returning empty list.")
                return@withContext emptyList()
            }

            val py = com.chaquo.python.Python.getInstance()
            var pyModule = getExtractorModule(py)

            val pyList = try {
                Log.d("MUESO_SEARCH", "[2/4] Executing pyModule.callAttr('search_tracks', '$query', 20)...")
                pyModule.callAttr("search_tracks", query, 20).asList()
            } catch (pyErr: com.chaquo.python.PyException) {
                Log.w("MUESO_SEARCH", "PyException on search_tracks, reloading python module and retrying...", pyErr)
                val importlib = py.getModule("importlib")
                pyModule = importlib.callAttr("reload", py.getModule("extractor"))
                pyModule.callAttr("search_tracks", query, 20).asList()
            }

            Log.d("MUESO_SEARCH", "[3/4] search_tracks returned ${pyList.size} raw Python item dicts.")

            val seenKeys = mutableSetOf<String>()
            val noiseRegex = Regex("(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\]|\\(audio\\)|\\[audio\\])", RegexOption.IGNORE_CASE)
            val compilationRegex = Regex("(?i)(compilation|jukebox|full album|all songs|non stop|non-stop|1 hour|2 hours|3 hours|10 hours|audio jukebox|video jukebox|album mix|best of|mashup)", RegexOption.IGNORE_CASE)
            val shortEditRegex = Regex("(?i)(shorts|short|reel|reels|tiktok|whatsapp status|status edit|status video|30 sec status|30sec|45sec|edit version|speed up|sped up|nightcore)", RegexOption.IGNORE_CASE)

            val tracks = pyList.mapNotNull { pyObj ->
                val videoId = pyObj.callAttr("get", "videoId")?.toString() ?: return@mapNotNull null
                val title = pyObj.callAttr("get", "title")?.toString() ?: "Unknown Title"
                val artist = pyObj.callAttr("get", "artist")?.toString() ?: "Unknown Artist"
                val duration = (pyObj.callAttr("get", "duration")?.toLong() ?: 0L) * 1000L
                val thumb = pyObj.callAttr("get", "thumbnail")?.toString()
                val fastThumb = getFastListThumbnailUrl(thumb)

                // Filter out low duration (<90s), long compilations (>10m), or short edits/reels/status
                if ((duration in 1L..89_999L) || (duration > 10 * 60 * 1000L) || compilationRegex.containsMatchIn(title) || shortEditRegex.containsMatchIn(title)) {
                    Log.d("MUESO_SEARCH", "Skipping invalid duration/short edit track: '$title' (duration=${duration / 1000}s)")
                    return@mapNotNull null
                }

                val cleanTitleKey = noiseRegex.replace(title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
                val cleanArtistKey = noiseRegex.replace(artist, "").lowercase().replace(Regex("[^a-z0-9]"), "")
                val uniqueKey = if (cleanTitleKey.isNotBlank()) "$cleanTitleKey:$cleanArtistKey" else videoId

                if (!seenKeys.add(uniqueKey)) {
                    Log.d("MUESO_SEARCH", "Skipping duplicate search result: '$title' by '$artist'")
                    return@mapNotNull null
                }

                TrackEntity(
                    id = videoId.hashCode().toLong(),
                    title = title,
                    artist = artist,
                    album = "Online Search",
                    duration = duration,
                    albumId = 0L,
                    filePath = "online:$videoId",
                    artworkUrl = fastThumb,
                    lyrics = null,
                    socialMetrics = null
                )
            }
            Log.d("MUESO_SEARCH", "[4/4] SUCCESS: Map completed. Returning ${tracks.size} TrackEntities.")
            return@withContext tracks
        } catch (e: Exception) {
            Log.e("MUESO_SEARCH", "[4/4] CRITICAL ERROR in searchOnlineTracks for query: '$query'", e)
            emptyList()
        }
    }

    suspend fun getSponsorSkipSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"intro\",\"music_offtopic\",\"selfpromo\"]"
            Log.d("MUESO_SPONSOR", "Fetching SponsorBlock skip segments for videoId: '$videoId'")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuesoMusicPlayer/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("[")) {
                        val jsonArr = JSONArray(jsonStr)
                        val segments = mutableListOf<SponsorSegment>()
                        for (i in 0 until jsonArr.length()) {
                            val item = jsonArr.optJSONObject(i) ?: continue
                            val category = item.optString("category", "")
                            val segArray = item.optJSONArray("segment")
                            if (segArray != null && segArray.length() >= 2) {
                                val startSec = segArray.optDouble(0, 0.0)
                                val endSec = segArray.optDouble(1, 0.0)
                                val startMs = (startSec * 1000).toLong()
                                val endMs = (endSec * 1000).toLong()
                                if (endMs > startMs) {
                                    segments.add(SponsorSegment(startMs, endMs, category))
                                }
                            }
                        }
                        Log.d("MUESO_SPONSOR", "Found ${segments.size} SponsorBlock skip segments for videoId: '$videoId'")
                        return@withContext segments
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MUESO_SPONSOR", "SponsorBlock fetch error for videoId: '$videoId'", e)
        }
        emptyList()
    }

    private fun getFastListThumbnailUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrEmpty()) return null
        var url = rawUrl
        if (url.contains("i.ytimg.com/vi/")) {
            val videoId = url.substringAfter("/vi/").substringBefore("/")
            if (videoId.isNotBlank() && !videoId.contains("http")) {
                return "https://i.ytimg.com/vi/$videoId/default.jpg"
            }
        }
        return url
    }

    private fun getHighResArtworkUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrEmpty()) return null
        var url = rawUrl
        if (url.contains("yt3.googleusercontent.com") || url.contains("ggpht.com")) {
            url = url.replace(Regex("=w\\d+-h\\d+.*"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1080")
        }
        if (url.contains("i.ytimg.com/vi/")) {
            val videoId = url.substringAfter("/vi/").substringBefore("/")
            if (videoId.isNotBlank() && !videoId.contains("http")) {
                return "https://i.ytimg.com/vi/$videoId/hq720.jpg"
            }
        }
        return url
    }

    suspend fun getRelatedRecommendations(track: TrackEntity): List<TrackEntity> = withContext(Dispatchers.IO) {
        var videoId = if (track.filePath.startsWith("online:")) track.filePath.removePrefix("online:") else ""
        if (videoId.isBlank() && track.artworkUrl != null && track.artworkUrl.contains("/vi/")) {
            videoId = track.artworkUrl.substringAfter("/vi/").substringBefore("/")
        }

        Log.d("MUESO_RADIO", "getRelatedRecommendations called for title='${track.title}', videoId='$videoId'")

        if (videoId.isNotBlank()) {
            try {
                val url = "https://api.piped.video/next?v=$videoId"
                Log.d("MUESO_RADIO", "Fetching related tracks from Piped API: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonStr = response.body?.string()
                        if (!jsonStr.isNullOrBlank()) {
                            val json = JSONObject(jsonStr)
                            val relatedArray = json.optJSONArray("relatedStreams")
                            if (relatedArray != null && relatedArray.length() > 0) {
                                val list = mutableListOf<TrackEntity>()
                                val seenKeys = mutableSetOf<String>()
                                val noiseRegex = Regex("(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\]|\\(audio\\)|\\[audio\\])", RegexOption.IGNORE_CASE)

                                // Always exclude current playing song title
                                val cleanCurrentTitle = noiseRegex.replace(track.title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
                                seenKeys.add(cleanCurrentTitle)

                                val compilationRegex = Regex("(?i)(compilation|jukebox|full album|all songs|non stop|non-stop|1 hour|2 hours|3 hours|10 hours|audio jukebox|video jukebox|album mix|best of|mashup)", RegexOption.IGNORE_CASE)
                                val shortEditRegex = Regex("(?i)(shorts|short|reel|reels|tiktok|whatsapp status|status edit|status video|30 sec status|30sec|45sec|edit version|speed up|sped up|nightcore)", RegexOption.IGNORE_CASE)

                                for (i in 0 until relatedArray.length()) {
                                    val item = relatedArray.optJSONObject(i) ?: continue
                                    val type = item.optString("type", "")
                                    if (type == "stream" || type == "music_video" || type.isBlank()) {
                                        val urlPath = item.optString("url", "")
                                        val vId = if (urlPath.contains("v=")) urlPath.substringAfter("v=").substringBefore("&") else urlPath.removePrefix("/watch?v=")
                                        val title = item.optString("title", "")
                                        val artist = item.optString("uploaderName", "Unknown Artist")
                                        val thumbnail = item.optString("thumbnail", "")
                                        val durationSeconds = item.optLong("duration", 0L)
                                        val durationMs = durationSeconds * 1000L

                                        // Skip low duration (<90s), compilations (>10m), or short edits/reels/status
                                        if ((durationSeconds in 1L..89L) || (durationSeconds > 600L) || compilationRegex.containsMatchIn(title) || shortEditRegex.containsMatchIn(title)) {
                                            Log.d("MUESO_RADIO", "Skipping invalid duration/short edit from radio queue: '$title' (${durationSeconds}s)")
                                            continue
                                        }

                                        val cleanTitle = noiseRegex.replace(title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
                                        if (vId.isNotBlank() && title.isNotBlank() && cleanTitle.isNotBlank()) {
                                            if (seenKeys.add(cleanTitle)) {
                                                list.add(
                                                    TrackEntity(
                                                        id = vId.hashCode().toLong(),
                                                        title = title,
                                                        artist = artist,
                                                        album = "Recommended Radio",
                                                        duration = durationMs,
                                                        albumId = 0L,
                                                        filePath = "online:$vId",
                                                        artworkUrl = getHighResArtworkUrl(thumbnail)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                if (list.isNotEmpty()) {
                                    Log.d("MUESO_RADIO", "Successfully fetched ${list.size} unique recommended tracks from Piped API")
                                    return@withContext list
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MUESO_RADIO", "Piped API recommendation request failed, falling back to search chaining...", e)
            }
        }

        // Fallback search chaining
        val query = if (track.artist != "Unknown Artist" && track.artist.isNotBlank()) {
            "songs like ${track.title} ${track.artist}"
        } else {
            "songs like ${track.title}"
        }
        Log.d("MUESO_RADIO", "Fallback search chaining query: '$query'")
        val searchResults = searchOnlineTracks(query)
        val noiseRegex = Regex("(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\]|\\(audio\\)|\\[audio\\])", RegexOption.IGNORE_CASE)
        val cleanCurrentTitle = noiseRegex.replace(track.title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
        return@withContext searchResults.filter { item ->
            val cleanItemTitle = noiseRegex.replace(item.title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
            item.id != track.id && cleanItemTitle != cleanCurrentTitle
        }
    }

    suspend fun embedMetadata(filePath: String, title: String, artist: String, album: String, artworkUrl: String?): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val py = com.chaquo.python.Python.getInstance()
            val pyModule = getExtractorModule(py)
            pyModule.callAttr("embed_metadata", filePath, title, artist, album, artworkUrl ?: "").toBoolean()
        } catch (e: Exception) {
            Log.e("MUESO_DOWNLOAD", "Failed to embed metadata via Python mutagen for $filePath", e)
            false
        }
    }
}
