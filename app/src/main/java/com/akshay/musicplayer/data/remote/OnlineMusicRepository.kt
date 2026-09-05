package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.data.remote.innertube.InnerTubeClient
import com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf
import com.akshay.musicplayer.domain.models.LrcParser
import com.akshay.musicplayer.domain.models.LrclibSearchResultItem
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import com.akshay.musicplayer.data.remote.stream.YouTubeStreamResolver
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork

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

    companion object {
        private const val TAG = "MUESO_ONLINE_REPO"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val innerTube = InnerTubeClient(httpClient)
    val streamResolver = YouTubeStreamResolver(httpClient)

    fun setAuthCookie(cookie: String?) {
        innerTube.setAuthCookie(cookie)
        streamResolver.setAuthCookie(cookie)
    }

    // ==========================================
    // 1. DYNAMIC & CURATED PLAYLISTS
    // ==========================================
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
                searchQuery = "90s retro hindi english classic hits"
            ),
            CuratedOnlinePlaylist(
                id = "curated_peaceful_sleep",
                title = "Peaceful Sleep",
                subtitle = "Gentle ambient soundscapes to help you fall asleep",
                category = "Mood & Focus",
                gradientColors = listOf(0xFF2C3E50, 0xFF3498DB),
                searchQuery = "peaceful sleep ambient rain music"
            )
        )
    }

    suspend fun fetchExploreShelves(): List<InnerTubeShelf> {
        return innerTube.getExploreShelves()
    }

    suspend fun fetchChartsShelves(): List<InnerTubeShelf> {
        return innerTube.getChartsShelves()
    }

    suspend fun fetchMoodShelves(mood: String): List<InnerTubeShelf> {
        return innerTube.getShelvesForMood(mood)
    }

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> = withContext(Dispatchers.IO) {
        val charts = innerTube.getChartsShelves()
        for (shelf in charts) {
            if (shelf.tracks.isNotEmpty()) {
                return@withContext shelf.tracks.map { it.toTrackEntity() }
            }
            for (playlist in shelf.playlists) {
                val tracks = innerTube.getPlaylistTracks(playlist.id)
                if (tracks.isNotEmpty()) {
                    return@withContext tracks.map { it.toTrackEntity() }
                }
            }
        }
        return@withContext searchOnlineTracks("trending music $country")
    }

    suspend fun fetchCuratedPlaylistTracks(query: String, language: String = ""): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (query.startsWith("browse:")) {
            val browseId = query.removePrefix("browse:")
            if (browseId.startsWith("online:")) {
                val videoId = browseId.removePrefix("online:")
                val radio = innerTube.getRadioTracks(videoId)
                if (radio.isNotEmpty()) {
                    return@withContext radio.map { it.toTrackEntity() }
                }
            }
            val tracks = innerTube.getPlaylistTracks(browseId)
            if (tracks.isNotEmpty()) {
                Log.d(TAG, "Successfully fetched ${tracks.size} real tracks for playlist '$browseId' via InnerTube")
                return@withContext tracks.map { it.toTrackEntity() }
            }
        }

        val cleanQuery = if (language.isNotBlank() && !query.lowercase().contains(language.lowercase())) {
            "$query $language"
        } else {
            query
        }

        return@withContext searchOnlineTracks(cleanQuery)
    }

    suspend fun fetchRealTop50GlobalCharts(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val charts = innerTube.getChartsShelves()
        for (shelf in charts) {
            if (shelf.tracks.isNotEmpty()) {
                return@withContext shelf.tracks.map { it.toTrackEntity() }
            }
            for (playlist in shelf.playlists) {
                val tracks = innerTube.getPlaylistTracks(playlist.id)
                if (tracks.isNotEmpty()) {
                    return@withContext tracks.map { it.toTrackEntity() }
                }
            }
        }
        return@withContext searchOnlineTracks("top 50 global songs")
    }

    fun getPlaylistDescription(playlistId: String): String? = innerTube.getCachedPlaylistDescription(playlistId)
    suspend fun fetchPlaylistDescription(playlistId: String): String? = innerTube.fetchPlaylistDescription(playlistId)

    // ==========================================
    // 2. SEARCH & STREAM RESOLUTION
    // ==========================================
    suspend fun searchOnlineTracks(query: String, category: String = "All"): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val filterParam = when (category.trim().lowercase()) {
            "songs", "song" -> "EgWKAQIIAWoSEAQQCRADEAUQEBAKEBUQERAO"
            "videos", "video" -> "EgWKAQIQAWoSEAQQCRADEAUQEBAKEBUQERAO"
            "albums", "album" -> "EgWKAQIYAWoSEAQQCRADEAUQEBAKEBUQERAO"
            "playlists", "playlist" -> "EgeKAQQoAEABahIQBBAJEAMQBRAQEAoQFRAREA4%3D"
            "artists", "artist" -> "EgWKAQIgAWoSEAQQCRADEAUQEBAKEBUQERAO"
            else -> null
        }
        val results = innerTube.search(query, filter = filterParam)
        if (results.isNotEmpty()) {
            Log.d(TAG, "Search for '$query' (category: $category) returned ${results.size} tracks via InnerTube")
            return@withContext results.map { it.toTrackEntity() }
        }
        return@withContext emptyList()
    }

    suspend fun searchArtists(query: String): List<com.akshay.musicplayer.data.remote.innertube.InnerTubeArtist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        return@withContext innerTube.searchArtists(query)
    }

    suspend fun searchPlaylists(query: String, filter: String? = null): List<com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        return@withContext innerTube.searchPlaylists(query, filter)
    }

    suspend fun fetchArtistPage(browseId: String): com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null
        return@withContext innerTube.getArtistPage(browseId)
    }


    suspend fun getStreamUrl(
        videoId: String,
        context: android.content.Context? = null,
        forceRefresh: Boolean = false,
        audioQuality: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext ""
        try {
            val effectiveQuality = audioQuality ?: context?.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)?.getString("audio_quality", "High (320 kbps)")

            if (forceRefresh) {
                com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.invalidateCache(videoId)
                streamResolver.invalidateCache(videoId)
            } else {
                // 1. Check in-memory cache from active playback or previous extraction
                val cached = com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.getCachedStreamUrl(videoId)
                if (!cached.isNullOrBlank()) {
                    Log.d(TAG, "Resolved audio stream from cache for videoId=$videoId")
                    return@withContext cached
                }
            }

            // 2. If context provided, try headless web extraction
            if (context != null) {
                val extracted = com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.extractAudioStream(context, videoId)
                if (!extracted.isNullOrBlank()) {
                    Log.d(TAG, "Resolved audio stream via Headless WebView for videoId=$videoId")
                    return@withContext extracted
                }
            }

            // 3. Fallback to streamResolver
            val resolved = streamResolver.resolveAudioStream(videoId, effectiveQuality)
            if (!resolved.isNullOrBlank()) {
                Log.d(TAG, "Resolved audio stream for videoId=$videoId (quality=$effectiveQuality) via StreamResolver (length=${resolved.length})")
                return@withContext resolved
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream resolution error for $videoId", e)
        }
        return@withContext ""
    }

    fun extractVideoId(track: TrackEntity): String {
        return if (track.filePath.startsWith("online:")) {
            track.filePath.removePrefix("online:")
        } else if (!track.artworkUrl.isNullOrBlank() && track.artworkUrl.contains("/vi/")) {
            track.artworkUrl.substringAfter("/vi/").substringBefore("/")
        } else {
            ""
        }
    }

    suspend fun embedMetadata(
        filePath: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        lyricsText: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return@withContext false

        var (cleanedTitle, cleanedArtist) = cleanTitleAndArtist(title, artist)
        if (cleanedArtist.isBlank() && cleanedTitle.contains(" - ")) {
            val parts = cleanedTitle.split(" - ", limit = 2)
            cleanedArtist = parts[0].trim()
            cleanedTitle = parts[1].trim()
        }
        val finalArtist = if (cleanedArtist.isNotBlank() && !cleanedArtist.equals("Unknown Artist", ignoreCase = true)) cleanedArtist else artist.ifBlank { "Unknown Artist" }
        val finalTitle = cleanedTitle.ifBlank { title }
        val finalAlbum = if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true)) album else finalTitle

        // 1. Download and decode artwork JPEG bytes
        var jpegBytes: ByteArray? = null
        if (!artworkUrl.isNullOrBlank()) {
            val candidates = getYouTubeArtworkFallbackList(artworkUrl)
            for (candUrl in candidates) {
                if (jpegBytes != null) break
                try {
                    val req = Request.Builder()
                        .url(candUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bytes = resp.body?.bytes()
                            if (bytes != null && bytes.size > 500) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    val stream = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                                    jpegBytes = stream.toByteArray()
                                    bitmap.recycle()
                                    Log.d(TAG, "Downloaded artwork (${jpegBytes?.size} bytes) from $candUrl")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download artwork candidate '$candUrl': ${e.message}")
                }
            }
        }

        val finalJpegBytes = jpegBytes
        // 2. If it's an MP4 / M4A file, use our native Mp4MetadataEditor
        if (file.name.endsWith(".m4a", ignoreCase = true) || file.name.endsWith(".mp4", ignoreCase = true)) {
            val success = Mp4MetadataEditor.embedMetadata(
                file = file,
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                albumArtist = finalArtist,
                lyrics = lyricsText,
                jpegBytes = finalJpegBytes
            )
            if (success) {
                Log.d(TAG, "Successfully embedded MP4 metadata (lyrics=${!lyricsText.isNullOrBlank()}) in $filePath for '$finalTitle' by '$finalArtist'")
                return@withContext true
            }
        }

        // 3. Fallback to Jaudiotagger (for MP3 / other formats)
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            if (finalTitle.isNotBlank()) {
                tag.setField(FieldKey.TITLE, finalTitle)
            }
            if (finalArtist.isNotBlank() && !finalArtist.equals("Unknown Artist", ignoreCase = true)) {
                tag.setField(FieldKey.ARTIST, finalArtist)
                tag.setField(FieldKey.ALBUM_ARTIST, finalArtist)
            }
            if (finalAlbum.isNotBlank()) {
                tag.setField(FieldKey.ALBUM, finalAlbum)
            }

            if (!lyricsText.isNullOrBlank()) {
                try {
                    tag.setField(FieldKey.LYRICS, lyricsText)
                } catch (_: Exception) {}
            }

            if (finalJpegBytes != null && finalJpegBytes.isNotEmpty()) {
                val artwork = AndroidArtwork()
                artwork.binaryData = finalJpegBytes
                artwork.mimeType = "image/jpeg"
                artwork.pictureType = 3 // Cover (front)
                try { tag.deleteArtworkField() } catch (_: Exception) {}
                tag.setField(artwork)
            }

            audioFile.commit()
            Log.d(TAG, "Successfully embedded metadata in $filePath for '$finalTitle' by '$finalArtist' (album: '$finalAlbum')")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error embedding metadata in $filePath: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // 3. RADIO / RECOMMENDATIONS (/next)
    // ==========================================
    suspend fun getRelatedRecommendations(track: TrackEntity): List<TrackEntity> = withContext(Dispatchers.IO) {
        var videoId = extractVideoId(track)
        Log.d("MUESO_RADIO", "getRelatedRecommendations called for title='${track.title}', videoId='$videoId'")

        if (videoId.isNotBlank()) {
            val radioTracks = innerTube.getRadioTracks(videoId)
            if (radioTracks.isNotEmpty()) {
                val noiseRegex = Regex("(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\]|\\(audio\\)|\\[audio\\])", RegexOption.IGNORE_CASE)
                val cleanCurrentTitle = noiseRegex.replace(track.title, "").lowercase().replace(Regex("[^a-z0-9]"), "")

                val filtered = radioTracks.map { it.toTrackEntity() }.filter { rec ->
                    val cleanRecTitle = noiseRegex.replace(rec.title, "").lowercase().replace(Regex("[^a-z0-9]"), "")
                    rec.id != track.id && cleanRecTitle != cleanCurrentTitle
                }
                if (filtered.isNotEmpty()) {
                    Log.d("MUESO_RADIO", "Successfully fetched ${filtered.size} authentic radio recommendations from InnerTube")
                    return@withContext filtered
                }
            }
        }

        // Fallback search: search for artist's other songs or title + artist
        val query = if (track.artist != "Unknown Artist" && track.artist.isNotBlank()) {
            "${track.artist} songs"
        } else {
            "${track.title} song"
        }
        Log.d("MUESO_RADIO", "Fallback search query: '$query'")
        val searchResults = searchOnlineTracks(query)
        return@withContext searchResults.filter { it.id != track.id }
    }

    // ==========================================
    // 4. SPONSORBLOCK INTEGRATION
    // ==========================================
    suspend fun getSponsorSkipSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mueso-Android-App/1.2")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404 || !response.isSuccessful) return@withContext emptyList()
                val jsonStr = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(jsonStr)
                val segments = mutableListOf<SponsorSegment>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val segArr = obj.optJSONArray("segment") ?: continue
                    val category = obj.optString("category", "sponsor")
                    if (segArr.length() >= 2) {
                        val startSec = segArr.optDouble(0, 0.0)
                        val endSec = segArr.optDouble(1, 0.0)
                        if (endSec > startSec) {
                            segments.add(
                                SponsorSegment(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    category = category
                                )
                            )
                        }
                    }
                }
                return@withContext segments
            }
        } catch (e: Exception) {
            Log.w(TAG, "SponsorBlock query failed for $videoId", e)
            return@withContext emptyList()
        }
    }

    // ==========================================
    // 5. LYRICS (LRCLIB)
    // ==========================================
    suspend fun fetchLyrics(title: String, artist: String, language: String = ""): LyricsData? = withContext(Dispatchers.IO) {
        val (cleanedTitle, cleanedArtist) = cleanTitleAndArtist(title, artist)
        var result: LyricsData? = null

        if (cleanedArtist.isNotBlank()) {
            result = tryLrclibGet(cleanedTitle, cleanedArtist)
            if (result != null) return@withContext result

            val query = "$cleanedTitle $cleanedArtist".trim()
            result = tryLrclibSearch(cleanedTitle, query)
            if (result != null) return@withContext result
        }

        // Fallback: search LRCLIB with cleaned title directly
        result = tryLrclibSearch(cleanedTitle, cleanedTitle)
        if (result != null) return@withContext result

        if (cleanedTitle != title) {
            result = tryLrclibSearch(title, "$title $artist".trim())
            if (result != null) return@withContext result
        }

        return@withContext null
    }

    private fun tryLrclibGet(trackName: String, artistName: String): LyricsData? {
        val encodedTitle = URLEncoder.encode(trackName, "UTF-8")
        val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
        val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
        return tryFetchFromUrl(url)
    }

    private fun JSONObject.optCleanString(name: String): String? {
        if (isNull(name)) return null
        val str = optString(name, "").trim()
        return str.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun tryFetchFromUrl(url: String): LyricsData? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mueso-Android-App/1.2")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val syncedLyrics = json.optCleanString("syncedLyrics")
                val plainLyrics = json.optCleanString("plainLyrics")
                if (!syncedLyrics.isNullOrBlank()) {
                    LyricsData(lines = LrcParser.parse(syncedLyrics), rawText = syncedLyrics)
                } else if (!plainLyrics.isNullOrBlank()) {
                    LyricsData(lines = LrcParser.parse(plainLyrics), rawText = plainLyrics)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryLrclibSearch(targetTitle: String, query: String): LyricsData? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mueso-Android-App/1.2")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val array = JSONArray(body)
                if (array.length() == 0) return null

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val trackName = item.optString("trackName", "")
                    val syncedLyrics = item.optCleanString("syncedLyrics")
                    if (!syncedLyrics.isNullOrBlank() && matchesTrackTitle(trackName, targetTitle)) {
                        return LyricsData(lines = LrcParser.parse(syncedLyrics), rawText = syncedLyrics)
                    }
                }

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val trackName = item.optString("trackName", "")
                    val plainLyrics = item.optCleanString("plainLyrics")
                    if (!plainLyrics.isNullOrBlank() && matchesTrackTitle(trackName, targetTitle)) {
                        return LyricsData(lines = LrcParser.parse(plainLyrics), rawText = plainLyrics)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchLrclibCandidates(query: String): List<LrclibSearchResultItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mueso-Android-App/1.2")
            .build()
        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                val list = mutableListOf<LrclibSearchResultItem>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    val trackName = item.optString("trackName", "")
                    val artistName = item.optString("artistName", "")
                    val albumName = item.optString("albumName", "")
                    val durationSeconds = item.optDouble("duration", 0.0).toInt()
                    val syncedLyrics = item.optCleanString("syncedLyrics")
                    val plainLyrics = item.optCleanString("plainLyrics")
                    val isSynced = !syncedLyrics.isNullOrBlank() && (syncedLyrics.contains("[0") || syncedLyrics.contains("[1") || syncedLyrics.contains("[:"))
                    if (trackName.isNotBlank()) {
                        list.add(
                            LrclibSearchResultItem(
                                id = id,
                                trackName = trackName,
                                artistName = artistName,
                                albumName = albumName,
                                durationSeconds = durationSeconds,
                                isSynced = isSynced,
                                syncedLyrics = syncedLyrics,
                                plainLyrics = plainLyrics
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ==========================================
    // 6. UTILITY HELPERS
    // ==========================================
    fun getHighResArtworkUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return if (rawUrl.contains("=w") || rawUrl.contains("=s")) {
            rawUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1200-h1200-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1200")
        } else if (rawUrl.contains("/vi/") && rawUrl.contains("/hqdefault.jpg")) {
            rawUrl.replace("/hqdefault.jpg", "/maxresdefault.jpg")
        } else {
            rawUrl
        }
    }

    fun getYouTubeArtworkFallbackList(rawUrl: String?, targetQuality: String = "Highest (1080p Maxres)"): List<String> {
        if (rawUrl.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        val isGUserContent = rawUrl.contains("googleusercontent.com") || rawUrl.contains("ggpht.com")
        val isYtImg = rawUrl.contains("/vi/") || rawUrl.contains("i.ytimg.com")

        val isHighest = targetQuality.contains("Highest", ignoreCase = true) || targetQuality.contains("1080", ignoreCase = true)
        val isHigh = targetQuality.contains("High (720p)", ignoreCase = true) || targetQuality.contains("720", ignoreCase = true)
        val isLow = targetQuality.contains("Low", ignoreCase = true) || targetQuality.contains("Fast", ignoreCase = true)
        // Default is Medium (480p)

        if (isGUserContent) {
            val base = rawUrl.replace(Regex("=w\\d+-h\\d+.*"), "").replace(Regex("=s\\d+.*"), "")
            when {
                isHighest -> {
                    list.add("$base=w1200-h1200-l90-rj")
                    list.add("$base=s1200")
                    list.add("$base=w544-h544-l90-rj")
                }
                isHigh -> {
                    list.add("$base=w800-h800-l90-rj")
                    list.add("$base=s800")
                    list.add("$base=w544-h544-l90-rj")
                    list.add("$base=w1200-h1200-l90-rj")
                }
                isLow -> {
                    list.add("$base=w226-h226-l90-rj")
                    list.add("$base=w120-h120-l90-rj")
                    list.add("$base=w544-h544-l90-rj")
                }
                else -> { // Medium (480p / 544p standard)
                    list.add("$base=w544-h544-l90-rj")
                    list.add("$base=s544")
                    list.add("$base=w226-h226-l90-rj")
                    list.add("$base=w1200-h1200-l90-rj")
                }
            }
            list.add(rawUrl)
            return list.distinct()
        }

        if (isYtImg) {
            val videoId = if (rawUrl.contains("/vi/")) {
                rawUrl.substringAfter("/vi/").substringBefore("/")
            } else {
                ""
            }
            if (videoId.isNotBlank()) {
                when {
                    isHighest -> {
                        list.add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/sddefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/hq720.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                    }
                    isHigh -> {
                        list.add("https://i.ytimg.com/vi/$videoId/hq720.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/sddefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                    }
                    isLow -> {
                        list.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/sddefault.jpg")
                    }
                    else -> { // Medium (480p)
                        list.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/sddefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/hq720.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                        list.add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                    }
                }
            }
            list.add(rawUrl)
            return list.distinct()
        }

        list.add(rawUrl)
        return list.distinct()
    }

    private fun cleanTitleAndArtist(title: String, artist: String): Pair<String, String> {
        val noiseRegex = Regex(
            "(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\]|\\(audio\\)|\\[audio\\])",
            RegexOption.IGNORE_CASE
        )
        val cleanedTitle = noiseRegex.replace(title, "")
            .replace(Regex("(?i)\\b(video|song|official|audio|remix|hd|4k)\\b"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

        val cleanedArtist = if (artist.isBlank() || artist == "Unknown Artist" ||
            artist.equals("Song", ignoreCase = true) || artist.equals("Video", ignoreCase = true) ||
            artist.equals("Single", ignoreCase = true)
        ) "" else artist.trim()
        return Pair(cleanedTitle.ifBlank { title }, cleanedArtist)
    }

    private fun matchesTrackTitle(candidateTrackName: String, targetTitle: String): Boolean {
        val cleanCandidate = candidateTrackName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTarget = targetTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        return cleanCandidate == cleanTarget || cleanCandidate.contains(cleanTarget) || cleanTarget.contains(cleanCandidate)
    }
}
