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

data class CuratedOnlinePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val gradientColors: List<Long>,
    val searchQuery: String
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

    fun getCuratedPlaylists(): List<CuratedOnlinePlaylist> {
        return listOf(
            CuratedOnlinePlaylist(
                id = "curated_top_global",
                title = "Top 50 Global",
                subtitle = "The hottest tracks trending worldwide right now",
                category = "Top Charts",
                gradientColors = listOf(0xFF8E2DE2, 0xFF4A00E0),
                searchQuery = "top 50 global songs"
            ),
            CuratedOnlinePlaylist(
                id = "curated_india_top",
                title = "India Top Hits",
                subtitle = "Most played chartbusters across India",
                category = "Top Charts",
                gradientColors = listOf(0xFFFF512F, 0xFFDD2476),
                searchQuery = "top indian trending songs"
            ),
            CuratedOnlinePlaylist(
                id = "curated_chill_lofi",
                title = "Chill Lofi Beats",
                subtitle = "Soft instrumental beats for study, work & relaxation",
                category = "Mood & Focus",
                gradientColors = listOf(0xFF11998e, 0xFF38ef7d),
                searchQuery = "chill lofi beats study focus"
            ),
            CuratedOnlinePlaylist(
                id = "curated_workout",
                title = "Workout Pump Up",
                subtitle = "High BPM energy tracks to power your workout",
                category = "Fitness & Energy",
                gradientColors = listOf(0xFFFF416C, 0xFFFF4B2B),
                searchQuery = "gym workout motivation songs"
            ),
            CuratedOnlinePlaylist(
                id = "curated_romance",
                title = "Love & Romance",
                subtitle = "Heartfelt acoustic melodies and soothing ballads",
                category = "Mood & Focus",
                gradientColors = listOf(0xFFf857a6, 0xFFff5858),
                searchQuery = "best romantic love songs"
            ),
            CuratedOnlinePlaylist(
                id = "curated_party",
                title = "Party & EDM Anthems",
                subtitle = "High-energy club bangers and dance hits",
                category = "Fitness & Energy",
                gradientColors = listOf(0xFF654ea3, 0xFFeaafc8),
                searchQuery = "party edm dance hits"
            ),
            CuratedOnlinePlaylist(
                id = "curated_retro",
                title = "Retro 90s Classics",
                subtitle = "Golden evergreen classics & nostalgic hits",
                category = "Classics",
                gradientColors = listOf(0xFFF2994A, 0xFFF2C94C),
                searchQuery = "best 90s evergreen songs"
            )
        )
    }

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> {
        Log.d("MUESO_TRENDING", "Fetching trending top telugu songs via yt-dlp...")
        return searchOnlineTracks("trending top telugu songs")
    }

    private fun cleanTitleAndArtist(title: String, artist: String): Pair<String, String> {
        val rawTitle = title.trim()
        val rawArtist = artist.trim()

        // 1. Remove bracketed noise: (Full Video), [4K], {Audio}, (Telugu), etc.
        val bracketRegex = Regex("(?i)\\(.*?\\)|\\[.*?\\]|\\{.*?\\}")
        var cleaned = bracketRegex.replace(rawTitle, " ").trim()

        // 2. Strip common YouTube video/audio suffix keywords
        val suffixKeywords = listOf(
            "full video song", "lyrical video song", "full video", "video song", "lyrical song", "lyrical video",
            "official video", "official audio", "full audio song", "full song", "audio song",
            "4k video", "8k video", "hd video", "video", "audio", "lyrical", "remix", "slowed", "reverb"
        )

        for (kw in suffixKeywords) {
            val re = Regex("(?i)\\b" + Regex.escape(kw) + "\\b")
            cleaned = re.replace(cleaned, " ").trim()
        }

        // 3. Split by common YouTube title delimiters: '|', ':', '/', '-'
        val segments = cleaned.split(Regex("[|:/\\-]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val cleanCharRegex = Regex("[^a-zA-Z0-9\\s\\u0C00-\\u0C7F\\u0900-\\u097F]")
        val cleanedSegments = segments.map {
            cleanCharRegex.replace(it, " ").replace(Regex("\\s+"), " ").trim()
        }.filter { it.isNotBlank() }

        var candidateTitle = ""
        var candidateMovieOrArtist = ""

        if (cleanedSegments.isNotEmpty()) {
            candidateTitle = cleanedSegments[0]
            if (cleanedSegments.size > 1) {
                candidateMovieOrArtist = cleanedSegments[1]
            }
        }

        if (candidateTitle.isBlank()) {
            candidateTitle = cleanCharRegex.replace(rawTitle, " ").replace(Regex("\\s+"), " ").trim()
        }

        val isGenericLabel = rawArtist.isBlank() || rawArtist == "Unknown Artist" ||
                listOf("music", "series", "records", "channel", "vevo", "topic", "station", "aditya", "lahari", "saregama", "sony", "zee", "tips", "t-series")
                    .any { rawArtist.lowercase().contains(it) }

        val finalArtist = if (isGenericLabel && candidateMovieOrArtist.isNotBlank()) {
            candidateMovieOrArtist
        } else if (!isGenericLabel) {
            cleanCharRegex.replace(rawArtist, " ").replace(Regex("\\s+"), " ").trim()
        } else {
            ""
        }

        return Pair(candidateTitle, finalArtist)
    }

    private fun matchesTrackTitle(candidateTrackName: String, targetTitle: String): Boolean {
        if (candidateTrackName.isBlank() || targetTitle.isBlank()) return false
        val c = candidateTrackName.lowercase().replace(Regex("[^a-z0-9\\s\\u0C00-\\u0C7F\\u0900-\\u097F]"), "")
        val t = targetTitle.lowercase().replace(Regex("[^a-z0-9\\s\\u0C00-\\u0C7F\\u0900-\\u097F]"), "")
        if (c.isBlank() || t.isBlank()) return false

        val cNoSpace = c.replace(" ", "")
        val tNoSpace = t.replace(" ", "")

        if (cNoSpace.contains(tNoSpace) || tNoSpace.contains(cNoSpace)) return true

        val tWords = t.split(" ").filter { it.length > 2 }
        if (tWords.isEmpty()) return true
        val cWords = c.split(" ").filter { it.length > 2 }

        val matchedCount = tWords.count { tw -> cWords.any { cw -> cw.contains(tw) || tw.contains(cw) } }
        return (matchedCount.toDouble() / tWords.size.toDouble()) >= 0.5
    }

    suspend fun fetchLyrics(title: String, artist: String, language: String = ""): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val (cleanTitle, cleanArtist) = cleanTitleAndArtist(title, artist)
            Log.d("MUESO_LYRICS", "Original ('$title', '$artist') -> Cleaned ('$cleanTitle', '$cleanArtist') [Lang: '$language']")

            // 1. LRCLIB Direct GET
            val directRes = tryLrclibGet(cleanTitle, cleanArtist)
            if (directRes != null) return@withContext directRes

            // 2. LRCLIB Search with Clean Title & Artist (Direct exact query)
            if (cleanArtist.isNotBlank()) {
                val res1 = tryLrclibSearch(cleanTitle, "$cleanTitle $cleanArtist")
                if (res1 != null) return@withContext res1
            }

            // 3. LRCLIB Search with Title, Artist & Language hint
            if (cleanArtist.isNotBlank() && language.isNotBlank() && !language.startsWith("All", ignoreCase = true) && !cleanArtist.lowercase().contains(language.lowercase())) {
                val res2 = tryLrclibSearch(cleanTitle, "$cleanTitle $cleanArtist $language")
                if (res2 != null) return@withContext res2
            }

            // 4. LRCLIB Search with Clean Title
            val res3 = tryLrclibSearch(cleanTitle, cleanTitle)
            if (res3 != null) return@withContext res3

            // 5. LRCLIB Search with Clean Title & Language hint
            if (language.isNotBlank() && !language.startsWith("All", ignoreCase = true)) {
                val res4 = tryLrclibSearch(cleanTitle, "$cleanTitle $language")
                if (res4 != null) return@withContext res4
            }

            Log.w("MUESO_LYRICS", "No matching lyrics found on LRCLIB for '$cleanTitle' ('$cleanArtist')")
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MUESO_LYRICS", "Error fetching lyrics from API pipeline for $title", e)
            null
        }
    }

    private fun tryLrclibGet(trackName: String, artistName: String): LyricsData? {
        if (trackName.isBlank()) return null
        return try {
            val encTitle = URLEncoder.encode(trackName, "UTF-8")
            val encArtist = URLEncoder.encode(artistName, "UTF-8")
            val url = if (artistName.isNotBlank()) {
                "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist"
            } else {
                "https://lrclib.net/api/get?track_name=$encTitle"
            }
            Log.d("MUESO_LYRICS", "LRCLIB direct GET request: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuesoMusicPlayer/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("{")) {
                        val jsonObj = JSONObject(jsonStr)
                        val synced = jsonObj.optString("syncedLyrics", "")
                        val plain = jsonObj.optString("plainLyrics", "")

                        if (synced.isNotBlank()) {
                            val parsedLines = LrcParser.parse(synced)
                            if (parsedLines.isNotEmpty()) {
                                Log.d("MUESO_LYRICS", "LRCLIB direct GET returned ${parsedLines.size} synced lines for '$trackName'")
                                return LyricsData(lines = parsedLines)
                            }
                        } else if (plain.isNotBlank()) {
                            Log.d("MUESO_LYRICS", "LRCLIB direct GET returned plain lyrics for '$trackName'")
                            return LyricsData(rawText = plain)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
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

    private fun tryLrclibSearch(targetTitle: String, query: String): LyricsData? {
        return try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"
            Log.d("MUESO_LYRICS", "Searching LRCLIB API for query: '$query' (targetTitle: '$targetTitle')")
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
                            val trackName = item.optString("trackName", "")

                            if (!matchesTrackTitle(trackName, targetTitle)) {
                                Log.d("MUESO_LYRICS", "Skipping LRCLIB item '$trackName' (does not match target '$targetTitle')")
                                continue
                            }

                            val synced = item.optString("syncedLyrics", "")
                            val plain = item.optString("plainLyrics", "")

                            if (synced.isNotBlank() && bestSynced == null) {
                                bestSynced = synced
                            } else if (plain.isNotBlank() && bestPlain == null) {
                                bestPlain = plain
                            }

                            if (bestSynced != null) break
                        }

                        if (!bestSynced.isNullOrBlank()) {
                            val parsedLines = LrcParser.parse(bestSynced)
                            if (parsedLines.isNotEmpty()) {
                                Log.d("MUESO_LYRICS", "Successfully parsed ${parsedLines.size} synced lines via LRCLIB search for '$query'")
                                return LyricsData(lines = parsedLines)
                            }
                        } else if (!bestPlain.isNullOrBlank()) {
                            val parsedLines = LrcParser.parse(bestPlain)
                            Log.d("MUESO_LYRICS", "Found plain lyrics (${parsedLines.size} lines) via LRCLIB search for '$query'")
                            return LyricsData(lines = parsedLines, rawText = bestPlain)
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

    suspend fun searchLrclibCandidates(query: String): List<com.akshay.musicplayer.domain.models.LrclibSearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        return@withContext try {
            val encQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"
            Log.d("MUESO_LYRICS", "Searching LRCLIB candidate list for: '$query'")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuesoMusicPlayer/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("[")) {
                        val jsonArr = JSONArray(jsonStr)
                        val candidates = mutableListOf<com.akshay.musicplayer.domain.models.LrclibSearchResultItem>()
                        for (i in 0 until jsonArr.length()) {
                            val item = jsonArr.optJSONObject(i) ?: continue
                            val id = item.optLong("id", i.toLong())
                            val trackName = item.optString("trackName", "Unknown Title")
                            val artistName = item.optString("artistName", "Unknown Artist")
                            val albumName = item.optString("albumName", "")
                            val duration = item.optInt("duration", 0)
                            val synced = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                            val plain = item.optString("plainLyrics", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                            if (synced != null || plain != null) {
                                candidates.add(
                                    com.akshay.musicplayer.domain.models.LrclibSearchResultItem(
                                        id = id,
                                        trackName = trackName,
                                        artistName = artistName,
                                        albumName = albumName,
                                        durationSeconds = duration,
                                        isSynced = synced != null,
                                        syncedLyrics = synced,
                                        plainLyrics = plain
                                    )
                                )
                            }
                        }
                        Log.d("MUESO_LYRICS", "LRCLIB returned ${candidates.size} candidate items for '$query'")
                        return@withContext candidates
                    }
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("MUESO_LYRICS", "Failed to search LRCLIB candidates for '$query'", e)
            emptyList()
        }
    }

    suspend fun fetchRealTop50GlobalCharts(): List<TrackEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/us/rss/topsongs/limit=50/json"
            Log.d("MUESO_CHARTS", "Fetching real Top 50 Global Chart from open iTunes RSS API...")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuesoMusicPlayer/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        val feed = root.optJSONObject("feed")
                        val entries = feed?.optJSONArray("entry")
                        if (entries != null && entries.length() > 0) {
                            val list = mutableListOf<TrackEntity>()
                            for (i in 0 until entries.length()) {
                                val item = entries.optJSONObject(i) ?: continue
                                val nameObj = item.optJSONObject("im:name")
                                val artistObj = item.optJSONObject("im:artist")
                                val title = nameObj?.optString("label", "") ?: ""
                                val artist = artistObj?.optString("label", "") ?: "Unknown Artist"

                                var artworkUrl: String? = null
                                val imageArr = item.optJSONArray("im:image")
                                if (imageArr != null && imageArr.length() > 0) {
                                    artworkUrl = imageArr.optJSONObject(imageArr.length() - 1)?.optString("label", "")?.takeIf { it.isNotBlank() }
                                }

                                if (title.isNotBlank()) {
                                    val trackId = (title + artist).hashCode().toLong()
                                    list.add(
                                        TrackEntity(
                                            id = trackId,
                                            title = title,
                                            artist = artist,
                                            album = "Top 50 Global Chart",
                                            duration = 210000L,
                                            albumId = 0L,
                                            filePath = "online:$title $artist",
                                            artworkUrl = getHighResArtworkUrl(artworkUrl)
                                        )
                                    )
                                }
                            }
                            if (list.isNotEmpty()) {
                                Log.d("MUESO_CHARTS", "Successfully loaded ${list.size} tracks from iTunes Top 50 Global Chart")
                                return@withContext list
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MUESO_CHARTS", "Failed to fetch iTunes Top 50 Global Chart, falling back to search...", e)
        }
        return@withContext searchOnlineTracks("top 50 global songs")
    }

    suspend fun fetchCuratedPlaylistTracks(query: String, language: String = ""): List<TrackEntity> = withContext(Dispatchers.IO) {
        try {
            val lang = language.trim()
            val isSpecificLang = lang.isNotBlank() && !lang.startsWith("All", ignoreCase = true)
            val isIndianLang = isSpecificLang && listOf("Telugu", "Hindi", "Tamil", "Punjabi", "Malayalam", "Kannada").any { lang.equals(it, ignoreCase = true) }

            val targetQuery = when {
                isSpecificLang && query.contains("indian trending", ignoreCase = true) -> "$lang top hit songs"
                isSpecificLang && query.contains("lofi", ignoreCase = true) -> "$lang lofi songs"
                isSpecificLang && query.contains("workout", ignoreCase = true) -> "$lang workout songs"
                isSpecificLang && query.contains("romantic", ignoreCase = true) -> "$lang romantic songs"
                isSpecificLang && query.contains("party", ignoreCase = true) -> "$lang party dance hits"
                isSpecificLang && query.contains("90s", ignoreCase = true) -> "$lang 90s hits"
                isSpecificLang && !query.lowercase().contains(lang.lowercase()) -> "$lang $query"
                query.contains("indian trending", ignoreCase = true) -> "top indian hit songs"
                else -> query
            }

            val countryParam = if (isIndianLang || query.contains("indian", ignoreCase = true)) "country=IN&" else ""

            val encQuery = URLEncoder.encode(targetQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encQuery&${countryParam}entity=song&limit=30"
            Log.d("MUESO_CURATED", "Fetching curated tracks from iTunes API: $url (Query: '$targetQuery', Lang: '$lang')")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val results = jsonObj.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val tracks = mutableListOf<TrackEntity>()
                            for (i in 0 until results.length()) {
                                val item = results.optJSONObject(i) ?: continue
                                val trackName = item.optString("trackName", "").takeIf { it.isNotBlank() } ?: continue
                                val artistName = item.optString("artistName", "Unknown Artist")
                                val albumName = item.optString("collectionName", "Curated Playlist")
                                val artworkUrl = item.optString("artworkUrl100", "").takeIf { it.isNotBlank() }
                                val durationMs = item.optLong("trackTimeMillis", 210_000L)

                                val trackId = (trackName + artistName).hashCode().toLong()
                                tracks.add(
                                    TrackEntity(
                                        id = trackId,
                                        title = trackName,
                                        artist = artistName,
                                        album = albumName,
                                        duration = durationMs,
                                        albumId = 0L,
                                        filePath = "online:$trackName $artistName",
                                        artworkUrl = getHighResArtworkUrl(artworkUrl)
                                    )
                                )
                            }
                            if (tracks.isNotEmpty()) {
                                Log.d("MUESO_CURATED", "Successfully loaded ${tracks.size} curated tracks via iTunes API for '$query'")
                                return@withContext tracks
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MUESO_CURATED", "iTunes API curated fetch failed for '$query', falling back to searchOnlineTracks", e)
        }

        return@withContext searchOnlineTracks(query)
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

    fun getHighResArtworkUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrEmpty()) return null
        var url = rawUrl
        if (url.contains("mzstatic.com")) {
            return url.replace(Regex("\\d+x\\d+bb"), "600x600bb")
        }
        if (url.contains("yt3.googleusercontent.com") || url.contains("ggpht.com")) {
            return url.replace(Regex("=w\\d+-h\\d+.*"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1080")
        }
        if (url.contains("i.ytimg.com/vi/")) {
            val videoId = url.substringAfter("/vi/").substringBefore("/")
            if (videoId.isNotBlank() && !videoId.contains("http")) {
                return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            }
        }
        return url
    }

    fun getYouTubeArtworkFallbackList(rawUrl: String?, targetQuality: String = "Highest (1080p Maxres)"): List<String> {
        if (rawUrl.isNullOrEmpty()) return emptyList()
        var url = rawUrl
        if (url.contains("mzstatic.com")) {
            val highRes = url.replace(Regex("\\d+x\\d+bb"), "600x600bb")
            return listOf(highRes, url)
        }
        if (url.contains("yt3.googleusercontent.com") || url.contains("ggpht.com")) {
            val highRes = url.replace(Regex("=w\\d+-h\\d+.*"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1080")
            return listOf(highRes, url)
        }
        if (url.contains("i.ytimg.com/vi/")) {
            val videoId = url.substringAfter("/vi/").substringBefore("/")
            if (videoId.isNotBlank() && !videoId.contains("http")) {
                val fullChain = listOf(
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/default.jpg"
                )
                return when {
                    targetQuality.contains("720p") -> fullChain.drop(1)
                    targetQuality.contains("480p") -> fullChain.drop(2)
                    targetQuality.contains("Low") -> listOf("https://i.ytimg.com/vi/$videoId/default.jpg")
                    else -> fullChain // Highest (1080p Maxres)
                }
            }
        }
        return listOf(url)
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
            val compilationRegex = Regex("(?i)\\b(compilation|jukebox|full album|all songs|non stop|non-stop|1 hour|2 hours|3 hours|10 hours|audio jukebox|video jukebox|album mix|best of|mashup)\\b", RegexOption.IGNORE_CASE)
            val shortEditRegex = Regex("(?i)\\b(shorts|short|reel|reels|tiktok|whatsapp status|status edit|status video|30 sec status|30sec|45sec|edit version|speed up|sped up|nightcore)\\b", RegexOption.IGNORE_CASE)

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

                                val compilationRegex = Regex("(?i)\\b(compilation|jukebox|full album|all songs|non stop|non-stop|1 hour|2 hours|3 hours|10 hours|audio jukebox|video jukebox|album mix|best of|mashup)\\b", RegexOption.IGNORE_CASE)
                                val shortEditRegex = Regex("(?i)\\b(shorts|short|reel|reels|tiktok|whatsapp status|status edit|status video|30 sec status|30sec|45sec|edit version|speed up|sped up|nightcore)\\b", RegexOption.IGNORE_CASE)

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
