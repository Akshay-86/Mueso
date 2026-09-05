package com.akshay.musicplayer.data.remote.innertube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class InnerTubeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val TAG = "MUESO_INNERTUBE"
        private const val YTM_BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val CLIENT_VERSION = "1.20240101.01.00"
        private const val CLIENT_NAME = "WEB_REMIX"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private var authCookie: String? = null

    fun setAuthCookie(cookie: String?) {
        this.authCookie = cookie
    }

    fun getAuthCookie(): String? = authCookie

    fun isLoggedIn(): Boolean {
        val c = authCookie
        return !c.isNullOrBlank() && (c.contains("SAPISID") || c.contains("__Secure-1PAPISID") || c.contains("SID"))
    }

    private fun extractCookieValue(cookieString: String?, key: String): String? {
        if (cookieString.isNullOrBlank()) return null
        val regex = Regex("(?:^|;\\s*)$key=([^;]+)")
        return regex.find(cookieString)?.groupValues?.getOrNull(1)
    }

    private fun getSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp $sapisid $origin"
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "$timestamp" + "_" + hex
    }

    private fun buildContext(): JSONObject {
        val userLocale = java.util.Locale.getDefault()
        val country = userLocale.country.takeIf { it.isNotBlank() } ?: "IN"
        val language = userLocale.language.takeIf { it.isNotBlank() } ?: "en"

        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", CLIENT_NAME)
                put("clientVersion", CLIENT_VERSION)
                put("hl", language)
                put("gl", country)
            })
        }
    }

    private fun buildBaseRequest(endpoint: String, payload: JSONObject): Request {
        val url = "$YTM_BASE_URL/$endpoint?prettyPrint=false"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)

        val cookie = authCookie
        if (!cookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookie)
            val sapisid = extractCookieValue(cookie, "SAPISID")
                ?: extractCookieValue(cookie, "__Secure-1PAPISID")
                ?: extractCookieValue(cookie, "__Secure-3PAPISID")

            if (!sapisid.isNullOrBlank()) {
                try {
                    val hash = getSapisidHash(sapisid)
                    requestBuilder.header("Authorization", "SAPISIDHASH $hash")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate SAPISIDHASH for YouTube Music auth", e)
                }
            }
        }

        return requestBuilder
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    // ==========================================
    // 1. SEARCH
    // ==========================================
    suspend fun search(query: String, filter: String? = null): List<InnerTubeTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("query", query)
            if (!filter.isNullOrBlank()) {
                put("params", filter)
            }
        }

        try {
            val request = buildBaseRequest("search", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search returned error code: ${response.code}")
                    return@withContext emptyList()
                }
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseSearchResults(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: '$query'", e)
            return@withContext emptyList()
        }
    }

    private fun parseSearchResults(json: JSONObject): List<InnerTubeTrack> {
        val tracks = mutableListOf<InnerTubeTrack>()
        val seenVideoIds = mutableSetOf<String>()

        fun extractFromItem(item: JSONObject) {
            val track = parseResponsiveListItem(item)
            if (track != null && seenVideoIds.add(track.videoId)) {
                tracks.add(track)
            }
        }

        fun extractFromCard(card: JSONObject) {
            val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
            val title = titleRuns?.optJSONObject(0)?.optString("text")
            val navEndpoint = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            val videoId = navEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
            var artist = "Unknown Artist"
            var durationSec = 0
            var itemType = "Song"
            if (subRuns != null) {
                val candidateTexts = mutableListOf<String>()
                for (k in 0 until subRuns.length()) {
                    val run = subRuns.optJSONObject(k) ?: continue
                    val text = run.optString("text", "").trim()
                    if (text.isBlank() || text == "•" || text == "|") continue
                    if (text.equals("Video", ignoreCase = true)) {
                        itemType = "Video"
                    } else if (text.equals("Song", ignoreCase = true) || text.equals("Single", ignoreCase = true)) {
                        itemType = "Song"
                    }
                    if (text.matches(Regex("\\d+:\\d+"))) {
                        durationSec = parseDurationToSeconds(text)
                    } else if (!text.equals("Song", ignoreCase = true) &&
                        !text.equals("Video", ignoreCase = true) &&
                        !text.equals("Single", ignoreCase = true) &&
                        !text.equals("Album", ignoreCase = true) &&
                        !text.contains("views", ignoreCase = true) &&
                        !text.contains("plays", ignoreCase = true)
                    ) {
                        candidateTexts.add(text)
                    }
                }
                if (candidateTexts.isNotEmpty()) {
                    artist = candidateTexts[0]
                }
            }

            val thumbList = card.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")
                ?: if (!videoId.isNullOrBlank()) "https://i.ytimg.com/vi/$videoId/hq720.jpg" else null

            if (!videoId.isNullOrBlank() && !title.isNullOrBlank() && seenVideoIds.add(videoId)) {
                tracks.add(
                    InnerTubeTrack(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        durationSec = durationSec,
                        artworkUrl = getHighResArtworkUrl(thumbUrl),
                        itemType = itemType
                    )
                )
            }

            val cardContents = card.optJSONArray("contents")
            if (cardContents != null) {
                for (k in 0 until cardContents.length()) {
                    val cardItem = cardContents.optJSONObject(k)?.optJSONObject("musicResponsiveListItemRenderer")
                    if (cardItem != null) extractFromItem(cardItem)
                }
            }
        }

        val contents = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue

            // 1. Check musicCardShelfRenderer (Top result)
            val card = section.optJSONObject("musicCardShelfRenderer")
            if (card != null) {
                extractFromCard(card)
            }

            // 2. Check musicShelfRenderer
            val shelf = section.optJSONObject("musicShelfRenderer")
            if (shelf != null) {
                val items = shelf.optJSONArray("contents")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        extractFromItem(item)
                    }
                }
            }

            // 3. Check itemSectionRenderer (Individual rows)
            val itemSection = section.optJSONObject("itemSectionRenderer")
            if (itemSection != null) {
                val items = itemSection.optJSONArray("contents")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        extractFromItem(item)
                    }
                }
            }
        }

        // Prioritize songs first, then videos and other results
        return tracks.sortedWith(
            compareByDescending<InnerTubeTrack> { it.itemType.equals("Song", ignoreCase = true) }
        )
    }

    private fun parseResponsiveListItem(item: JSONObject): InnerTubeTrack? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() == 0) return null

        // Column 1: Title & videoId
        val col1 = flexColumns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
        val title = col1.optJSONObject(0)?.optString("text") ?: return null
        val navEndpoint = col1.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            ?: item.optJSONObject("navigationEndpoint")
            ?: item.optJSONObject("playNavigationEndpoint")
            ?: item.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
        val videoId = navEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?: item.optJSONObject("playlistItemData")?.optString("videoId")
            ?: return null

        if (videoId.isBlank()) return null

        // Column 2: Artists, Album, Duration
        var artistName = "Unknown Artist"
        var albumName: String? = null
        var durationSec = 0
        var itemType = "Song"
        val artistRefs = mutableListOf<InnerTubeArtistRef>()

        if (flexColumns.length() > 1) {
            val col2 = flexColumns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            if (col2 != null) {
                val runsList = mutableListOf<String>()
                for (k in 0 until col2.length()) {
                    val run = col2.optJSONObject(k) ?: continue
                    val text = run.optString("text", "").trim()
                    if (text == "•" || text == "|" || text.isBlank()) continue
                    runsList.add(text)

                    if (text.equals("Video", ignoreCase = true)) {
                        itemType = "Video"
                    } else if (text.equals("Song", ignoreCase = true) || text.equals("Single", ignoreCase = true)) {
                        itemType = "Song"
                    }

                    val browseId = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (!browseId.isNullOrBlank() && browseId.startsWith("UC")) {
                        artistRefs.add(InnerTubeArtistRef(browseId, text))
                    }
                }
                val filteredRuns = runsList.filter { text ->
                    !text.equals("Song", ignoreCase = true) &&
                    !text.equals("Video", ignoreCase = true) &&
                    !text.equals("Single", ignoreCase = true) &&
                    !text.contains("views", ignoreCase = true) &&
                    !text.contains("plays", ignoreCase = true)
                }

                // Explicitly find duration run matching timestamp pattern (m:ss or h:mm:ss)
                val durRun = filteredRuns.find { it.matches(Regex("\\d+:\\d+(:\\d+)?")) }
                if (durRun != null) {
                    durationSec = parseDurationToSeconds(durRun)
                }

                val nonDurRuns = filteredRuns.filter { !it.matches(Regex("\\d+:\\d+(:\\d+)?")) }
                if (nonDurRuns.isNotEmpty()) {
                    artistName = nonDurRuns[0]
                    if (nonDurRuns.size >= 2) {
                        albumName = nonDurRuns[1]
                    }
                }
            }
        }

        // Fixed column (often duration)
        val fixedColumns = item.optJSONArray("fixedColumns")
        if (fixedColumns != null && fixedColumns.length() > 0) {
            val durText = fixedColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            if (!durText.isNullOrBlank()) {
                durationSec = parseDurationToSeconds(durText)
            }
        }

        // Thumbnail
        val thumbList = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val artworkUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"

        return InnerTubeTrack(
            videoId = videoId,
            title = title,
            artist = artistName,
            artists = artistRefs,
            album = albumName,
            durationSec = durationSec,
            artworkUrl = getHighResArtworkUrl(artworkUrl),
            itemType = itemType
        )
    }

    // ==========================================
    // 2. RADIO / RECOMMENDATIONS (/next)
    // ==========================================
    suspend fun getRadioTracks(videoId: String): List<InnerTubeTrack> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val playlistId = if (videoId.startsWith("RDAMVM")) videoId else "RDAMVM$videoId"
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("videoId", videoId)
            put("playlistId", playlistId)
            put("isAutomix", true)
            put("enablePersistentPlaylistPanel", true)
        }

        try {
            val request = buildBaseRequest("next", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Radio /next request returned code: ${response.code}")
                    return@withContext emptyList()
                }
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseRadioResults(json, videoId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Radio /next request failed for videoId: '$videoId'", e)
            return@withContext emptyList()
        }
    }

    private fun parseRadioResults(json: JSONObject, seedVideoId: String): List<InnerTubeTrack> {
        val tracks = mutableListOf<InnerTubeTrack>()
        val tabs = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()

        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            val queueRenderer = tab.optJSONObject("content")?.optJSONObject("musicQueueRenderer") ?: continue
            val items = queueRenderer.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                val trackId = item.optString("videoId")
                if (trackId.isBlank() || trackId == seedVideoId) continue

                val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Title"
                val artistRuns = item.optJSONObject("shortBylineText")?.optJSONArray("runs")
                val artist = artistRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"
                val durationText = item.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                val durationSec = parseDurationToSeconds(durationText)

                val thumbnails = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    ?: "https://i.ytimg.com/vi/$trackId/hq720.jpg"

                tracks.add(
                    InnerTubeTrack(
                        videoId = trackId,
                        title = title,
                        artist = artist,
                        durationSec = durationSec,
                        artworkUrl = getHighResArtworkUrl(thumbUrl)
                    )
                )
            }
        }
        return tracks
    }

    // ==========================================
    // 3. EXPLORE, HOME & CHARTS PLAYLISTS (/browse)
    // ==========================================
    suspend fun getHomeFeedShelves(): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", "FEmusic_home")
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                val shelves = parseBrowseShelves(json)
                if (shelves.isNotEmpty()) return@withContext shelves
                return@withContext getExploreShelves()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHomeFeedShelves failed, falling back to explore", e)
            return@withContext getExploreShelves()
        }
    }

    suspend fun getExploreShelves(): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", "FEmusic_explore")
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseBrowseShelves(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getExploreShelves failed", e)
            return@withContext emptyList()
        }
    }

    suspend fun getChartsShelves(): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", "FEmusic_charts")
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseBrowseShelves(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getChartsShelves failed", e)
            return@withContext emptyList()
        }
    }

    suspend fun searchTracks(query: String): List<InnerTubeTrack> = search(query)

    suspend fun searchPlaylists(query: String, filter: String? = null): List<InnerTubePlaylist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("query", query)
            put("params", filter ?: "Eg-KAQwIABAAGAEgACgB")
        }

        try {
            val request = buildBaseRequest("search", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseSearchPlaylists(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchPlaylists failed for query: '$query'", e)
            return@withContext emptyList()
        }
    }

    suspend fun searchArtists(query: String): List<InnerTubeArtist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("query", query)
            put("params", "EgWKAQIgAWoSEAQQCRADEAUQEBAKEBUQERAO")
        }

        try {
            val request = buildBaseRequest("search", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                return@withContext parseSearchArtists(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchArtists failed for query: '$query'", e)
            return@withContext emptyList()
        }
    }

    private fun parseSearchArtists(json: JSONObject): List<InnerTubeArtist> {
        val artists = mutableListOf<InnerTubeArtist>()
        val seenIds = mutableSetOf<String>()

        val contents = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue

            // 1. Check card shelf (Top result)
            val card = section.optJSONObject("musicCardShelfRenderer")
            if (card != null) {
                val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
                val title = titleRuns?.optJSONObject(0)?.optString("text")
                val browseId = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?: card.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")

                var subs = ""
                val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
                if (subRuns != null) {
                    val parts = mutableListOf<String>()
                    for (k in 0 until subRuns.length()) {
                        val text = subRuns.optJSONObject(k)?.optString("text", "")?.trim() ?: ""
                        if (text.isNotBlank() && text != "•" && text != "|") {
                            parts.add(text)
                        }
                    }
                    subs = parts.joinToString(" • ")
                }

                val thumbList = card.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")

                if (!title.isNullOrBlank() && !browseId.isNullOrBlank() && seenIds.add(browseId)) {
                    artists.add(
                        InnerTubeArtist(
                            id = browseId,
                            name = title,
                            subscribers = subs,
                            thumbnailUrl = getHighResArtworkUrl(thumbUrl)
                        )
                    )
                }
            }

            // 2. Shelf contents
            val shelf = section.optJSONObject("musicShelfRenderer") ?: continue
            val items = shelf.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val flexCols = item.optJSONArray("flexColumns") ?: continue
                if (flexCols.length() == 0) continue

                val col1Runs = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                val title = col1Runs.optJSONObject(0)?.optString("text") ?: continue

                var browseId: String? = null
                for (r in 0 until col1Runs.length()) {
                    val nav = col1Runs.optJSONObject(r)?.optJSONObject("navigationEndpoint")
                    val bId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (!bId.isNullOrBlank()) {
                        browseId = bId
                        break
                    }
                }
                if (browseId == null) {
                    browseId = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                }

                if (browseId.isNullOrBlank()) continue
                if (!seenIds.add(browseId)) continue

                var subtitle = ""
                if (flexCols.length() > 1) {
                    val col2Runs = flexCols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    if (col2Runs != null) {
                        val parts = mutableListOf<String>()
                        for (k in 0 until col2Runs.length()) {
                            val text = col2Runs.optJSONObject(k)?.optString("text", "")?.trim() ?: ""
                            if (text.isNotBlank() && text != "•" && text != "|") {
                                parts.add(text)
                            }
                        }
                        subtitle = parts.joinToString(" • ")
                    }
                }

                val thumbList = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")

                artists.add(
                    InnerTubeArtist(
                        id = browseId,
                        name = title,
                        subscribers = subtitle,
                        thumbnailUrl = getHighResArtworkUrl(thumbUrl)
                    )
                )
            }
        }
        return artists
    }

    suspend fun getArtistPage(browseId: String): InnerTubeArtistPage? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", browseId)
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                return@withContext parseArtistPage(browseId, json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getArtistPage failed for browseId: '$browseId'", e)
            return@withContext null
        }
    }

    private fun parseArtistPage(browseId: String, json: JSONObject): InnerTubeArtistPage {
        val header = json.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
            ?: json.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
            ?: json.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")

        var artistName = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Artist"
        if (artistName.isBlank() || artistName == "Artist") {
            val altTitle = json.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            if (!altTitle.isNullOrBlank()) artistName = altTitle
        }

        // Bio / Description
        var description = ""
        val descRuns = header?.optJSONObject("description")?.optJSONArray("runs")
        if (descRuns != null) {
            val sb = StringBuilder()
            for (i in 0 until descRuns.length()) {
                sb.append(descRuns.optJSONObject(i)?.optString("text", "") ?: "")
            }
            description = sb.toString().trim()
        } else {
            description = header?.optJSONObject("description")?.optString("simpleText", "") ?: ""
        }

        // Monthly audience / Subscribers
        var subscribers = ""
        val subBtnText = header?.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")
            ?.optJSONObject("subscriberCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        val strapline = header?.optJSONObject("straplineTextOne")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        val subText = header?.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        subscribers = when {
            !subBtnText.isNullOrBlank() -> subBtnText
            !strapline.isNullOrBlank() -> strapline
            !subText.isNullOrBlank() -> subText
            else -> ""
        }

        // Thumbnails / Banner
        val thumbList = header?.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: header?.optJSONObject("foregroundThumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")

        // Radio / Play endpoints
        val playBtnNav = header?.optJSONObject("playButton")?.optJSONObject("buttonRenderer")?.optJSONObject("navigationEndpoint")
        val radioBtnNav = header?.optJSONObject("startRadioButton")?.optJSONObject("buttonRenderer")?.optJSONObject("navigationEndpoint")
        val radioPlaylistId = radioBtnNav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
            ?: radioBtnNav?.optJSONObject("watchEndpoint")?.optString("playlistId")
        val radioVideoId = radioBtnNav?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?: playBtnNav?.optJSONObject("watchEndpoint")?.optString("videoId")
        val shufflePlaylistId = playBtnNav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")

        // Parse Sections
        val topSongs = mutableListOf<InnerTubeTrack>()
        val albums = mutableListOf<InnerTubePlaylist>()
        val singlesAndEPs = mutableListOf<InnerTubePlaylist>()
        val videos = mutableListOf<InnerTubeTrack>()
        val livePerformances = mutableListOf<InnerTubeTrack>()
        val featuredOn = mutableListOf<InnerTubePlaylist>()
        val playlistsByArtist = mutableListOf<InnerTubePlaylist>()
        val similarArtists = mutableListOf<InnerTubeArtist>()

        val sectionList = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")

        if (sectionList != null) {
            for (i in 0 until sectionList.length()) {
                val sectionObj = sectionList.optJSONObject(i) ?: continue
                val musicShelf = sectionObj.optJSONObject("musicShelfRenderer")
                val carousel = sectionObj.optJSONObject("musicCarouselShelfRenderer")

                // 1. Top Songs shelf
                if (musicShelf != null) {
                    val shelfTitle = musicShelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                    val items = musicShelf.optJSONArray("contents")
                    if (items != null) {
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val track = parseResponsiveListItem(item)
                            if (track != null) {
                                topSongs.add(track)
                            }
                        }
                    }
                }

                // 2. Carousel Shelves (Albums, Singles, Videos, Featured, Fans might like)
                if (carousel != null) {
                    val headerObj = carousel.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    val shelfTitle = headerObj?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                    val titleLower = shelfTitle.lowercase()
                    val items = carousel.optJSONArray("contents") ?: continue

                    for (j in 0 until items.length()) {
                        val itemObj = items.optJSONObject(j) ?: continue
                        val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
                        val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer")

                        if (twoRow != null) {
                            val title = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                            val subtitle = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.let { runs ->
                                val parts = mutableListOf<String>()
                                for (r in 0 until runs.length()) {
                                    val t = runs.optJSONObject(r)?.optString("text", "")?.trim() ?: ""
                                    if (t.isNotBlank() && t != "•" && t != "|") parts.add(t)
                                }
                                parts.joinToString(" • ")
                            } ?: ""

                            val nav = twoRow.optJSONObject("navigationEndpoint")
                            val itemBrowseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                            val watchEndpoint = nav?.optJSONObject("watchEndpoint")
                            val videoId = watchEndpoint?.optString("videoId")
                            val playlistId = watchEndpoint?.optString("playlistId")
                                ?: itemBrowseId?.removePrefix("VL")

                            val itemThumb = twoRow.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbArt = itemThumb?.optJSONObject(itemThumb.length() - 1)?.optString("url")
                            val highResArt = getHighResArtworkUrl(thumbArt)

                            when {
                                titleLower.contains("fan") || titleLower.contains("similar") || titleLower.contains("like") || (itemBrowseId != null && itemBrowseId.startsWith("UC")) -> {
                                    if (!itemBrowseId.isNullOrBlank()) {
                                        similarArtists.add(
                                            InnerTubeArtist(
                                                id = itemBrowseId,
                                                name = title,
                                                subscribers = subtitle,
                                                thumbnailUrl = highResArt
                                            )
                                        )
                                    }
                                }
                                titleLower.contains("single") || titleLower.contains("ep") -> {
                                    val plId = playlistId ?: itemBrowseId ?: ""
                                    if (plId.isNotBlank()) {
                                        singlesAndEPs.add(
                                            InnerTubePlaylist(
                                                id = plId,
                                                title = title,
                                                subtitle = subtitle,
                                                artworkUrl = highResArt
                                            )
                                        )
                                    }
                                }
                                titleLower.contains("album") -> {
                                    val plId = playlistId ?: itemBrowseId ?: ""
                                    if (plId.isNotBlank()) {
                                        albums.add(
                                            InnerTubePlaylist(
                                                id = plId,
                                                title = title,
                                                subtitle = subtitle,
                                                artworkUrl = highResArt
                                            )
                                        )
                                    }
                                }
                                titleLower.contains("live") -> {
                                    if (!videoId.isNullOrBlank()) {
                                        livePerformances.add(
                                            InnerTubeTrack(
                                                videoId = videoId,
                                                title = title,
                                                artist = artistName,
                                                artworkUrl = highResArt,
                                                itemType = "Video"
                                            )
                                        )
                                    }
                                }
                                titleLower.contains("video") -> {
                                    if (!videoId.isNullOrBlank()) {
                                        videos.add(
                                            InnerTubeTrack(
                                                videoId = videoId,
                                                title = title,
                                                artist = artistName,
                                                artworkUrl = highResArt,
                                                itemType = "Video"
                                            )
                                        )
                                    }
                                }
                                titleLower.contains("featured") -> {
                                    val plId = playlistId ?: itemBrowseId ?: ""
                                    if (plId.isNotBlank()) {
                                        featuredOn.add(
                                            InnerTubePlaylist(
                                                id = plId,
                                                title = title,
                                                subtitle = subtitle,
                                                artworkUrl = highResArt
                                            )
                                        )
                                    }
                                }
                                else -> {
                                    val plId = playlistId ?: itemBrowseId ?: ""
                                    if (plId.isNotBlank()) {
                                        playlistsByArtist.add(
                                            InnerTubePlaylist(
                                                id = plId,
                                                title = title,
                                                subtitle = subtitle,
                                                artworkUrl = highResArt
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (responsive != null) {
                            val track = parseResponsiveListItem(responsive)
                            if (track != null) {
                                if (titleLower.contains("video")) {
                                    videos.add(track)
                                } else {
                                    topSongs.add(track)
                                }
                            }
                        }
                    }
                }
            }
        }

        return InnerTubeArtistPage(
            id = browseId,
            name = artistName,
            subscribers = subscribers,
            description = description,
            bannerUrl = thumbUrl,
            thumbnailUrl = thumbUrl,
            radioPlaylistId = radioPlaylistId,
            radioVideoId = radioVideoId,
            shufflePlaylistId = shufflePlaylistId,
            topSongs = topSongs,
            albums = albums,
            singlesAndEPs = singlesAndEPs,
            videos = videos,
            livePerformances = livePerformances,
            featuredOn = featuredOn,
            playlistsByArtist = playlistsByArtist,
            similarArtists = similarArtists
        )
    }


    private fun parseSearchPlaylists(json: JSONObject): List<InnerTubePlaylist> {
        val playlists = mutableListOf<InnerTubePlaylist>()
        val seenIds = mutableSetOf<String>()

        val contents = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue
            val shelf = section.optJSONObject("musicShelfRenderer") ?: continue
            val items = shelf.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val flexCols = item.optJSONArray("flexColumns") ?: continue
                if (flexCols.length() == 0) continue

                val col1Runs = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                val title = col1Runs.optJSONObject(0)?.optString("text") ?: continue

                var browseId: String? = null
                for (r in 0 until col1Runs.length()) {
                    val nav = col1Runs.optJSONObject(r)?.optJSONObject("navigationEndpoint")
                    val bId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (!bId.isNullOrBlank()) {
                        browseId = bId
                        break
                    }
                }
                if (browseId == null) {
                    browseId = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                }

                if (browseId.isNullOrBlank()) continue
                val playlistId = if (browseId.startsWith("VL")) browseId.removePrefix("VL") else browseId
                if (!seenIds.add(playlistId)) continue

                var subtitle = ""
                if (flexCols.length() > 1) {
                    val col2Runs = flexCols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    if (col2Runs != null) {
                        val parts = mutableListOf<String>()
                        for (k in 0 until col2Runs.length()) {
                            val text = col2Runs.optJSONObject(k)?.optString("text", "")?.trim() ?: ""
                            if (text.isNotBlank() && text != "•" && text != "|") {
                                parts.add(text)
                            }
                        }
                        subtitle = parts.joinToString(" • ")
                    }
                }

                val thumbList = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")

                playlists.add(
                    InnerTubePlaylist(
                        id = playlistId,
                        title = title,
                        subtitle = subtitle,
                        artworkUrl = getHighResArtworkUrl(thumbUrl)
                    )
                )
            }
        }
        return playlists
    }

    private val moodParamsMap = mapOf(
        "chill" to "ggMPOg1uX1JOQWZFeDByc2Jm",
        "relax" to "ggMPOg1uX1JOQWZFeDByc2Jm",
        "workout" to "ggMPOg1uX09LWkhnTjRGRUJh",
        "work out" to "ggMPOg1uX09LWkhnTjRGRUJh",
        "energize" to "ggMPOg1uX2lRZUZiMnNrQnJW",
        "energise" to "ggMPOg1uX2lRZUZiMnNrQnJW",
        "feel good" to "ggMPOg1uXzZQbDB5eThLRTQ3",
        "focus" to "ggMPOg1uX0NvNGNhWThMYWRh",
        "gaming" to "ggMPOg1uX3NmUVV4Vzl3WGQ0",
        "party" to "ggMPOg1uX0pmQ0s2V0JRclZs",
        "romance" to "ggMPOg1uX0FzQ2FhZWtUY211",
        "sad" to "ggMPOg1uX0JLQ0gySWZKZVY1",
        "sleep" to "ggMPOg1uX1MxaFQ3Z0JMZkN4",
        "commute" to "ggMPOg1uX044Z2o5WERLckpU",
        "pop" to "ggMPOg1uX1lLQkxHbHhWQUUy",
        "hip-hop" to "ggMPOg1uX0M2dmRieXNxTW1s",
        "rock" to "ggMPOg1uXzJKTm5jUEZ5Uzlu",
        "indie & alternative" to "ggMPOg1uX21NWWpBbU01SDgy",
        "indie" to "ggMPOg1uX21NWWpBbU01SDgy",
        "dance & electronic" to "ggMPOg1uX1NPTld3SDN3WGs4",
        "electronic" to "ggMPOg1uX1NPTld3SDN3WGs4",
        "r&b & soul" to "ggMPOg1uX2JxQ2hxc2J5UFhR",
        "r&b" to "ggMPOg1uX2JxQ2hxc2J5UFhR",
        "k-pop" to "ggMPOg1uX0JrbjBDOFFPSzJW",
        "classical" to "ggMPOg1uX1N4VmduTmdUR3dm",
        "jazz" to "ggMPOg1uX3lPcDFRaE9wM1BS",
        "hindi" to "ggMPOg1uX2ZvbzNJMzJwRkFT",
        "punjabi" to "ggMPOg1uX1ZKNkRodjF2YWxv",
        "telugu" to "ggMPOg1uX0syaEVmTXhSOVl6",
        "tamil" to "ggMPOg1uX2p2emtjU3J3ZVFB",
        "indian pop" to "ggMPOg1uXzNleFNpSmk2TTcy",
        "desi hip-hop" to "ggMPOg1uX3VtYUhGSmtKdlhr",
        "devotional" to "ggMPOg1uX3g1dEo4cmZVY1Jm"
    )

    private val dynamicMoodParamsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getShelvesForMood(mood: String): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val normalized = mood.trim().lowercase()
        if (normalized == "all") {
            return@withContext if (isLoggedIn()) getHomeFeedShelves() else getExploreShelves()
        }

        try {
            var params = moodParamsMap[normalized] ?: dynamicMoodParamsCache[normalized]

            if (params == null) {
                val fetchedMap = fetchMoodsAndGenresMap()
                dynamicMoodParamsCache.putAll(fetchedMap)
                params = dynamicMoodParamsCache[normalized]
            }

            if (!params.isNullOrBlank()) {
                val payload = JSONObject().apply {
                    put("context", buildContext())
                    put("browseId", "FEmusic_moods_and_genres_category")
                    put("params", params)
                }
                val request = buildBaseRequest("browse", payload)
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val shelves = parseBrowseShelves(json)
                        if (shelves.isNotEmpty()) {
                            Log.d(TAG, "getShelvesForMood parsed ${shelves.size} shelves for $mood")
                            return@withContext shelves
                        }
                    }
                }
            }

            // Fallback: search playlists and tracks for this mood
            val searchResults = searchPlaylists("$mood hits")
            val topSongs = searchTracks("$mood songs")
            val fallbackShelves = mutableListOf<InnerTubeShelf>()
            if (topSongs.isNotEmpty()) {
                fallbackShelves.add(
                    InnerTubeShelf(
                        title = "Top $mood Tracks",
                        subtitle = "Trending in $mood",
                        tracks = topSongs
                    )
                )
            }
            if (searchResults.isNotEmpty()) {
                fallbackShelves.add(
                    InnerTubeShelf(
                        title = "$mood Playlists & Mixes",
                        subtitle = "Curated for $mood",
                        playlists = searchResults
                    )
                )
            }
            if (fallbackShelves.isNotEmpty()) {
                return@withContext fallbackShelves
            }
        } catch (e: Exception) {
            Log.e(TAG, "getShelvesForMood failed for $mood", e)
        }
        return@withContext getExploreShelves()
    }

    private suspend fun fetchMoodsAndGenresMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        try {
            val payload = JSONObject().apply {
                put("context", buildContext())
                put("browseId", "FEmusic_moods_and_genres")
            }
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyMap()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyMap())
                val sections = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return@withContext emptyMap()

                for (i in 0 until sections.length()) {
                    val section = sections.optJSONObject(i) ?: continue
                    val grid = section.optJSONObject("gridRenderer") ?: section.optJSONObject("musicGridRenderer") ?: continue
                    val items = grid.optJSONArray("items") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val btn = item.optJSONObject("musicNavigationButtonRenderer") ?: continue
                        val btnText = btn.optJSONObject("buttonText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()?.lowercase() ?: continue
                        val nav = btn.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: continue
                        val p = nav.optString("params")
                        if (p.isNotBlank()) {
                            map[btnText] = p
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchMoodsAndGenresMap error", e)
        }
        return@withContext map
    }

    private fun parseBrowseShelves(json: JSONObject): List<InnerTubeShelf> {
        val shelves = mutableListOf<InnerTubeShelf>()
        val sectionList = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until sectionList.length()) {
            val sectionObj = sectionList.optJSONObject(i) ?: continue
            val carousel = sectionObj.optJSONObject("musicCarouselShelfRenderer")
            val musicShelf = sectionObj.optJSONObject("musicShelfRenderer")
            val grid = sectionObj.optJSONObject("gridRenderer") ?: sectionObj.optJSONObject("musicGridRenderer")

            val shelfTitle = carousel?.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: musicShelf?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: grid?.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: "Featured"

            val shelfSubtitle = carousel?.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: carousel?.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")?.optJSONArray("runs")?.let { if (it.length() > 1) it.optJSONObject(1)?.optString("text") else null }
                ?: musicShelf?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""

            val items = carousel?.optJSONArray("contents")
                ?: musicShelf?.optJSONArray("contents")
                ?: grid?.optJSONArray("items")
                ?: continue

            val playlistsInShelf = mutableListOf<InnerTubePlaylist>()
            val tracksInShelf = mutableListOf<InnerTubeTrack>()

            for (j in 0 until items.length()) {
                val itemObj = items.optJSONObject(j) ?: continue
                val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
                val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer")

                if (twoRow != null) {
                    val title = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                    val subtitle = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                    val navEndpoint = twoRow.optJSONObject("navigationEndpoint")
                    val browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                    val watchEndpoint = navEndpoint?.optJSONObject("watchEndpoint")
                    val playlistId = watchEndpoint?.optString("playlistId")
                    val videoId = watchEndpoint?.optString("videoId")

                    val thumbList = twoRow.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val artworkUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url") ?: ""
                    val highResArt = getHighResArtworkUrl(artworkUrl)

                    // If it's a song item (has videoId and is not explicitly a playlist/album browseId)
                    if (!videoId.isNullOrBlank() && (browseId.isNullOrBlank() || (!browseId.startsWith("VL") && !browseId.startsWith("MPRE") && !browseId.startsWith("UC")))) {
                        tracksInShelf.add(
                            InnerTubeTrack(
                                videoId = videoId,
                                title = title,
                                artist = subtitle.ifBlank { "YouTube Music" },
                                artworkUrl = highResArt
                            )
                        )
                    } else {
                        val targetId = when {
                            !browseId.isNullOrBlank() -> browseId
                            !playlistId.isNullOrBlank() -> playlistId
                            !videoId.isNullOrBlank() -> "online:$videoId"
                            else -> ""
                        }
                        if (targetId.isNotBlank()) {
                            playlistsInShelf.add(
                                InnerTubePlaylist(
                                    id = targetId,
                                    title = title,
                                    subtitle = subtitle,
                                    artworkUrl = highResArt
                                )
                            )
                        }
                    }
                } else if (responsive != null) {
                    val track = parseResponsiveListItem(responsive)
                    if (track != null) {
                        tracksInShelf.add(track)
                    }
                }
            }

            if (playlistsInShelf.isNotEmpty() || tracksInShelf.isNotEmpty()) {
                shelves.add(
                    InnerTubeShelf(
                        title = shelfTitle,
                        subtitle = shelfSubtitle,
                        playlists = playlistsInShelf,
                        tracks = tracksInShelf
                    )
                )
            }
        }
        return shelves
    }

    // ==========================================
    // 4. PLAYLIST TRACKS (/browse)
    // ==========================================
    suspend fun getPlaylistTracks(playlistId: String): List<InnerTubeTrack> = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("online:")) {
            val videoId = playlistId.removePrefix("online:")
            val radioTracks = getRadioTracks(videoId)
            if (radioTracks.isNotEmpty()) return@withContext radioTracks
        }

        val cleanBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", cleanBrowseId)
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "getPlaylistTracks returned error: ${response.code}")
                    if (playlistId.contains("RD")) {
                        val videoId = playlistId.removePrefix("VL").removePrefix("RDAMVM").removePrefix("RD")
                        return@withContext getRadioTracks(videoId)
                    }
                    return@withContext emptyList()
                }
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                val parsed = parsePlaylistTracks(json)
                val desc = parsePlaylistDescription(json)
                if (!desc.isNullOrBlank()) {
                    val cleanId = playlistId.removePrefix("VL")
                    playlistDescriptions[cleanId] = desc
                    playlistDescriptions[playlistId] = desc
                }
                if (parsed.isEmpty() && playlistId.contains("RD")) {
                    val videoId = playlistId.removePrefix("VL").removePrefix("RDAMVM").removePrefix("RD")
                    return@withContext getRadioTracks(videoId)
                }
                return@withContext parsed
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistTracks failed for playlistId: $playlistId", e)
            if (playlistId.contains("RD")) {
                val videoId = playlistId.removePrefix("VL").removePrefix("RDAMVM").removePrefix("RD")
                return@withContext getRadioTracks(videoId)
            }
            return@withContext emptyList()
        }
    }

    private val playlistDescriptions = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCachedPlaylistDescription(playlistId: String): String? {
        val cleanId = playlistId.removePrefix("VL")
        return playlistDescriptions[cleanId] ?: playlistDescriptions[playlistId]
    }

    suspend fun fetchPlaylistDescription(playlistId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val cached = playlistDescriptions[cleanId] ?: playlistDescriptions[playlistId]
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }

        val cleanBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", cleanBrowseId)
        }
        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val desc = parsePlaylistDescription(json)
                if (!desc.isNullOrBlank()) {
                    playlistDescriptions[cleanId] = desc
                    playlistDescriptions[playlistId] = desc
                }
                return@withContext desc
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlaylistDescription failed for $playlistId", e)
            return@withContext null
        }
    }

    private fun extractRunsText(obj: Any?): String? {
        if (obj == null) return null
        if (obj is String) {
            val trimmed = obj.trim()
            if (trimmed.isNotBlank()) return trimmed
            return null
        }
        if (obj is JSONObject) {
            val directText = obj.optString("simpleText", "").ifBlank { obj.optString("text", "") }
            if (directText.isNotBlank()) return directText

            val runs = obj.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    val runObj = runs.optJSONObject(i)
                    val text = runObj?.optString("text", "") ?: ""
                    sb.append(text)
                }
                val res = sb.toString().trim()
                if (res.isNotBlank()) return res
            }
        }
        return null
    }

    private fun parsePlaylistDescription(json: JSONObject): String? {
        fun extractFromNode(node: Any?, depth: Int = 0): String? {
            if (node == null || depth > 10) return null
            if (node is JSONObject) {
                // Priority 1: User-editable playlist header (for user-owned playlists)
                val editHdr = node.optJSONObject("musicPlaylistEditHeaderRenderer")
                    ?: node.optJSONObject("editHeader")?.optJSONObject("musicPlaylistEditHeaderRenderer")
                if (editHdr != null) {
                    val descNode = editHdr.opt("description")
                    val descText = extractRunsText(descNode)
                    if (!descText.isNullOrBlank() && !descText.startsWith("Playlist •", ignoreCase = true) && !descText.startsWith("Album •", ignoreCase = true)) {
                        return descText
                    }
                }

                // Priority 2: Description shelf renderer (for curated/public playlists with description shelves)
                val shelfDesc = node.optJSONObject("musicDescriptionShelfRenderer")?.opt("description")
                if (shelfDesc != null) {
                    val descText = extractRunsText(shelfDesc)
                    if (!descText.isNullOrBlank() && !descText.startsWith("Playlist •", ignoreCase = true) && !descText.startsWith("Album •", ignoreCase = true)) {
                        return descText
                    }
                }

                // Priority 3: Responsive header description
                val respHdr = node.optJSONObject("musicResponsiveHeaderRenderer")
                    ?: node.optJSONObject("musicDetailHeaderRenderer")
                if (respHdr != null) {
                    val descObj = respHdr.opt("description")
                    if (descObj is JSONObject) {
                        val innerShelf = descObj.optJSONObject("musicDescriptionShelfRenderer")?.opt("description")
                        val innerShelfText = extractRunsText(innerShelf)
                        if (!innerShelfText.isNullOrBlank() && !innerShelfText.startsWith("Playlist •", ignoreCase = true)) {
                            return innerShelfText
                        }
                    }
                    val descText = extractRunsText(descObj)
                    if (!descText.isNullOrBlank() && !descText.startsWith("Playlist •", ignoreCase = true) && !descText.startsWith("Album •", ignoreCase = true)) {
                        return descText
                    }
                }

                // Priority 4: Search children, strictly skipping microformat, accessibility, tracking, and track list items
                for (key in node.keys()) {
                    if (key == "microformat" || key == "microformatDataRenderer" || key == "accessibility" ||
                        key == "accessibilityData" || key == "trackingParams" || key == "musicResponsiveListItemRenderer" ||
                        key == "playlistVideoRenderer" || key == "continuations" || key == "subtitle" || key == "secondSubtitle" || key == "straplineTextOne" || key == "straplineTextTwo"
                    ) {
                        continue
                    }
                    val child = node.opt(key)
                    val res = extractFromNode(child, depth + 1)
                    if (!res.isNullOrBlank()) return res
                }
            } else if (node is org.json.JSONArray) {
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    val res = extractFromNode(item, depth + 1)
                    if (!res.isNullOrBlank()) return res
                }
            }
            return null
        }

        return extractFromNode(json)
    }

    private fun parsePlaylistTracks(json: JSONObject): List<InnerTubeTrack> {
        val tracks = mutableListOf<InnerTubeTrack>()
        val twoColumnSections = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")

        val singleColumnSections = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")

        val sectionList = twoColumnSections ?: singleColumnSections ?: return emptyList()

        for (i in 0 until sectionList.length()) {
            val shelf = sectionList.optJSONObject(i)?.optJSONObject("musicPlaylistShelfRenderer")
                ?: sectionList.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue

            val items = shelf.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val track = parseResponsiveListItem(item)
                if (track != null) {
                    tracks.add(track)
                }
            }
        }
        return tracks
    }

    // ==========================================
    // 5. USER AUTHENTICATED LIBRARY (LIKED SONGS & PLAYLISTS)
    // ==========================================
    suspend fun getUserPlaylists(): List<InnerTubePlaylist> = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext emptyList()

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("browseId", "FEmusic_liked_playlists")
        }

        try {
            val request = buildBaseRequest("browse", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                val playlists = mutableListOf<InnerTubePlaylist>()

                val sectionList = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return@withContext emptyList()

                for (i in 0 until sectionList.length()) {
                    val grid = sectionList.optJSONObject(i)?.optJSONObject("gridRenderer") ?: continue
                    val items = grid.optJSONArray("items") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                        val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                        val subtitle = item.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        val browseId = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                        val thumbList = item.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val artwork = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")

                        if (browseId.isNotBlank()) {
                            playlists.add(
                                InnerTubePlaylist(
                                    id = browseId,
                                    title = title,
                                    subtitle = subtitle,
                                    artworkUrl = getHighResArtworkUrl(artwork)
                                )
                            )
                        }
                    }
                }
                return@withContext playlists
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserPlaylists failed", e)
            return@withContext emptyList()
        }
    }

    suspend fun getLikedSongs(): List<InnerTubeTrack> = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext emptyList()
        return@withContext getPlaylistTracks("LM")
    }

    suspend fun setTrackLiked(videoId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || videoId.isBlank()) return@withContext false

        val endpoint = if (isLiked) "like/like" else "like/removelike"
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("target", JSONObject().apply {
                put("videoId", videoId)
            })
        }

        try {
            val request = buildBaseRequest(endpoint, payload)
            httpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "setTrackLiked failed for videoId: $videoId", e)
            return@withContext false
        }
    }

    suspend fun addTrackToPlaylist(playlistId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || playlistId.isBlank() || videoId.isBlank()) return@withContext false

        val cleanPlaylistId = playlistId.removePrefix("VL")
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("playlistId", cleanPlaylistId)
            put("actions", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("addedVideoId", videoId)
                    put("action", "ACTION_ADD_VIDEO")
                })
            })
        }

        try {
            val request = buildBaseRequest("browse/edit_playlist", payload)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "addTrackToPlaylist response code: ${response.code}, body: ${body.take(200)}")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "addTrackToPlaylist failed for playlist: $playlistId, videoId: $videoId", e)
            return@withContext false
        }
    }

    suspend fun createPlaylist(title: String, description: String = ""): String? = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || title.isBlank()) return@withContext null

        val payload = JSONObject().apply {
            put("context", buildContext())
            put("title", title)
            put("description", description)
            put("privacyStatus", "PRIVATE")
        }

        try {
            val request = buildBaseRequest("playlist/create", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val playlistId = json.optString("playlistId")
                return@withContext if (playlistId.isNotBlank()) playlistId else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "createPlaylist failed for title: $title", e)
            return@withContext null
        }
    }

    suspend fun editPlaylistDetails(playlistId: String, newName: String, newDescription: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || playlistId.isBlank() || newName.isBlank()) return@withContext false
        val cleanPlaylistId = playlistId.removePrefix("VL")
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("playlistId", cleanPlaylistId)
            put("actions", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("action", "ACTION_SET_PLAYLIST_NAME")
                    put("playlistName", newName)
                })
                put(JSONObject().apply {
                    put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    put("playlistDescription", newDescription)
                })
            })
        }
        try {
            val request = buildBaseRequest("browse/edit_playlist", payload)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "editPlaylistDetails response code: ${response.code}, body: ${body.take(200)}")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "editPlaylistDetails failed for playlist: $playlistId", e)
            return@withContext false
        }
    }

    suspend fun renamePlaylist(playlistId: String, newName: String): Boolean =
        editPlaylistDetails(playlistId, newName, "")

    suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || playlistId.isBlank()) return@withContext false
        val cleanPlaylistId = playlistId.removePrefix("VL")
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("playlistId", cleanPlaylistId)
        }
        try {
            val request = buildBaseRequest("playlist/delete", payload)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "deletePlaylist response code: ${response.code}, body: ${body.take(200)}")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "deletePlaylist failed for playlist: $playlistId", e)
            return@withContext false
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn() || playlistId.isBlank() || videoId.isBlank()) return@withContext false
        val cleanPlaylistId = playlistId.removePrefix("VL")
        val payload = JSONObject().apply {
            put("context", buildContext())
            put("playlistId", cleanPlaylistId)
            put("actions", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                    put("removedVideoId", videoId)
                })
            })
        }
        try {
            val request = buildBaseRequest("browse/edit_playlist", payload)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "removeTrackFromPlaylist response code: ${response.code}, body: ${body.take(200)}")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeTrackFromPlaylist failed for playlist: $playlistId, videoId: $videoId", e)
            return@withContext false
        }
    }


    // ==========================================
    // 7. ACCOUNT PROFILE (/account/account_menu)
    // ==========================================
    suspend fun getAccountInfo(): InnerTubeAccountInfo? = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext null
        val payload = JSONObject().apply {
            put("context", buildContext())
        }
        try {
            val request = buildBaseRequest("account/account_menu", payload)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "getAccountInfo returned error: ${response.code}")
                    return@withContext null
                }
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val actions = json.optJSONArray("actions") ?: return@withContext null
                val header = actions.optJSONObject(0)
                    ?.optJSONObject("openPopupAction")
                    ?.optJSONObject("popup")
                    ?.optJSONObject("multiPageMenuRenderer")
                    ?.optJSONObject("header")
                    ?.optJSONObject("activeAccountHeaderRenderer") ?: return@withContext null

                val name = header.optJSONObject("accountName")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: "YouTube Music User"
                val handle = header.optJSONObject("channelHandle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: header.optJSONObject("email")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                val thumbs = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
                val avatar = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")

                Log.d(TAG, "Fetched account info: name='$name', handle='$handle'")
                return@withContext InnerTubeAccountInfo(name, handle, avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAccountInfo failed", e)
            return@withContext null
        }
    }

    // ==========================================
    // UTILS
    // ==========================================
    private fun parseDurationToSeconds(text: String): Int {
        if (text.isBlank() || !text.contains(":")) return 0
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun getHighResArtworkUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (url.contains("=w") || url.contains("=s")) {
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w1200-h1200-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1200")
        } else if (url.contains("/vi/") && url.contains("/hqdefault.jpg")) {
            url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
        } else {
            url
        }
    }
}
