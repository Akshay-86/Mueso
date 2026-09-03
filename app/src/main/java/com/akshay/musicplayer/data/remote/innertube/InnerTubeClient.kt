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
            if (subRuns != null) {
                val candidateTexts = mutableListOf<String>()
                for (k in 0 until subRuns.length()) {
                    val run = subRuns.optJSONObject(k) ?: continue
                    val text = run.optString("text", "").trim()
                    if (text.isBlank() || text == "•" || text == "|") continue
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
                        artworkUrl = getHighResArtworkUrl(thumbUrl)
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
        return tracks
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
            artworkUrl = getHighResArtworkUrl(artworkUrl)
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
            val section = sectionList.optJSONObject(i)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val shelfTitle = section.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Featured"
            val shelfSubtitle = section.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

            val items = section.optJSONArray("contents") ?: continue
            val playlistsInShelf = mutableListOf<InnerTubePlaylist>()

            for (j in 0 until items.length()) {
                val itemObj = items.optJSONObject(j) ?: continue
                val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
                if (twoRow != null) {
                    val title = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                    val subtitle = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                    val navEndpoint = twoRow.optJSONObject("navigationEndpoint")
                    val browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                    val watchEndpoint = navEndpoint?.optJSONObject("watchEndpoint")
                    val playlistId = watchEndpoint?.optString("playlistId")
                    val videoId = watchEndpoint?.optString("videoId")
                    val targetId = when {
                        !browseId.isNullOrBlank() -> browseId
                        !playlistId.isNullOrBlank() -> playlistId
                        !videoId.isNullOrBlank() -> "online:$videoId"
                        else -> ""
                    }

                    val thumbList = twoRow.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val artworkUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url") ?: ""

                    if (targetId.isNotBlank()) {
                        playlistsInShelf.add(
                            InnerTubePlaylist(
                                id = targetId,
                                title = title,
                                subtitle = subtitle,
                                artworkUrl = getHighResArtworkUrl(artworkUrl)
                            )
                        )
                    }
                } else {
                    val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer")
                    if (responsive != null) {
                        val track = parseResponsiveListItem(responsive)
                        if (track != null) {
                            playlistsInShelf.add(
                                InnerTubePlaylist(
                                    id = "online:${track.videoId}",
                                    title = track.title,
                                    subtitle = track.artist,
                                    artworkUrl = track.artworkUrl ?: ""
                                )
                            )
                        }
                    }
                }
            }
            if (playlistsInShelf.isNotEmpty()) {
                shelves.add(InnerTubeShelf(shelfTitle, shelfSubtitle, playlists = playlistsInShelf))
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
