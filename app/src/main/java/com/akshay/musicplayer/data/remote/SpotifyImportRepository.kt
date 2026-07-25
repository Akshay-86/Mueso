package com.akshay.musicplayer.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyTrackInfo(
    val title: String,
    val artist: String
)

data class SpotifyPlaylistData(
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val tracks: List<SpotifyTrackInfo>
)

class SpotifyImportRepository {

    /**
     * In-memory cookie jar so OkHttp stores/sends session cookies automatically.
     */
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Replace existing cookies with same name
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "MUESO_SPOTIFY"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("spotify:playlist:")) {
            return trimmed.removePrefix("spotify:playlist:").takeIf { it.isNotBlank() }
        }
        val regex = Regex("""open\.spotify\.com/playlist/([a-zA-Z0-9]+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    /**
     * Step 1: Visit the Spotify homepage to establish session cookies.
     * Step 2: Use those cookies to fetch an anonymous access token.
     */
    private suspend fun fetchAnonymousToken(): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Establish session by visiting the Spotify home page
            Log.d(TAG, "[TOKEN] Step 1: Visiting open.spotify.com to establish session cookies...")
            val homeRequest = Request.Builder()
                .url("https://open.spotify.com/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .get()
                .build()

            val homeResponse = httpClient.newCall(homeRequest).execute()
            homeResponse.body?.close() // We don't need the body, just cookies
            Log.d(TAG, "[TOKEN] Step 1 done: HTTP ${homeResponse.code}, cookies: ${cookieStore.values.flatten().map { it.name }}")

            // Step 2: Now request the anonymous token with established cookies
            Log.d(TAG, "[TOKEN] Step 2: Fetching anonymous access token...")
            val tokenRequest = Request.Builder()
                .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://open.spotify.com/")
                .get()
                .build()

            val tokenResponse = httpClient.newCall(tokenRequest).execute()
            val tokenBody = tokenResponse.body?.string()

            if (!tokenResponse.isSuccessful || tokenBody.isNullOrBlank()) {
                Log.e(TAG, "[TOKEN] Step 2 failed: HTTP ${tokenResponse.code}, body: ${tokenBody?.take(200)}")
                return@withContext null
            }

            val json = JSONObject(tokenBody)
            val token = json.optString("accessToken", "").takeIf { it.isNotBlank() }
            if (token != null) {
                Log.d(TAG, "[TOKEN] Successfully obtained anonymous token (${token.take(20)}...)")
            } else {
                Log.e(TAG, "[TOKEN] Response missing accessToken: ${tokenBody.take(300)}")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "[TOKEN] Exception fetching anonymous token", e)
            null
        }
    }

    /**
     * Primary method: use anonymous token + Spotify Web API.
     */
    private suspend fun fetchViaApi(playlistId: String, token: String): SpotifyPlaylistData? = withContext(Dispatchers.IO) {
        try {
            // Fetch playlist metadata
            val metaRequest = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId?fields=name,description,images")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            val metaResponse = httpClient.newCall(metaRequest).execute()
            val metaBody = metaResponse.body?.string()

            if (!metaResponse.isSuccessful || metaBody.isNullOrBlank()) {
                Log.e(TAG, "[API] Metadata fetch failed: HTTP ${metaResponse.code}")
                return@withContext null
            }

            val metaJson = JSONObject(metaBody)
            val playlistName = metaJson.optString("name", "Imported Playlist")
            val playlistDesc = metaJson.optString("description", "").takeIf { it.isNotBlank() }
            val images = metaJson.optJSONArray("images")
            val artworkUrl = images?.optJSONObject(0)?.optString("url")

            Log.d(TAG, "[API] Playlist: \"$playlistName\"")

            // Fetch all tracks (paginated)
            val allTracks = mutableListOf<SpotifyTrackInfo>()
            var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?fields=items(track(name,artists(name))),next&limit=100"

            while (nextUrl != null) {
                val tracksRequest = Request.Builder()
                    .url(nextUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val tracksResponse = httpClient.newCall(tracksRequest).execute()
                val tracksBody = tracksResponse.body?.string()

                if (!tracksResponse.isSuccessful || tracksBody.isNullOrBlank()) break

                val tracksJson = JSONObject(tracksBody)
                val items = tracksJson.optJSONArray("items") ?: break

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val track = item.optJSONObject("track") ?: continue
                    val name = track.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                    val artists = track.optJSONArray("artists")
                    val artistName = if (artists != null && artists.length() > 0) {
                        (0 until artists.length()).mapNotNull { idx ->
                            artists.optJSONObject(idx)?.optString("name")
                        }.joinToString(", ")
                    } else "Unknown Artist"
                    allTracks.add(SpotifyTrackInfo(title = name, artist = artistName))
                }

                nextUrl = tracksJson.optString("next", "null").takeIf { it != "null" && it.isNotBlank() }
                Log.d(TAG, "[API] ${allTracks.size} tracks fetched so far")
            }

            if (allTracks.isEmpty()) return@withContext null

            SpotifyPlaylistData(name = playlistName, description = playlistDesc, artworkUrl = artworkUrl, tracks = allTracks)
        } catch (e: Exception) {
            Log.e(TAG, "[API] Exception", e)
            null
        }
    }

    /**
     * Fallback: scrape the Spotify embed page HTML which contains track data.
     */
    private suspend fun fetchViaEmbed(playlistId: String): SpotifyPlaylistData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[EMBED] Fetching embed page for playlist $playlistId...")
            val embedRequest = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            val embedResponse = httpClient.newCall(embedRequest).execute()
            val html = embedResponse.body?.string()

            if (!embedResponse.isSuccessful || html.isNullOrBlank()) {
                Log.e(TAG, "[EMBED] Failed: HTTP ${embedResponse.code}")
                return@withContext null
            }

            Log.d(TAG, "[EMBED] Got HTML (${html.length} chars), parsing...")

            // Try to find __NEXT_DATA__ JSON block
            val nextDataRegex = Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json">\s*(\{.*?\})\s*</script>""", RegexOption.DOT_MATCHES_ALL)
            val nextDataMatch = nextDataRegex.find(html)

            if (nextDataMatch != null) {
                val jsonStr = nextDataMatch.groupValues[1]
                Log.d(TAG, "[EMBED] Found __NEXT_DATA__ (${jsonStr.length} chars)")
                return@withContext parseNextData(jsonStr)
            }

            // Alternative: look for embedded JSON state in a script tag
            val stateRegex = Regex("""<script[^>]*>\s*window\.__SPOTIFY_STATE__\s*=\s*(\{.*?\});\s*</script>""", RegexOption.DOT_MATCHES_ALL)
            val stateMatch = stateRegex.find(html)
            if (stateMatch != null) {
                Log.d(TAG, "[EMBED] Found __SPOTIFY_STATE__")
                return@withContext parseNextData(stateMatch.groupValues[1])
            }

            // Final fallback: extract track info from HTML meta tags / structured data
            Log.d(TAG, "[EMBED] No JSON data found in embed page, trying meta tag extraction...")
            return@withContext parseEmbedHtmlMeta(html, playlistId)

        } catch (e: Exception) {
            Log.e(TAG, "[EMBED] Exception", e)
            null
        }
    }

    private fun parseNextData(jsonStr: String): SpotifyPlaylistData? {
        return try {
            val root = JSONObject(jsonStr)
            Log.d(TAG, "[EMBED_JSON] Parsing __NEXT_DATA__ JSON (${jsonStr.length} chars)")
            
            val props = root.optJSONObject("props")
            val pageProps = props?.optJSONObject("pageProps")

            var name: String? = null
            var description: String? = null
            var artworkUrl: String? = null

            // Try explicit entity locations
            val entityObj = pageProps?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                ?: pageProps?.optJSONObject("entity")
                ?: pageProps?.optJSONObject("playlist")
                ?: pageProps?.optJSONObject("data")?.optJSONObject("playlist")
                ?: pageProps?.optJSONObject("initialState")?.optJSONObject("playlist")

            if (entityObj != null) {
                name = entityObj.optString("name", "").takeIf { it.isNotBlank() }
                    ?: entityObj.optString("title", "").takeIf { it.isNotBlank() }
                description = entityObj.optString("subtitle", "")
                    .ifBlank { entityObj.optString("description", "") }
                    .takeIf { it.isNotBlank() }
                artworkUrl = entityObj.optString("coverArt", "")
                    .ifBlank { entityObj.optString("images", "") }
                    .ifBlank { entityObj.optJSONArray("images")?.optJSONObject(0)?.optString("url", "") ?: "" }
                    .takeIf { it.isNotBlank() }
            }

            val tracks = mutableListOf<SpotifyTrackInfo>()

            // Try explicit track lists
            val trackListItems = entityObj?.optJSONObject("trackList")?.optJSONArray("items")
                ?: entityObj?.optJSONArray("tracks")
                ?: entityObj?.optJSONObject("tracks")?.optJSONArray("items")
                ?: pageProps?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("trackList")?.optJSONArray("items")

            if (trackListItems != null && trackListItems.length() > 0) {
                for (i in 0 until trackListItems.length()) {
                    val item = trackListItems.optJSONObject(i) ?: continue
                    val tName = item.optString("title", "")
                        .ifBlank { item.optString("name", "") }
                        .ifBlank { item.optJSONObject("track")?.optString("name", "") ?: "" }
                        .takeIf { it.isNotBlank() } ?: continue

                    val tArtist = item.optString("subtitle", "")
                        .ifBlank { item.optString("artist", "") }
                        .ifBlank {
                            val artistsArr = item.optJSONArray("artists") ?: item.optJSONObject("track")?.optJSONArray("artists")
                            if (artistsArr != null && artistsArr.length() > 0) {
                                (0 until artistsArr.length()).mapNotNull { idx ->
                                    artistsArr.optJSONObject(idx)?.optString("name")
                                }.joinToString(", ")
                            } else ""
                        }
                        .ifBlank { "Unknown Artist" }

                    tracks.add(SpotifyTrackInfo(title = tName, artist = tArtist))
                }
            }

            // Deep JSON search if explicit paths produced no tracks
            if (tracks.isEmpty()) {
                Log.d(TAG, "[EMBED_JSON] Explicit paths empty. Running deep JSON tree search...")
                findTracksDeep(root, tracks)
            }

            if (name.isNullOrBlank()) {
                name = pageProps?.optString("name", "").takeIf { it!!.isNotBlank() }
                    ?: "Imported Playlist"
            }

            // Remove any track item that matches the playlist name itself or generic site titles
            val finalPlaylistName = name
            tracks.removeAll {
                it.title.equals(finalPlaylistName, ignoreCase = true) ||
                it.title.equals("Spotify", ignoreCase = true) ||
                it.title.equals("Imported Playlist", ignoreCase = true) ||
                it.title.startsWith("http", ignoreCase = true)
            }

            if (tracks.isEmpty()) {
                Log.e(TAG, "[EMBED_JSON] Deep JSON search found 0 valid tracks.")
                return null
            }

            Log.d(TAG, "[EMBED_JSON] SUCCESS: Extracted playlist \"$name\" with ${tracks.size} tracks")
            SpotifyPlaylistData(name = name, description = description, artworkUrl = artworkUrl, tracks = tracks)
        } catch (e: Exception) {
            Log.e(TAG, "[EMBED_JSON] Error parsing JSON", e)
            null
        }
    }

    private fun findTracksDeep(obj: Any?, outTracks: MutableList<SpotifyTrackInfo>, depth: Int = 0) {
        if (depth > 12 || obj == null) return

        if (obj is JSONObject) {
            val title = obj.optString("title", "").ifBlank { obj.optString("name", "") }.trim()
            val subtitle = obj.optString("subtitle", "").ifBlank { obj.optString("artist", "") }.trim()
            val uri = obj.optString("uri", "")

            val isTrackUri = uri.contains("spotify:track:") || obj.has("duration_ms") || obj.has("duration") || obj.has("trackList")

            if (title.isNotBlank() && (subtitle.isNotBlank() || isTrackUri)) {
                var artistName = subtitle
                if (artistName.isBlank()) {
                    val artistsArr = obj.optJSONArray("artists")
                    if (artistsArr != null && artistsArr.length() > 0) {
                        artistName = (0 until artistsArr.length()).mapNotNull { idx ->
                            artistsArr.optJSONObject(idx)?.optString("name")
                        }.joinToString(", ")
                    }
                }
                if (artistName.isBlank()) artistName = "Unknown Artist"

                // Filter out non-track strings like "Spotify" or URL strings
                if (title != "Spotify" && title != "Imported Playlist" && !title.startsWith("http") && !title.contains("open.spotify") && !title.contains("playlist", ignoreCase = true)) {
                    val candidate = SpotifyTrackInfo(title = title, artist = artistName)
                    if (!outTracks.any { it.title.equals(title, ignoreCase = true) && it.artist.equals(artistName, ignoreCase = true) }) {
                        outTracks.add(candidate)
                    }
                }
            }

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                findTracksDeep(obj.opt(key), outTracks, depth + 1)
            }
        } else if (obj is JSONArray) {
            for (i in 0 until obj.length()) {
                findTracksDeep(obj.opt(i), outTracks, depth + 1)
            }
        }
    }

    private fun parseEmbedHtmlMeta(html: String, playlistId: String): SpotifyPlaylistData? {
        val titleRegex = Regex("""<title>(.*?)</title>""")
        val title = titleRegex.find(html)?.groupValues?.get(1)?.replace(" | Spotify", "")?.replace(" - playlist by", "")?.trim() ?: "Imported Playlist"

        // Match song title / artist patterns from HTML tags
        val trackRegex = Regex("""(?i)"title"\s*:\s*"([^"]+)"\s*,\s*"subtitle"\s*:\s*"([^"]+)"""")
        val matches = trackRegex.findAll(html).toList()

        val tracks = matches.mapNotNull { m ->
            val trackName = m.groupValues[1].trim()
            val artistName = m.groupValues[2].trim()
            if (trackName.isNotBlank() && trackName != "Spotify") {
                SpotifyTrackInfo(title = trackName, artist = artistName.ifBlank { "Unknown Artist" })
            } else null
        }.distinctBy { "${it.title}:${it.artist}".lowercase() }

        if (tracks.isNotEmpty()) {
            Log.d(TAG, "[EMBED_META] Extracted ${tracks.size} tracks via regex")
            return SpotifyPlaylistData(name = title, description = null, artworkUrl = null, tracks = tracks)
        }

        Log.e(TAG, "[EMBED_META] Could not extract tracks from HTML meta tags")
        return null
    }

    /**
     * Main entry point: try API method first, fall back to embed scraping & oEmbed.
     */
    suspend fun fetchPlaylist(url: String): Result<SpotifyPlaylistData> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
        if (playlistId == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL. Please paste a link like:\nhttps://open.spotify.com/playlist/..."))
        }

        Log.d(TAG, "[FETCH] Starting playlist fetch for ID: $playlistId")
        cookieStore.clear()

        // Method 1: Anonymous token + Web API
        val token = fetchAnonymousToken()
        if (token != null) {
            Log.d(TAG, "[FETCH] Got token, trying Web API...")
            val apiResult = fetchViaApi(playlistId, token)
            if (apiResult != null && apiResult.tracks.isNotEmpty()) {
                Log.d(TAG, "[FETCH] API method succeeded: ${apiResult.tracks.size} tracks")
                return@withContext Result.success(apiResult)
            }
            Log.w(TAG, "[FETCH] API method returned no results, trying embed fallback...")
        } else {
            Log.w(TAG, "[FETCH] Could not get anonymous token, trying embed fallback...")
        }

        // Method 2: Embed page JSON scraping
        val embedResult = fetchViaEmbed(playlistId)
        if (embedResult != null && embedResult.tracks.isNotEmpty()) {
            Log.d(TAG, "[FETCH] Embed method succeeded: ${embedResult.tracks.size} tracks")
            return@withContext Result.success(embedResult)
        }

        Log.e(TAG, "[FETCH] All methods failed for playlist $playlistId")
        Result.failure(RuntimeException("Could not fetch playlist. Please make sure:\n• The playlist is set to Public\n• The link is correct\n• You have internet connection"))
    }
}
