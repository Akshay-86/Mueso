package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.data.remote.innertube.InnerTubeClient
import com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf
import com.akshay.musicplayer.data.remote.innertube.InnerTubeBrowseResult
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.data.remote.innertube.isVideoShelf
import com.akshay.musicplayer.domain.models.LrcParser
import com.akshay.musicplayer.domain.models.LrclibSearchResultItem
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.domain.models.SupportedLanguages
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
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

        fun cleanTitleForLyrics(rawTitle: String): String {
            var t = rawTitle
            // 1. If contains pipe '|', take the part before '|' (very common for YT titles with movie/artist info)
            if (t.contains("|")) {
                t = t.substringBefore("|").trim()
            }
            // 2. Remove @mentions and #hashtags
            t = t.replace(Regex("[@#][a-zA-Z0-9_.]+"), " ")
            // 3. Remove common YouTube / video noise inside parentheses or brackets
            t = t.replace(
                Regex(
                    "(?i)\\s*[\\[\\(](?:official\\s*(?:music\\s*)?(?:video|audio|lyric|lyrical)?|full\\s*(?:video|audio|song)|lyric(?:al)?\\s*(?:video)?|video\\s*song|visualizer|audio|4k(?:\\s*uhd)?|hd|hq|remaster(?:ed)?|extended(?:\\s*mix)?|prod\\..*?|dir\\..*?|lyrics?)[\\x5D\\)]"
                ),
                " "
            )
            // 4. Remove standalone noise words
            t = t.replace(
                Regex("(?i)\\b(?:official\\s*(?:music\\s*)?(?:video|audio)|lyrical\\s*video|full\\s*video(?:\\s*song)?|video\\s*song|video\\s*(?:song\\s*)?with\\s*lyrics|with\\s*lyrics|lyrics?\\s*video|song\\s*with\\s*lyrics|lyrical|lyrics?|full\\s*song|audio\\s*song)\\b"),
                " "
            )
            // 5. Remove (From "...") or (feat. ...)
            t = t.replace(
                Regex("(?i)\\s*[\\[\\(](?:from|feat\\.?|featuring)\\s*[\"']?([^\"'\\[\\]\\(\\)]+)[\"']?[\\]\\)]"),
                " "
            )
            t = t.replace(Regex("\\s+"), " ")
            // 6. Clean whitespace and trailing punctuation
            t = t.trim().trim(' ', '-', '|', ':', ',')
            return t.ifBlank { rawTitle.trim() }
        }

        fun stripLanguageBrackets(text: String): String {
            val res = text.replace(
                Regex("(?i)\\s*[\\[\\(](?:telugu|hindi|tamil|malayalam|kannada|punjabi|bengali|marathi|bhojpuri|english|gujarati|odia|urdu|japanese|korean)[\\x5D\\)]"),
                ""
            ).trim()
            return res.ifBlank { text }
        }

        fun cleanArtistForLyrics(rawArtist: String): String {
            var a = rawArtist.trim()
            if (a.isBlank() || a.equals("Unknown Artist", ignoreCase = true) ||
                a.equals("Song", ignoreCase = true) || a.equals("Video", ignoreCase = true) ||
                a.equals("Single", ignoreCase = true)
            ) return ""
            a = a.replace(Regex("^@"), "").trim()
            return a
        }

        fun isRecordLabelOrChannel(artist: String): Boolean {
            val lower = artist.lowercase().trim()
            if (lower.contains("t-series") || lower.contains("aditya music") ||
                lower.contains("sony music") || lower.contains("zee music") ||
                lower.contains("lahari music") || lower.contains("saregama") ||
                lower.contains("tips official") || lower.contains("speed records") ||
                lower.contains("yrf") || lower.contains("think music") ||
                lower.contains("geetha arts") || lower.contains("eros now") ||
                lower.contains("times music") || lower.contains("junglee music")
            ) return true
            return lower.matches(Regex(".*\\b(?:music|records|company|vevo|films|official|studios|entertainment|playback|audio|series|channel|media)\\b.*"))
        }

        fun getPrimaryArtist(rawArtist: String): String {
            val clean = cleanArtistForLyrics(rawArtist)
            if (clean.isBlank()) return ""
            val first = clean.split(Regex("(?i),|&|\\bfeat\\.?\\b|\\bfeaturing\\b")).firstOrNull()?.trim() ?: clean
            return first.replace(Regex("^@"), "").trim()
        }

        fun calculateTitleMatchScore(candidate: String, target: String): Int {
            val c = candidate.trim().lowercase()
            val t = target.trim().lowercase()
            if (c == t) return 100

            val cleanC = c.replace(Regex("[^a-z0-9]"), "")
            val cleanT = t.replace(Regex("[^a-z0-9]"), "")
            if (cleanC.isEmpty() || cleanT.isEmpty()) return 0
            if (cleanC == cleanT) return 95

            // Check if one starts with the other
            if (cleanC.startsWith(cleanT) || cleanT.startsWith(cleanC)) return 80

            // Check token overlap
            val targetTokens = t.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
            if (targetTokens.isNotEmpty()) {
                val matchedTokens = targetTokens.count { c.contains(it) }
                val ratio = matchedTokens.toFloat() / targetTokens.size
                if (ratio >= 0.75f) return (60 * ratio).toInt()
            }

            // Substring fallback for names >= 4 chars
            if (cleanC.contains(cleanT) || cleanT.contains(cleanC)) {
                if (cleanT.length >= 4 && cleanC.length >= 4) return 50
            }

            return 0
        }

        fun cleanSearchQuery(title: String, artist: String = ""): String {
            val cleanTitle = cleanTitleForLyrics(title)
            val primaryArtist = getPrimaryArtist(artist)
            return if (primaryArtist.isNotBlank() && !cleanTitle.contains(primaryArtist, ignoreCase = true)) {
                "$cleanTitle $primaryArtist".trim()
            } else {
                cleanTitle
            }
        }

        fun isQueryMatch(title: String, artist: String, query: String): Boolean {
            val q = query.trim().lowercase()
            if (q.isBlank()) return true
            val t = title.lowercase()
            val a = artist.lowercase()

            val pattern = Regex("(?i)(?:\\b|^)${Regex.escape(q)}(?:\\b|$)")
            if (pattern.containsMatchIn(t) || pattern.containsMatchIn(a) || t.contains(q) || a.contains(q)) {
                return true
            }

            val tokens = q.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return true
            val combined = "$t $a"
            return tokens.all { tok ->
                Regex("(?i)(?:\\b|^)${Regex.escape(tok)}").containsMatchIn(combined)
            }
        }

        fun rankTracks(tracks: List<TrackEntity>, query: String): List<TrackEntity> {
            if (tracks.isEmpty() || query.isBlank()) return tracks
            val matchingSongs = mutableListOf<TrackEntity>()
            val matchingVideos = mutableListOf<TrackEntity>()
            val alikeSongs = mutableListOf<TrackEntity>()
            val alikeVideos = mutableListOf<TrackEntity>()

            for (track in tracks) {
                val isVid = track.isVideo
                val matches = isQueryMatch(track.title, track.artist, query)
                when {
                    matches && !isVid -> matchingSongs.add(track)
                    matches && isVid -> matchingVideos.add(track)
                    !matches && !isVid -> alikeSongs.add(track)
                    else -> alikeVideos.add(track)
                }
            }
            return matchingSongs + matchingVideos + alikeSongs + alikeVideos
        }

        fun findBestTrackMatch(
            candidates: List<TrackEntity>,
            targetTitle: String,
            targetArtist: String,
            targetDurationMs: Long = 0L
        ): TrackEntity? {
            if (candidates.isEmpty()) return null

            val cleanTargetTitle = cleanTitleForLyrics(targetTitle)
            val cleanTargetArtist = cleanArtistForLyrics(targetArtist).lowercase()
            val primaryArtist = getPrimaryArtist(targetArtist).lowercase()

            var bestCandidate: TrackEntity? = null
            var bestScore = -1

            for (cand in candidates) {
                val candTitle = cand.title
                val candArtist = cand.artist.lowercase()
                val isVid = cand.isVideo
                val candDurMs = cand.duration

                val titleScore = calculateTitleMatchScore(candTitle, cleanTargetTitle)
                if (titleScore < 40) continue

                var artistBonus = 0
                if (primaryArtist.isNotBlank() && (candArtist.contains(primaryArtist) || primaryArtist.contains(candArtist))) {
                    artistBonus = 30
                } else if (cleanTargetArtist.isNotBlank()) {
                    val parts = cleanTargetArtist.split(Regex("[,&]")).map { it.trim() }.filter { it.length >= 3 }
                    if (parts.any { candArtist.contains(it) }) {
                        artistBonus = 20
                    }
                }

                val audioBonus = if (!isVid) 25 else 0

                var durPenalty = 0
                if (targetDurationMs > 0 && candDurMs > 0) {
                    val diffSec = Math.abs(candDurMs - targetDurationMs) / 1000L
                    durPenalty = (diffSec / 2).toInt().coerceAtMost(25)
                    if (diffSec > 90) {
                        durPenalty += 30
                    }
                }

                val totalScore = titleScore + artistBonus + audioBonus - durPenalty
                if (totalScore > bestScore) {
                    bestScore = totalScore
                    bestCandidate = cand
                }
            }

            return if (bestScore >= 50) bestCandidate else null
        }
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

    // ==========================================
    // 1B. AUTHENTIC LANGUAGE PLAYLISTS & DISK CACHE
    // ==========================================
    private val languageShelvesMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InnerTubeShelf>>>()
    private val languagePlaylistsMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InnerTubePlaylist>>>()
    private val LANGUAGE_PLAYLISTS_CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL
    private val chartsMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InnerTubeShelf>>>()
    private val CHARTS_CACHE_TTL_MS = 10 * 60 * 1000L

    private data class RegionalLanguageInfo(
        val industry: String,
        val spotlightArtists: List<String>
    )

    private fun getRegionalLanguageInfo(languageName: String): RegionalLanguageInfo {
        return when (languageName.lowercase()) {
            "telugu" -> RegionalLanguageInfo(
                industry = "Tollywood",
                spotlightArtists = listOf("Anirudh Telugu", "Sid Sriram", "DSP", "Devi Sri Prasad", "Thaman", "SPB", "Anurag Kulkarni", "Ram Miriyala")
            )
            "hindi" -> RegionalLanguageInfo(
                industry = "Bollywood",
                spotlightArtists = listOf("Arijit Singh", "Shreya Ghoshal", "Pritam", "Neha Kakkar", "Atif Aslam", "Sachin-Jigar")
            )
            "tamil" -> RegionalLanguageInfo(
                industry = "Kollywood",
                spotlightArtists = listOf("Anirudh", "AR Rahman", "Yuvan Shankar Raja", "Harris Jayaraj", "Sid Sriram Tamil", "Dhanush")
            )
            "punjabi" -> RegionalLanguageInfo(
                industry = "Punjabi",
                spotlightArtists = listOf("Diljit Dosanjh", "AP Dhillon", "Sidhu Moosewala", "Karan Aujla", "B Praak")
            )
            "malayalam" -> RegionalLanguageInfo(
                industry = "Mollywood",
                spotlightArtists = listOf("Sushin Shyam", "KS Harisankar", "Job Kurian", "Hesham Abdul Wahab", "Vineeth Sreenivasan")
            )
            "kannada" -> RegionalLanguageInfo(
                industry = "Sandalwood",
                spotlightArtists = listOf("Sanjith Hegde", "Charan Raj", "Ravi Basrur", "Vijay Prakash", "Arjun Janya")
            )
            "bengali" -> RegionalLanguageInfo(
                industry = "Bengali",
                spotlightArtists = listOf("Anupam Roy", "Shreya Ghoshal Bengali", "Arijit Singh Bengali", "Jeet Gannguli")
            )
            "marathi" -> RegionalLanguageInfo(
                industry = "Marathi",
                spotlightArtists = listOf("Ajay-Atul", "Swapnil Bandodkar", "Avadhoot Gupte", "Adarsh Shinde")
            )
            "gujarati" -> RegionalLanguageInfo(
                industry = "Gujarati",
                spotlightArtists = listOf("Sachin-Jigar", "Jigardan Gadhvi", "Kinjal Dave", "Aditya Gadhvi")
            )
            "bhojpuri" -> RegionalLanguageInfo(
                industry = "Bhojpuri",
                spotlightArtists = listOf("Pawan Singh", "Khesari Lal Yadav", "Shilpi Raj")
            )
            "korean" -> RegionalLanguageInfo(
                industry = "K-Pop",
                spotlightArtists = listOf("BTS", "BLACKPINK", "NewJeans", "Stray Kids", "TWICE")
            )
            "spanish" -> RegionalLanguageInfo(
                industry = "Latin",
                spotlightArtists = listOf("Bad Bunny", "Rosalía", "J Balvin", "Shakira")
            )
            "english" -> RegionalLanguageInfo(
                industry = "Pop",
                spotlightArtists = listOf("Taylor Swift", "The Weeknd", "Drake", "Eminem", "Billie Eilish")
            )
            else -> {
                val supported = SupportedLanguages.getLanguageByName(languageName)
                val artists = if (supported.sampleArtists.isNotBlank()) {
                    supported.sampleArtists.split(",").map { it.trim() }.filter { it.isNotBlank() }
                } else listOf(languageName)
                RegionalLanguageInfo(
                    industry = supported.name,
                    spotlightArtists = artists
                )
            }
        }
    }

    private fun getLanguageCacheFile(language: String): File? {
        val context = try { com.akshay.musicplayer.AppContainer.getContext() } catch (_: Exception) { null } ?: return null
        val cacheDir = File(context.cacheDir, "language_playlists_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeLang = language.lowercase().replace(Regex("[^a-z0-9]"), "_")
        return File(cacheDir, "lang_${safeLang}.json")
    }

    private fun getLanguageShelvesCacheFile(language: String): File? {
        val context = try { com.akshay.musicplayer.AppContainer.getContext() } catch (_: Exception) { null } ?: return null
        val cacheDir = File(context.cacheDir, "language_playlists_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeLang = language.lowercase().replace(Regex("[^a-z0-9]"), "_")
        return File(cacheDir, "shelves_${safeLang}.json")
    }

    private fun getChartsCacheFile(language: String): File? {
        val context = try { com.akshay.musicplayer.AppContainer.getContext() } catch (_: Exception) { null } ?: return null
        val cacheDir = File(context.cacheDir, "language_playlists_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeLang = language.lowercase().replace(Regex("[^a-z0-9]"), "_")
        return File(cacheDir, "charts_${safeLang}.json")
    }

    private val diskCacheLock = Any()

    fun clearMemoryCaches() {
        languageShelvesMemoryCache.clear()
        languagePlaylistsMemoryCache.clear()
        chartsMemoryCache.clear()
        trackIdToVideoIdMap.clear()
    }

    private fun readLanguagePlaylistsFromDisk(language: String): List<InnerTubePlaylist> = synchronized(diskCacheLock) {
        try {
            val file = getLanguageCacheFile(language) ?: return emptyList()
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val root = JSONObject(text)
            val timestamp = root.optLong("timestamp", 0L)
            val isValid = (System.currentTimeMillis() - timestamp) < 86_400_000L // 24 hours
            if (!isValid) return emptyList()
            parsePlaylistsJsonArray(root.optJSONArray("playlists"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading language playlists disk cache for $language", e)
            emptyList()
        }
    }

    private fun readExpiredLanguagePlaylistsFromDisk(language: String): List<InnerTubePlaylist> = synchronized(diskCacheLock) {
        try {
            val file = getLanguageCacheFile(language) ?: return emptyList()
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val root = JSONObject(text)
            parsePlaylistsJsonArray(root.optJSONArray("playlists"))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveShelvesToDiskFile(file: File?, shelves: List<InnerTubeShelf>, description: String) {
        if (file == null) return
        synchronized(diskCacheLock) {
            try {
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                val root = JSONObject()
                root.put("timestamp", System.currentTimeMillis())
                val shelvesArr = JSONArray()
                for (shelf in shelves) {
                    val shelfObj = JSONObject()
                    shelfObj.put("title", shelf.title)
                    shelfObj.put("subtitle", shelf.subtitle)
                    val plArr = JSONArray()
                    for (pl in shelf.playlists) {
                        val obj = JSONObject()
                        obj.put("id", pl.id)
                        obj.put("title", pl.title)
                        obj.put("subtitle", pl.subtitle)
                        obj.put("artworkUrl", pl.artworkUrl ?: "")
                        obj.put("trackCount", pl.trackCount)
                        obj.put("author", pl.author)
                        plArr.put(obj)
                    }
                    shelfObj.put("playlists", plArr)

                    val trkArr = JSONArray()
                    for (t in shelf.tracks) {
                        val obj = JSONObject()
                        obj.put("videoId", t.videoId)
                        obj.put("title", t.title)
                        obj.put("artist", t.artist)
                        obj.put("album", t.album ?: "")
                        obj.put("durationSec", t.durationSec)
                        obj.put("artworkUrl", t.artworkUrl ?: "")
                        trkArr.put(obj)
                    }
                    shelfObj.put("tracks", trkArr)

                    shelvesArr.put(shelfObj)
                }
                root.put("shelves", shelvesArr)
                tempFile.writeText(root.toString())
                if (!tempFile.renameTo(file)) {
                    file.writeText(root.toString())
                    tempFile.delete()
                }
                Log.d(TAG, "Saved ${shelves.size} shelves ($description) to disk cache at ${file.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving $description disk cache", e)
            }
        }
    }

    private fun readShelvesFromDiskFile(file: File?, allowExpired: Boolean = false, description: String): List<InnerTubeShelf> {
        if (file == null) return emptyList()
        return synchronized(diskCacheLock) {
            try {
                if (!file.exists()) return@synchronized emptyList()
                val text = file.readText()
                if (text.isBlank()) return@synchronized emptyList()
                val root = JSONObject(text)
                val timestamp = root.optLong("timestamp", 0L)
                val isValid = (System.currentTimeMillis() - timestamp) < 86_400_000L // 24 hours
                if (!isValid && !allowExpired) return@synchronized emptyList()
                val shelvesArr = root.optJSONArray("shelves") ?: return@synchronized emptyList()
                val result = mutableListOf<InnerTubeShelf>()
                for (i in 0 until shelvesArr.length()) {
                    val shelfObj = shelvesArr.optJSONObject(i) ?: continue
                    val title = shelfObj.optString("title")
                    val subtitle = shelfObj.optString("subtitle")
                    val playlists = parsePlaylistsJsonArray(shelfObj.optJSONArray("playlists"))
                    val tracks = parseTracksJsonArray(shelfObj.optJSONArray("tracks"))
                    if (title.isNotBlank() && (playlists.isNotEmpty() || tracks.isNotEmpty())) {
                        result.add(
                            InnerTubeShelf(
                                title = title,
                                subtitle = subtitle,
                                playlists = playlists,
                                tracks = tracks
                            )
                        )
                    }
                }
                result
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading $description disk cache", e)
                emptyList()
            }
        }
    }

    private fun saveLanguageShelvesToDisk(language: String, shelves: List<InnerTubeShelf>) {
        saveShelvesToDiskFile(getLanguageShelvesCacheFile(language), shelves, "language shelves for $language")
    }

    private fun readLanguageShelvesFromDisk(language: String, allowExpired: Boolean = false): List<InnerTubeShelf> {
        return readShelvesFromDiskFile(getLanguageShelvesCacheFile(language), allowExpired, "language shelves for $language")
    }

    private fun saveChartsShelvesToDisk(language: String, shelves: List<InnerTubeShelf>) {
        saveShelvesToDiskFile(getChartsCacheFile(language), shelves, "charts shelves for $language")
    }

    private fun readChartsShelvesFromDisk(language: String, allowExpired: Boolean = false): List<InnerTubeShelf> {
        return readShelvesFromDiskFile(getChartsCacheFile(language), allowExpired, "charts shelves for $language")
    }

    private fun parseTracksJsonArray(arr: JSONArray?): List<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack> {
        if (arr == null) return emptyList()
        val list = mutableListOf<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val videoId = obj.optString("videoId")
            val title = obj.optString("title")
            val artist = obj.optString("artist")
            if (videoId.isNotBlank() && title.isNotBlank()) {
                list.add(
                    com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        album = obj.optString("album").takeIf { it.isNotBlank() },
                        durationSec = obj.optInt("durationSec", 0),
                        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return list
    }

    private fun parsePlaylistsJsonArray(arr: JSONArray?): List<InnerTubePlaylist> {
        if (arr == null) return emptyList()
        val list = mutableListOf<InnerTubePlaylist>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val title = obj.optString("title")
            if (id.isNotBlank() && title.isNotBlank()) {
                list.add(
                    InnerTubePlaylist(
                        id = id,
                        title = title,
                        subtitle = obj.optString("subtitle", ""),
                        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                        trackCount = obj.optInt("trackCount", 0),
                        author = obj.optString("author", "")
                    )
                )
            }
        }
        return list
    }

    private fun saveLanguagePlaylistsToDisk(language: String, playlists: List<InnerTubePlaylist>) {
        val file = getLanguageCacheFile(language) ?: return
        synchronized(diskCacheLock) {
            try {
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                val root = JSONObject()
                root.put("timestamp", System.currentTimeMillis())
                root.put("language", language)
                val arr = JSONArray()
                for (pl in playlists) {
                    val obj = JSONObject()
                    obj.put("id", pl.id)
                    obj.put("title", pl.title)
                    obj.put("subtitle", pl.subtitle)
                    obj.put("artworkUrl", pl.artworkUrl ?: "")
                    obj.put("trackCount", pl.trackCount)
                    obj.put("author", pl.author)
                    arr.put(obj)
                }
                root.put("playlists", arr)
                tempFile.writeText(root.toString())
                if (!tempFile.renameTo(file)) {
                    file.writeText(root.toString())
                    tempFile.delete()
                }
                Log.d(TAG, "Saved ${playlists.size} playlists for $language to disk cache at ${file.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving language playlists disk cache for $language", e)
            }
        }
    }

    suspend fun fetchLanguagePlaylistShelves(language: String, forceRefresh: Boolean = false): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val cleanLang = SupportedLanguages.getLanguageByName(language).name
        if (cleanLang.equals("Auto", ignoreCase = true) || cleanLang.equals("Default", ignoreCase = true)) {
            return@withContext emptyList()
        }
        val cacheKey = cleanLang.lowercase()

        // 1. In-memory cache check
        if (!forceRefresh) {
            val memCached = languageShelvesMemoryCache[cacheKey]
            if (memCached != null && (System.currentTimeMillis() - memCached.first) < LANGUAGE_PLAYLISTS_CACHE_TTL_MS && memCached.second.isNotEmpty()) {
                return@withContext memCached.second
            }
            // 2. Disk cache check
            val diskCached = readLanguageShelvesFromDisk(cleanLang, allowExpired = false)
            if (diskCached.isNotEmpty()) {
                languageShelvesMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), diskCached)
                return@withContext diskCached
            }
        }

        val regional = getRegionalLanguageInfo(cleanLang)
        val industry = regional.industry
        val artists = regional.spotlightArtists

        // Parallel query executor
        suspend fun fetchPlaylistsForQueries(vararg queries: String): List<InnerTubePlaylist> = coroutineScope {
            val validQueries = queries.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val deferreds = validQueries.map { q ->
                async {
                    ensureActive()
                    runCatching {
                        innerTube.searchPlaylists(q)
                    }.getOrDefault(emptyList())
                }
            }
            val combined = deferreds.flatMap { it.await() }
            combined.filterNot { isVideoShelf(it.title, it.subtitle) }.distinctBy { it.id }
        }

        try {
            val shelves = coroutineScope {
                // Shelf 1: "Featured playlists for you"
                val featuredOfficialQueries = mutableListOf<String>()
                if (!industry.equals(cleanLang, ignoreCase = true)) {
                    featuredOfficialQueries.add("$industry Dance Essentials")
                    featuredOfficialQueries.add("$industry Hitlist")
                    featuredOfficialQueries.add("$industry Party")
                    featuredOfficialQueries.add("$industry Hits")
                }
                featuredOfficialQueries.add("$cleanLang Dance Essentials")
                featuredOfficialQueries.add("$cleanLang Hitlist")
                featuredOfficialQueries.add("$cleanLang Hits")
                featuredOfficialQueries.add("$cleanLang Essentials")
                artists.take(4).forEach { artist ->
                    val trimmed = artist.trim()
                    if (trimmed.isNotBlank()) {
                        featuredOfficialQueries.add("Presenting $trimmed")
                    }
                }
                featuredOfficialQueries.add("Presenting $cleanLang")

                val featuredCommunityQueries = if (!industry.equals(cleanLang, ignoreCase = true)) {
                    listOf(
                        "$industry Dance Essentials",
                        "$cleanLang Hits",
                        "$cleanLang Best Songs",
                        "Top Weekly Songs $cleanLang",
                        "$cleanLang Melody Hits",
                        "$industry Party"
                    )
                } else {
                    listOf(
                        "$cleanLang Dance Essentials",
                        "$cleanLang Hits",
                        "$cleanLang Best Songs",
                        "Top Weekly Songs $cleanLang",
                        "$cleanLang Melody Hits",
                        "$cleanLang Party"
                    )
                }

                val cleanFeaturedOfficialQueries = featuredOfficialQueries.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val cleanFeaturedCommunityQueries = featuredCommunityQueries.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val featuredOfficialDeferred = async {
                    ensureActive()
                    runCatching {
                        innerTube.searchOfficialCardPlaylists(cleanFeaturedOfficialQueries)
                    }.getOrDefault(emptyList())
                }
                val featuredCommunityDeferred = async {
                    ensureActive()
                    fetchPlaylistsForQueries(*cleanFeaturedCommunityQueries.toTypedArray())
                }

                // Shelf 2: "Trending songs for you"
                val trendingSongsDeferred = async {
                    ensureActive()
                    runCatching {
                        val tracks = innerTube.searchSongs("Top 50 $cleanLang Songs", limit = 20)
                        ensureActive()
                        if (tracks.isNotEmpty()) tracks else innerTube.searchSongs("Top $cleanLang Songs", limit = 20)
                    }.getOrDefault(emptyList()).filterNot {
                        it.itemType.equals("Video", ignoreCase = true) ||
                        it.title.contains("Official Music Video", ignoreCase = true) ||
                        it.title.contains("Official Video", ignoreCase = true)
                    }
                }

                // Shelf 3: "Brb, Being Nostalgic!"
                val nostalgicOfficialQueries = if (!industry.equals(cleanLang, ignoreCase = true)) {
                    listOf(
                        "00s Unwind $cleanLang",
                        "00s $industry Romance",
                        "00s $cleanLang Romance",
                        "10s $industry Dance Hits",
                        "10s $cleanLang Dance Hits",
                        "00s $industry",
                        "10s $industry",
                        "90s $industry",
                        "00s $industry Dance",
                        "00s $cleanLang",
                        "10s $cleanLang",
                        "90s $cleanLang"
                    )
                } else {
                    listOf(
                        "00s Unwind $cleanLang",
                        "00s $cleanLang Romance",
                        "10s $cleanLang Dance Hits",
                        "00s $cleanLang Dance",
                        "00s $cleanLang",
                        "10s $cleanLang",
                        "90s $cleanLang"
                    )
                }

                val nostalgicCommunityQueries = if (!industry.equals(cleanLang, ignoreCase = true)) {
                    listOf(
                        "00s $industry Romance",
                        "00s $cleanLang",
                        "10s $cleanLang",
                        "90s $cleanLang",
                        "00s $cleanLang Romance",
                        "00s $cleanLang Melody Hits",
                        "Forgotten Faves $cleanLang",
                        "90s $industry Hits"
                    )
                } else {
                    listOf(
                        "00s $cleanLang Romance",
                        "00s $cleanLang",
                        "10s $cleanLang",
                        "90s $cleanLang",
                        "00s $cleanLang Melody Hits",
                        "Forgotten Faves $cleanLang",
                        "90s $cleanLang Hits"
                    )
                }

                val cleanNostalgicOfficialQueries = nostalgicOfficialQueries.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val cleanNostalgicCommunityQueries = nostalgicCommunityQueries.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val nostalgicOfficialDeferred = async {
                    ensureActive()
                    runCatching {
                        innerTube.searchOfficialCardPlaylists(cleanNostalgicOfficialQueries)
                    }.getOrDefault(emptyList())
                }
                val nostalgicCommunityDeferred = async {
                    ensureActive()
                    fetchPlaylistsForQueries(*cleanNostalgicCommunityQueries.toTypedArray())
                }

                // Shelf 4: "Albums for you"
                val albumsDeferred = async {
                    ensureActive()
                    val albumQueries = if (!industry.equals(cleanLang, ignoreCase = true)) {
                        listOf("$cleanLang", "$industry New Releases", "$cleanLang New Releases", "$cleanLang Soundtrack")
                    } else {
                        listOf("$cleanLang", "$cleanLang New Releases", "$cleanLang Albums", "$cleanLang Soundtrack")
                    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                    val fetchedAlbums = albumQueries.map { q ->
                        async {
                            ensureActive()
                            runCatching { innerTube.searchAlbums(q) }.getOrDefault(emptyList())
                        }
                    }.flatMap { it.await() }

                    ensureActive()
                    val officialAlbumQueries = listOf(
                        "New Releases only $cleanLang",
                        "New Releases $cleanLang",
                        "$cleanLang New Releases"
                    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

                    val officialAlbumCards = runCatching {
                        innerTube.searchOfficialCardPlaylists(officialAlbumQueries)
                    }.getOrDefault(emptyList())

                    (officialAlbumCards + fetchedAlbums)
                        .distinctBy { it.id }
                        .filterNot { isVideoShelf(it.title, it.subtitle) }
                }

                val shelfList = mutableListOf<InnerTubeShelf>()
                val globalSeenIds = mutableSetOf<String>()

                // 1. "Featured playlists for you"
                val rawFeaturedOfficial = featuredOfficialDeferred.await()
                val rawFeaturedCommunity = featuredCommunityDeferred.await()
                val featuredPlaylists = (rawFeaturedOfficial + rawFeaturedCommunity)
                    .distinctBy { it.id }
                    .take(14)
                    .filter { globalSeenIds.add(it.id) }
                if (featuredPlaylists.isNotEmpty()) {
                    shelfList.add(
                        InnerTubeShelf(
                            title = "Featured playlists for you",
                            subtitle = "Curated playlists & mixes in $cleanLang",
                            playlists = featuredPlaylists
                        )
                    )
                }

                // 2. "Trending songs for you" (song shelf with compact list rows)
                val trendingTracks = trendingSongsDeferred.await()
                if (trendingTracks.isNotEmpty()) {
                    shelfList.add(
                        InnerTubeShelf(
                            title = "Trending songs for you",
                            subtitle = "Top songs in $cleanLang",
                            tracks = trendingTracks
                        )
                    )
                }

                // 3. "Brb, Being Nostalgic!" (decades 00s/10s/90s)
                val rawNostalgicOfficial = nostalgicOfficialDeferred.await()
                val rawNostalgicCommunity = nostalgicCommunityDeferred.await()
                val nostalgicPlaylists = (rawNostalgicOfficial + rawNostalgicCommunity)
                    .distinctBy { it.id }
                    .take(14)
                    .filter { globalSeenIds.add(it.id) }
                if (nostalgicPlaylists.isNotEmpty()) {
                    shelfList.add(
                        InnerTubeShelf(
                            title = "Brb, Being Nostalgic!",
                            subtitle = "Throwback to the OG eras of music",
                            playlists = nostalgicPlaylists
                        )
                    )
                }

                // 4. "Albums for you" (New releases & albums in <Language>)
                val albums = albumsDeferred.await().filter { globalSeenIds.add(it.id) }
                if (albums.isNotEmpty()) {
                    shelfList.add(
                        InnerTubeShelf(
                            title = "Albums for you",
                            subtitle = "New releases in $cleanLang",
                            playlists = albums
                        )
                    )
                }

                shelfList
            }

            if (shelves.isNotEmpty()) {
                val allPlaylists = shelves.flatMap { it.playlists }.distinctBy { it.id }
                languageShelvesMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), shelves)
                languagePlaylistsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), allPlaylists)
                saveLanguageShelvesToDisk(cleanLang, shelves)
                saveLanguagePlaylistsToDisk(cleanLang, allPlaylists)
                return@withContext shelves
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "fetchLanguagePlaylistShelves failed for $cleanLang", e)
        }

        // Stale disk cache fallback on network failure or empty response
        val fallback = readLanguageShelvesFromDisk(cleanLang, allowExpired = true)
        if (fallback.isNotEmpty()) {
            val allPlaylists = fallback.flatMap { it.playlists }.distinctBy { it.id }
            languageShelvesMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), fallback)
            languagePlaylistsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), allPlaylists)
            return@withContext fallback
        }

        emptyList()
    }

    suspend fun fetchLanguagePlaylists(language: String, forceRefresh: Boolean = false): List<InnerTubePlaylist> = withContext(Dispatchers.IO) {
        val cleanLang = SupportedLanguages.getLanguageByName(language).name
        if (cleanLang.equals("Auto", ignoreCase = true) || cleanLang.equals("Default", ignoreCase = true)) {
            return@withContext emptyList()
        }
        val cacheKey = cleanLang.lowercase()

        // 1. Check in-memory cache
        if (!forceRefresh) {
            val cached = languagePlaylistsMemoryCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.first) < LANGUAGE_PLAYLISTS_CACHE_TTL_MS && cached.second.isNotEmpty()) {
                return@withContext cached.second
            }
            // 2. Check disk cache
            val diskCached = readLanguagePlaylistsFromDisk(cleanLang)
            if (diskCached.isNotEmpty()) {
                languagePlaylistsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), diskCached)
                return@withContext diskCached
            }
        }

        // 3. Fetch comprehensive authentic playlist shelves and flatten them
        val shelves = fetchLanguagePlaylistShelves(cleanLang, forceRefresh = forceRefresh)
        if (shelves.isNotEmpty()) {
            val playlists = shelves.flatMap { it.playlists }.distinctBy { it.id }
            if (playlists.isNotEmpty()) {
                languagePlaylistsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), playlists)
                saveLanguagePlaylistsToDisk(cleanLang, playlists)
                return@withContext playlists
            }
        }

        // 4. Stale disk cache fallback on network failure or empty response
        val fallback = readExpiredLanguagePlaylistsFromDisk(cleanLang)
        if (fallback.isNotEmpty()) {
            languagePlaylistsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), fallback)
            return@withContext fallback
        }

        emptyList()
    }

    fun filterAudioShelves(shelves: List<InnerTubeShelf>): List<InnerTubeShelf> {
        return shelves.filterNot { isVideoShelf(it.title, it.subtitle) }
            .map { shelf ->
                shelf.copy(
                    playlists = shelf.playlists.filterNot { isVideoShelf(it.title, it.subtitle) },
                    tracks = shelf.tracks.filterNot { it.itemType.equals("Video", ignoreCase = true) }
                )
            }
            .filter { it.tracks.isNotEmpty() || it.playlists.isNotEmpty() }
    }

    suspend fun fetchHomeFeedResult(): InnerTubeBrowseResult = withContext(Dispatchers.IO) {
        val result = innerTube.getHomeFeedResult()
        InnerTubeBrowseResult(filterAudioShelves(result.shelves), result.continuationToken)
    }

    suspend fun fetchExploreResult(): InnerTubeBrowseResult = withContext(Dispatchers.IO) {
        val result = innerTube.getHomeFeedResult()
        InnerTubeBrowseResult(filterAudioShelves(result.shelves), result.continuationToken)
    }

    suspend fun fetchBrowseContinuation(continuationToken: String): InnerTubeBrowseResult = withContext(Dispatchers.IO) {
        val result = innerTube.getShelvesContinuation(continuationToken)
        InnerTubeBrowseResult(filterAudioShelves(result.shelves), result.continuationToken)
    }

    suspend fun fetchExploreShelves(): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        fetchExploreResult().shelves
    }

    suspend fun fetchChartsShelves(language: String = "Auto", forceRefresh: Boolean = false): List<InnerTubeShelf> = withContext(Dispatchers.IO) {
        val cleanLang = SupportedLanguages.getLanguageByName(language).name
        val isAuto = cleanLang.equals("Auto", ignoreCase = true) || cleanLang.equals("Default", ignoreCase = true)
        val cacheKey = cleanLang.lowercase()

        if (!forceRefresh) {
            val cached = chartsMemoryCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.first) < CHARTS_CACHE_TTL_MS && cached.second.isNotEmpty()) {
                return@withContext cached.second
            }
            val diskCached = readChartsShelvesFromDisk(cleanLang, allowExpired = false)
            if (diskCached.isNotEmpty()) {
                chartsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), diskCached)
                return@withContext diskCached
            }
        }

        val result = mutableListOf<InnerTubeShelf>()
        val existingTitles = mutableSetOf<String>()

        fun addShelfIfNew(shelf: InnerTubeShelf) {
            val key = shelf.title.trim().lowercase()
            if (key.isNotBlank() && !existingTitles.contains(key) && (shelf.tracks.isNotEmpty() || shelf.playlists.isNotEmpty())) {
                existingTitles.add(key)
                result.add(shelf)
            }
        }

        try {
            coroutineScope {
                // 1. Language-specific async fetches (only when user explicitly selects a regional language)
                val langTopSongsDeferred: kotlinx.coroutines.Deferred<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack>>? = if (!isAuto) async {
                    ensureActive()
                    runCatching {
                        val tracks = innerTube.searchSongs("Top 50 $cleanLang Songs", limit = 25)
                        ensureActive()
                        if (tracks.isNotEmpty()) tracks else innerTube.searchSongs("Top $cleanLang Songs", limit = 25)
                    }.getOrDefault(emptyList())
                } else null
                val langTrendingDeferred: kotlinx.coroutines.Deferred<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack>>? = if (!isAuto) async {
                    ensureActive()
                    runCatching {
                        val tracks = innerTube.searchSongs("Trending $cleanLang Audio Hits", limit = 25)
                        ensureActive()
                        if (tracks.isNotEmpty()) tracks else innerTube.searchSongs("Trending $cleanLang Songs", limit = 25)
                    }.getOrDefault(emptyList())
                } else null
                val langViralDeferred: kotlinx.coroutines.Deferred<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack>>? = if (!isAuto) async {
                    ensureActive()
                    runCatching {
                        val tracks = innerTube.searchSongs("Viral $cleanLang 50", limit = 25)
                        ensureActive()
                        if (tracks.isNotEmpty()) tracks else innerTube.searchSongs("Viral 50 $cleanLang", limit = 25)
                    }.getOrDefault(emptyList())
                } else null
                val langPlaylistsChartsDeferred: kotlinx.coroutines.Deferred<List<InnerTubePlaylist>>? = if (!isAuto) async {
                    ensureActive()
                    runCatching { innerTube.searchPlaylists("$cleanLang Charts") }.getOrDefault(emptyList())
                } else null
                val langPlaylistsTop100Deferred: kotlinx.coroutines.Deferred<List<InnerTubePlaylist>>? = if (!isAuto) async {
                    ensureActive()
                    runCatching { innerTube.searchPlaylists("Top 100 $cleanLang") }.getOrDefault(emptyList())
                } else null

                // 2. National / Global async fetches
                val topGlobalDeferred = async {
                    ensureActive()
                    runCatching { innerTube.searchSongs("Top Global Songs", limit = 25) }.getOrDefault(emptyList())
                }
                val topIndiaDeferred = async {
                    ensureActive()
                    runCatching { innerTube.searchSongs("Top 50 India", limit = 25) }.getOrDefault(emptyList())
                }
                val chartPlaylistsDeferred = async {
                    ensureActive()
                    runCatching { innerTube.searchPlaylists("Top Charts") }.getOrDefault(emptyList())
                }
                val chartsDeferred = async {
                    ensureActive()
                    runCatching { innerTube.getChartsShelves() }.getOrDefault(emptyList())
                }

                // 1. "Trending songs for you"
                if (!isAuto && langTopSongsDeferred != null) {
                    val langTopTracks = langTopSongsDeferred.await().filterNot {
                        it.itemType.equals("Video", ignoreCase = true) ||
                        it.title.contains("Official Music Video", ignoreCase = true) ||
                        it.title.contains("Official Video", ignoreCase = true)
                    }
                    if (langTopTracks.isNotEmpty()) {
                        addShelfIfNew(
                            InnerTubeShelf(
                                title = "Trending songs for you",
                                subtitle = "Most played audio hits in $cleanLang",
                                tracks = langTopTracks
                            )
                        )
                    }
                }

                // 2. "Trending $cleanLang Audio Hits"
                if (!isAuto && langTrendingDeferred != null) {
                    val langTrendingTracks = langTrendingDeferred.await().filterNot {
                        it.itemType.equals("Video", ignoreCase = true) ||
                        it.title.contains("Official Music Video", ignoreCase = true) ||
                        it.title.contains("Official Video", ignoreCase = true)
                    }
                    if (langTrendingTracks.isNotEmpty()) {
                        addShelfIfNew(
                            InnerTubeShelf(
                                title = "Trending $cleanLang Audio Hits",
                                subtitle = "Popular audio tracks trending in $cleanLang",
                                tracks = langTrendingTracks
                            )
                        )
                    }
                }

                // 3. "Viral $cleanLang 50"
                if (!isAuto && langViralDeferred != null) {
                    val langViralTracks = langViralDeferred.await().filterNot {
                        it.itemType.equals("Video", ignoreCase = true) ||
                        it.title.contains("Official Music Video", ignoreCase = true) ||
                        it.title.contains("Official Video", ignoreCase = true)
                    }
                    if (langViralTracks.isNotEmpty()) {
                        addShelfIfNew(
                            InnerTubeShelf(
                                title = "Viral $cleanLang 50",
                                subtitle = "Viral breakout audio tracks in $cleanLang",
                                tracks = langViralTracks
                            )
                        )
                    }
                }

                // 4. "$cleanLang Chart Playlists"
                if (!isAuto && langPlaylistsChartsDeferred != null && langPlaylistsTop100Deferred != null) {
                    val langPlaylists = (langPlaylistsChartsDeferred.await() + langPlaylistsTop100Deferred.await())
                        .distinctBy { it.id }
                        .filterNot { isVideoShelf(it.title, it.subtitle) }
                    if (langPlaylists.isNotEmpty()) {
                        addShelfIfNew(
                            InnerTubeShelf(
                                title = "$cleanLang Chart Playlists",
                                subtitle = "Curated official chart playlists in $cleanLang",
                                playlists = langPlaylists
                            )
                        )
                    }
                }

                // 5. "Top Global Songs"
                val topGlobalTracks = topGlobalDeferred.await().filterNot {
                    it.itemType.equals("Video", ignoreCase = true) ||
                    it.title.contains("Official Music Video", ignoreCase = true) ||
                    it.title.contains("Official Video", ignoreCase = true)
                }
                if (topGlobalTracks.isNotEmpty()) {
                    addShelfIfNew(
                        InnerTubeShelf(
                            title = "Top Global Songs",
                            subtitle = "Most played audio tracks worldwide",
                            tracks = topGlobalTracks
                        )
                    )
                }

                // 6. "Top 50 India"
                val topIndiaTracks = topIndiaDeferred.await().filterNot {
                    it.itemType.equals("Video", ignoreCase = true) ||
                    it.title.contains("Official Music Video", ignoreCase = true) ||
                    it.title.contains("Official Video", ignoreCase = true)
                }
                if (topIndiaTracks.isNotEmpty()) {
                    addShelfIfNew(
                        InnerTubeShelf(
                            title = "Top 50 India",
                            subtitle = "Biggest audio hits trending across India",
                            tracks = topIndiaTracks
                        )
                    )
                }

                // 7. "Top Chart Playlists"
                val chartPlaylists = chartPlaylistsDeferred.await().filterNot { isVideoShelf(it.title, it.subtitle) }
                if (chartPlaylists.isNotEmpty()) {
                    addShelfIfNew(
                        InnerTubeShelf(
                            title = "Top Chart Playlists",
                            subtitle = "Official chart rankings & curated hit collections",
                            playlists = chartPlaylists
                        )
                    )
                }

                // 8. Additional clean audio shelves from InnerTube charts browse
                val rawCharts = chartsDeferred.await()
                val cleanRawShelves = rawCharts.filterNot { isVideoShelf(it.title, it.subtitle) }
                    .map { shelf ->
                        shelf.copy(
                            playlists = shelf.playlists.filterNot { isVideoShelf(it.title, it.subtitle) },
                            tracks = shelf.tracks.filterNot {
                                it.itemType.equals("Video", ignoreCase = true) ||
                                it.title.contains("Official Music Video", ignoreCase = true) ||
                                it.title.contains("Official Video", ignoreCase = true)
                            }
                        )
                    }
                    .filter { it.tracks.isNotEmpty() || it.playlists.isNotEmpty() }

                cleanRawShelves.forEach { addShelfIfNew(it) }
            }

            if (result.isNotEmpty()) {
                chartsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), result)
                saveChartsShelvesToDisk(cleanLang, result)
                return@withContext result
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "fetchChartsShelves failed for $cleanLang", e)
        }

        // Stale disk cache fallback on network failure or empty response
        val fallback = readChartsShelvesFromDisk(cleanLang, allowExpired = true)
        if (fallback.isNotEmpty()) {
            chartsMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), fallback)
            return@withContext fallback
        }

        result
    }

    suspend fun fetchMoodShelves(mood: String): List<InnerTubeShelf> {
        return innerTube.getShelvesForMood(mood)
    }

    /**
     * Cleanly combines language-specific curated shelves with the dynamic home feed
     * preserving authentic dynamic shelf titles without crushing or overriding them.
     * Deduplicates shelves by title and playlist/track content.
     */
    fun combineHomeFeedWithLanguageShelves(
        homeShelves: List<InnerTubeShelf>,
        languageShelves: List<InnerTubeShelf>,
        chartsShelves: List<InnerTubeShelf> = emptyList(),
        languagePlaylists: List<InnerTubePlaylist> = emptyList(),
        preferredLanguage: String = ""
    ): List<InnerTubeShelf> {
        val isAuto = preferredLanguage.isBlank() ||
                preferredLanguage.equals("Auto", ignoreCase = true) ||
                preferredLanguage.equals("Default", ignoreCase = true)

        if (isAuto) {
            return homeShelves
        }

        if (languageShelves.isEmpty() && languagePlaylists.isEmpty()) {
            return homeShelves
        }

        val result = mutableListOf<InnerTubeShelf>()
        val seenTitles = mutableSetOf<String>()
        val seenTrackIds = mutableSetOf<String>()
        val seenPlaylistIds = mutableSetOf<String>()

        fun addShelf(shelf: InnerTubeShelf) {
            val trimmedTitle = shelf.title.trim()
            if (trimmedTitle.isBlank()) return
            val normTitle = trimmedTitle.lowercase()
            if (seenTitles.contains(normTitle)) return

            val validTracks = shelf.tracks.filterNot {
                it.itemType.equals("Video", ignoreCase = true) ||
                it.title.contains("Official Music Video", ignoreCase = true) ||
                it.title.contains("Official Video", ignoreCase = true)
            }
            val validPlaylists = shelf.playlists.filterNot { isVideoShelf(it.title, it.subtitle) }

            if (validTracks.isEmpty() && validPlaylists.isEmpty()) return

            if (validTracks.isNotEmpty()) {
                val novelTracks = validTracks.filter { !seenTrackIds.contains(it.videoId) }
                if (novelTracks.isEmpty() && seenTrackIds.isNotEmpty()) return
                seenTitles.add(normTitle)
                validTracks.forEach { seenTrackIds.add(it.videoId) }
                result.add(shelf.copy(tracks = validTracks, playlists = emptyList()))
            } else {
                val novelPlaylists = validPlaylists.filter { !seenPlaylistIds.contains(it.id) }
                if (novelPlaylists.isEmpty() && seenPlaylistIds.isNotEmpty()) return
                seenTitles.add(normTitle)
                validPlaylists.forEach { seenPlaylistIds.add(it.id) }
                result.add(shelf.copy(playlists = validPlaylists, tracks = emptyList()))
            }
        }

        // 1. Language curated shelves (fallback to languagePlaylists if empty)
        val effectiveLanguageShelves = if (languageShelves.isEmpty() && languagePlaylists.isNotEmpty()) {
            listOf(
                InnerTubeShelf(
                    title = "Featured playlists for you",
                    subtitle = if (preferredLanguage.isNotBlank()) "Curated playlists & mixes in $preferredLanguage" else "Curated playlists & mixes",
                    playlists = languagePlaylists
                )
            )
        } else {
            languageShelves
        }

        // Anchor regional preference: first curated language shelf (e.g. Featured playlists in language)
        val firstLangShelf = effectiveLanguageShelves.firstOrNull()
        if (firstLangShelf != null) {
            addShelf(firstLangShelf)
        }

        // 2. Dynamic home feed shelves from InnerTube (e.g. Quick picks, Listen again, Similar to..., Mixed for you, Forgotten favorites)
        homeShelves.forEach { shelf ->
            addShelf(shelf)
        }

        // 3. Remaining language curated shelves (e.g. Brb, Being Nostalgic!, Albums for you, Top songs in language)
        effectiveLanguageShelves.drop(1).forEach { shelf ->
            addShelf(shelf)
        }

        // 4. Dynamic charts shelves
        chartsShelves.forEach { shelf ->
            addShelf(shelf)
        }

        return result
    }

    suspend fun getTrendingTracks(country: String = "IN"): List<TrackEntity> = withContext(Dispatchers.IO) {
        val charts = fetchChartsShelves()
        // Find trending or top songs audio shelf first
        val preferredShelf = charts.find { it.title.contains("Trending", ignoreCase = true) }
            ?: charts.find { it.title.contains("Top Global", ignoreCase = true) }
            ?: charts.firstOrNull { it.tracks.isNotEmpty() }

        if (preferredShelf != null && preferredShelf.tracks.isNotEmpty()) {
            val audioSongs = preferredShelf.tracks
                .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                .map { it.toTrackEntity().copy(isVideo = false) }
            if (audioSongs.isNotEmpty()) {
                return@withContext audioSongs
            }
        }

        for (shelf in charts) {
            val audioTracks = shelf.tracks
                .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                .map { it.toTrackEntity().copy(isVideo = false) }
            if (audioTracks.isNotEmpty()) {
                return@withContext audioTracks
            }
            for (playlist in shelf.playlists) {
                if (isVideoShelf(playlist.title, playlist.subtitle)) continue
                val tracks = innerTube.getPlaylistTracks(playlist.id)
                    .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                    .map { it.toTrackEntity().copy(isVideo = false) }
                if (tracks.isNotEmpty()) {
                    return@withContext tracks
                }
            }
        }
        // Fallback: search pure audio songs
        val fallbackSongs = innerTube.searchSongs("trending audio songs $country", limit = 30)
            .filterNot { it.itemType.equals("Video", ignoreCase = true) }
            .map { it.toTrackEntity().copy(isVideo = false) }
        if (fallbackSongs.isNotEmpty()) {
            return@withContext fallbackSongs
        }
        return@withContext searchOnlineTracks("trending audio songs $country", category = "songs")
            .map { it.copy(isVideo = false) }
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
                val audioTracks = tracks.filterNot { it.itemType.equals("Video", ignoreCase = true) }
                Log.d(TAG, "Successfully fetched ${audioTracks.size} real tracks for playlist/album '$browseId' via InnerTube")
                return@withContext audioTracks.map { it.toTrackEntity().copy(isVideo = false) }
            }
            Log.w(TAG, "No tracks returned for browseId: '$browseId'")
            return@withContext emptyList()
        }

        val cleanQuery = if (language.isNotBlank() && !query.lowercase().contains(language.lowercase())) {
            "$query $language"
        } else {
            query
        }

        return@withContext searchOnlineTracks(cleanQuery)
    }

    suspend fun fetchRealTop50GlobalCharts(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val charts = fetchChartsShelves()
        val topShelf = charts.find { it.title.contains("Top Global", ignoreCase = true) }
            ?: charts.firstOrNull { it.tracks.isNotEmpty() }

        if (topShelf != null && topShelf.tracks.isNotEmpty()) {
            val audioSongs = topShelf.tracks
                .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                .map { it.toTrackEntity().copy(isVideo = false) }
            if (audioSongs.isNotEmpty()) {
                return@withContext audioSongs
            }
        }

        for (shelf in charts) {
            val audioTracks = shelf.tracks
                .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                .map { it.toTrackEntity().copy(isVideo = false) }
            if (audioTracks.isNotEmpty()) {
                return@withContext audioTracks
            }
            for (playlist in shelf.playlists) {
                if (isVideoShelf(playlist.title, playlist.subtitle)) continue
                val tracks = innerTube.getPlaylistTracks(playlist.id)
                    .filterNot { it.itemType.equals("Video", ignoreCase = true) }
                    .map { it.toTrackEntity().copy(isVideo = false) }
                if (tracks.isNotEmpty()) {
                    return@withContext tracks
                }
            }
        }
        val fallbackSongs = innerTube.searchSongs("top 50 global songs", limit = 30)
            .filterNot { it.itemType.equals("Video", ignoreCase = true) }
            .map { it.toTrackEntity().copy(isVideo = false) }
        if (fallbackSongs.isNotEmpty()) {
            return@withContext fallbackSongs
        }
        return@withContext searchOnlineTracks("top 50 global songs", category = "songs")
            .map { it.copy(isVideo = false) }
    }

    fun getPlaylistDescription(playlistId: String): String? = innerTube.getCachedPlaylistDescription(playlistId)
    suspend fun fetchPlaylistDescription(playlistId: String): String? = innerTube.fetchPlaylistDescription(playlistId)

    // ==========================================
    // 2. SEARCH & STREAM RESOLUTION
    // ==========================================
    suspend fun searchOnlineTracks(query: String, category: String = "All"): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = when (category.trim().lowercase()) {
            "all", "songs", "song" -> innerTube.searchSongs(query)
            "videos", "video" -> innerTube.search(query, filter = "EgWKAQIQAWoKEAkQBRAKEAMQBA==")
            else -> innerTube.searchSongs(query)
        }
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

    suspend fun searchAlbums(query: String): List<com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        return@withContext innerTube.searchAlbums(query)
    }

    suspend fun fetchArtistPage(browseId: String): com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null
        return@withContext innerTube.getArtistPage(browseId)
    }

    fun invalidateStreamCache(videoId: String) {
        com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.invalidateCache(videoId)
        streamResolver.invalidateCache(videoId)
    }

    fun clearAllStreamCache() {
        com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.clearAllCache()
        streamResolver.clearAllCache()
    }

    suspend fun resolveStreamUrl(
        videoId: String,
        context: android.content.Context? = null,
        forceRefresh: Boolean = false,
        audioQuality: String? = null
    ): String = getStreamUrl(videoId, context, forceRefresh, audioQuality)

    suspend fun getStreamUrl(
        videoId: String,
        context: android.content.Context? = null,
        forceRefresh: Boolean = false,
        audioQuality: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext ""
        try {
            val effectiveContext = context ?: try { com.akshay.musicplayer.AppContainer.getContext() } catch (_: Exception) { null }
            val effectiveQuality = audioQuality ?: effectiveContext?.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)?.getString("audio_quality", "Medium (160 kbps)")

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

            // 2. If authenticated, try fast authenticated YouTube Music stream resolver directly
            if (streamResolver.hasAuthCookie()) {
                val resolved = streamResolver.resolveAudioStream(videoId, effectiveQuality)
                if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                    Log.d(TAG, "Resolved authenticated audio stream for videoId=$videoId via StreamResolver")
                    com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.cacheStreamUrl(videoId, resolved)
                    return@withContext resolved
                }
            }

            // 3. Prioritize bundled Headless WebView BotGuard extractor (Innertube.js decipher).
            // This produces signed WEB_REMIX streams with valid PO tokens that play continuously
            // in ExoPlayer without 403 Forbidden errors or 1MB chunk limits.
            if (effectiveContext != null) {
                val extracted = com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.extractAudioStream(effectiveContext, videoId)
                if (!extracted.isNullOrBlank() && extracted.startsWith("http")) {
                    Log.d(TAG, "Resolved audio stream via Headless WebView for videoId=$videoId (length=${extracted.length})")
                    return@withContext extracted
                }
            }

            // 4. Fallback to StreamResolver (Zuno multi-client strategies: iOS, Android, TV) if WebView is unavailable
            val resolved = streamResolver.resolveAudioStream(videoId, effectiveQuality)
            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                Log.d(TAG, "Resolved audio stream for videoId=$videoId (quality=$effectiveQuality) via StreamResolver fallback (length=${resolved.length})")
                com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.cacheStreamUrl(videoId, resolved)
                return@withContext resolved
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Stream resolution error for $videoId", e)
        }
        return@withContext ""
    }

    private val trackIdToVideoIdMap = java.util.concurrent.ConcurrentHashMap<Long, String>()

    fun recordVideoId(trackId: Long, videoId: String) {
        if (trackId != 0L && videoId.isNotBlank()) {
            trackIdToVideoIdMap[trackId] = videoId
        }
    }

    fun extractVideoId(track: TrackEntity): String {
        if (track.filePath.startsWith("online:")) {
            val vid = track.filePath.removePrefix("online:").substringBefore("?").substringBefore("&")
            if (vid.isNotBlank() && !vid.contains(" ")) {
                recordVideoId(track.id, vid)
                return vid
            }
        }
        val cached = trackIdToVideoIdMap[track.id]
        if (!cached.isNullOrBlank()) return cached

        if (!track.artworkUrl.isNullOrBlank() && track.artworkUrl.contains("/vi/")) {
            val vid = track.artworkUrl.substringAfter("/vi/").substringBefore("/")
            if (vid.isNotBlank()) {
                recordVideoId(track.id, vid)
                return vid
            }
        }
        if (track.filePath.contains("youtube.com/watch?v=")) {
            val vid = track.filePath.substringAfter("v=").substringBefore("&")
            if (vid.isNotBlank()) {
                recordVideoId(track.id, vid)
                return vid
            }
        }
        if (track.filePath.contains("youtu.be/")) {
            val vid = track.filePath.substringAfter("youtu.be/").substringBefore("?")
            if (vid.isNotBlank()) {
                recordVideoId(track.id, vid)
                return vid
            }
        }
        return ""
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
                try {
                    val artwork = AndroidArtwork()
                    artwork.binaryData = finalJpegBytes
                    artwork.mimeType = "image/jpeg"
                    artwork.pictureType = 3 // Cover (front)
                    try { tag.deleteArtworkField() } catch (_: Exception) {}
                    tag.setField(artwork)
                } catch (artEx: Exception) {
                    Log.w(TAG, "Failed to set artwork via Jaudiotagger in $filePath: ${artEx.message}")
                }
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
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\",\"preview\",\"filler\"]"
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
                    val votes = obj.optInt("votes", 0)
                    if (votes < 0) continue
                    val segArr = obj.optJSONArray("segment") ?: continue
                    val rawCategory = obj.optString("category", "sponsor")
                    if (segArr.length() >= 2) {
                        val startSec = segArr.optDouble(0, 0.0)
                        val endSec = segArr.optDouble(1, 0.0)
                        if (endSec > startSec) {
                            val startMs = (startSec * 1000).toLong()
                            val endMs = (endSec * 1000).toLong()
                            val category = if (rawCategory == "outro" && startMs < 15_000L) "intro" else rawCategory
                            segments.add(
                                SponsorSegment(
                                    startMs = startMs,
                                    endMs = endMs,
                                    category = category
                                )
                            )
                        }
                    }
                }
                return@withContext segments
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "SponsorBlock query failed for $videoId", e)
            return@withContext emptyList()
        }
    }

    // ==========================================
    // 5. LYRICS (LRCLIB)
    // ==========================================
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        language: String = "",
        targetDurationSec: Int = 0
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanTitleForLyrics(title)
        val primaryArtist = getPrimaryArtist(artist)
        val titleBeforeHyphen = if (cleanedTitle.contains(" - ")) stripLanguageBrackets(cleanedTitle.substringBefore(" - ").trim()) else ""
        val titleAfterHyphen = if (cleanedTitle.contains(" - ")) stripLanguageBrackets(cleanedTitle.substringAfter(" - ").trim()) else ""

        Log.d(TAG, "fetchLyrics: title='$title' -> cleanedTitle='$cleanedTitle', before='$titleBeforeHyphen', after='$titleAfterHyphen', primaryArtist='$primaryArtist', dur=${targetDurationSec}s")

        val isLabel = isRecordLabelOrChannel(primaryArtist)

        // Step 1: If title contains " - ", the part after hyphen is frequently the exact song name (e.g. "Movie - Song")
        if (titleAfterHyphen.isNotBlank()) {
            val encAfter = URLEncoder.encode(titleAfterHyphen, "UTF-8")
            if (primaryArtist.isNotBlank() && !isLabel) {
                val encArtist = URLEncoder.encode(primaryArtist, "UTF-8")
                val candidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encAfter&artist_name=$encArtist")
                pickBestLyrics(candidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                    Log.d(TAG, "Lyrics found via titleAfterHyphen ('$titleAfterHyphen') + artist")
                    return@withContext it
                }
            }
            val candidatesAfter = fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encAfter")
            pickBestLyrics(candidatesAfter, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                Log.d(TAG, "Lyrics found via titleAfterHyphen ('$titleAfterHyphen')")
                return@withContext it
            }
        }

        // Step 2: Structured search with cleanedTitle & primaryArtist (only if artist is not a label/channel)
        val encTitle = URLEncoder.encode(stripLanguageBrackets(cleanedTitle), "UTF-8")
        if (primaryArtist.isNotBlank() && !isLabel) {
            val encArtist = URLEncoder.encode(primaryArtist, "UTF-8")
            val candidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encTitle&artist_name=$encArtist")
            pickBestLyrics(candidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                Log.d(TAG, "Lyrics found via structured search (track_name + artist_name)")
                return@withContext it
            }
        }

        // Step 2b: Structured search with cleanedTitle alone (matches manual search behaviour)
        val titleOnlyCandidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encTitle")
        pickBestLyrics(titleOnlyCandidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
            Log.d(TAG, "Lyrics found via cleanedTitle alone ('$cleanedTitle')")
            return@withContext it
        }

        // Step 3: Structured search with titleBeforeHyphen (for Western "Artist - Song" or "Song - Album")
        if (titleBeforeHyphen.isNotBlank() && titleBeforeHyphen != cleanedTitle) {
            val encBefore = URLEncoder.encode(titleBeforeHyphen, "UTF-8")
            val candidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encBefore")
            pickBestLyrics(candidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                Log.d(TAG, "Lyrics found via titleBeforeHyphen ('$titleBeforeHyphen')")
                return@withContext it
            }
        }

        // Step 4: General query search fallback (combined title + artist if artist is not a label)
        if (primaryArtist.isNotBlank() && !isLabel && !cleanedTitle.contains(primaryArtist, ignoreCase = true)) {
            val q1 = "$cleanedTitle $primaryArtist"
            val qCandidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?q=${URLEncoder.encode(q1, "UTF-8")}")
            pickBestLyrics(qCandidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                Log.d(TAG, "Lyrics found via general query '$q1'")
                return@withContext it
            }
        }

        // Step 4b: General query search with cleanedTitle alone
        val qTitleCandidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?q=$encTitle")
        pickBestLyrics(qTitleCandidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
            Log.d(TAG, "Lyrics found via pure title query '$cleanedTitle'")
            return@withContext it
        }

        // Step 5: Direct search with titleAfterHyphen as general query (e.g. searching "Eppudo Ninnu")
        if (titleAfterHyphen.isNotBlank()) {
            val afterCandidates = fetchCandidatesFromUrl("https://lrclib.net/api/search?q=${URLEncoder.encode(titleAfterHyphen, "UTF-8")}")
            pickBestLyrics(afterCandidates, cleanedTitle, titleAfterHyphen, titleBeforeHyphen, targetDurationSec)?.let {
                Log.d(TAG, "Lyrics found via titleAfterHyphen query '$titleAfterHyphen'")
                return@withContext it
            }
        }

        Log.w(TAG, "No lyrics found for '$title' by '$artist'")
        return@withContext null
    }

    private fun tryLrclibGet(trackName: String, artistName: String, durationSec: Int = 0): LyricsData? {
        val encodedTitle = URLEncoder.encode(trackName, "UTF-8")
        val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
        val durationParam = if (durationSec > 0) "&duration=$durationSec" else ""
        val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist$durationParam"
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

    private suspend fun fetchCandidatesFromUrl(url: String): List<JSONObject> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mueso-Android-App/1.2")
            .build()
        for (attempt in 0..1) {
            var shouldRetry = false
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 503 || response.code == 429) {
                        if (attempt == 0) {
                            shouldRetry = true
                            return@use
                        }
                        return emptyList()
                    }
                    if (!response.isSuccessful) return emptyList()
                    val body = response.body?.string() ?: return emptyList()
                    val array = JSONArray(body)
                    val list = ArrayList<JSONObject>(array.length())
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let { list.add(it) }
                    }
                    return list
                }
                if (shouldRetry) {
                    kotlinx.coroutines.delay(500L)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (attempt == 0) {
                    kotlinx.coroutines.delay(300L)
                }
            }
        }
        return emptyList()
    }

    private fun pickBestLyrics(
        candidates: List<JSONObject>,
        targetTitle: String,
        targetAfterHyphen: String = "",
        targetBeforeHyphen: String = "",
        targetDurationSec: Int = 0
    ): LyricsData? {
        if (candidates.isEmpty()) return null

        data class ScoredCandidate(
            val json: JSONObject,
            val score: Int,
            val isSynced: Boolean,
            val durationDiff: Int
        )

        val scored = candidates.mapNotNull { item ->
            val trackName = item.optString("trackName", "")
            if (trackName.isBlank()) return@mapNotNull null
            val syncedLyrics = item.optCleanString("syncedLyrics")
            val plainLyrics = item.optCleanString("plainLyrics")
            val hasSynced = !syncedLyrics.isNullOrBlank() &&
                (syncedLyrics.contains("[0") || syncedLyrics.contains("[1") || syncedLyrics.contains("[:"))
            if (!hasSynced && plainLyrics.isNullOrBlank()) return@mapNotNull null

            val durationSec = item.optDouble("duration", 0.0).toInt()
            val durationDiff = if (targetDurationSec > 0 && durationSec > 0) {
                Math.abs(durationSec - targetDurationSec)
            } else 0

            val titleScore = maxOf(
                calculateTitleMatchScore(trackName, targetTitle),
                if (targetAfterHyphen.isNotBlank()) calculateTitleMatchScore(trackName, targetAfterHyphen) else 0,
                if (targetBeforeHyphen.isNotBlank()) calculateTitleMatchScore(trackName, targetBeforeHyphen) else 0
            )
            if (titleScore == 0) return@mapNotNull null

            val syncBonus = if (hasSynced) 50 else 0
            val durPenalty = (durationDiff / 2).coerceAtMost(25)
            val totalScore = titleScore + syncBonus - durPenalty

            ScoredCandidate(item, totalScore, hasSynced, durationDiff)
        }

        val best = scored.maxWithOrNull(
            compareBy<ScoredCandidate> { it.score }
                .thenBy { it.isSynced }
                .thenByDescending { -it.durationDiff }
        )?.json ?: return null

        val selectedLyrics = best.optCleanString("syncedLyrics") ?: best.optCleanString("plainLyrics") ?: return null
        return LyricsData(lines = LrcParser.parse(selectedLyrics), rawText = selectedLyrics)
    }

    suspend fun searchLrclibCandidates(query: String, targetDurationSec: Int = 0): List<LrclibSearchResultItem> = withContext(Dispatchers.IO) {
        val cleanQuery = cleanTitleForLyrics(query)
        val queryBeforeHyphen = if (cleanQuery.contains(" - ")) stripLanguageBrackets(cleanQuery.substringBefore(" - ").trim()) else ""
        val queryAfterHyphen = if (cleanQuery.contains(" - ")) stripLanguageBrackets(cleanQuery.substringAfter(" - ").trim()) else ""

        val rawItems = mutableListOf<JSONObject>()

        // 1. Search by track_name
        val encClean = URLEncoder.encode(stripLanguageBrackets(cleanQuery), "UTF-8")
        rawItems.addAll(fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encClean"))

        if (queryAfterHyphen.isNotBlank() && queryAfterHyphen != cleanQuery) {
            val encAfter = URLEncoder.encode(queryAfterHyphen, "UTF-8")
            rawItems.addAll(fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encAfter"))
        }

        if (queryBeforeHyphen.isNotBlank() && queryBeforeHyphen != cleanQuery) {
            val encBefore = URLEncoder.encode(queryBeforeHyphen, "UTF-8")
            rawItems.addAll(fetchCandidatesFromUrl("https://lrclib.net/api/search?track_name=$encBefore"))
        }

        // 2. Search by general query
        rawItems.addAll(fetchCandidatesFromUrl("https://lrclib.net/api/search?q=$encClean"))
        if (query.trim() != cleanQuery) {
            val encRaw = URLEncoder.encode(query.trim(), "UTF-8")
            rawItems.addAll(fetchCandidatesFromUrl("https://lrclib.net/api/search?q=$encRaw"))
        }

        val seenIds = mutableSetOf<Long>()
        val list = mutableListOf<LrclibSearchResultItem>()

        for (item in rawItems) {
            val id = item.optLong("id", 0L)
            if (id != 0L && !seenIds.add(id)) continue

            val trackName = item.optString("trackName", "")
            val artistName = item.optString("artistName", "")
            val albumName = item.optString("albumName", "")
            val durationSeconds = item.optDouble("duration", 0.0).toInt()
            val syncedLyrics = item.optCleanString("syncedLyrics")
            val plainLyrics = item.optCleanString("plainLyrics")
            val isSynced = !syncedLyrics.isNullOrBlank() &&
                (syncedLyrics.contains("[0") || syncedLyrics.contains("[1") || syncedLyrics.contains("[:"))

            if (trackName.isNotBlank() && (isSynced || !plainLyrics.isNullOrBlank())) {
                list.add(
                    LrclibSearchResultItem(
                        id = if (id != 0L) id else list.size.toLong() + 1,
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

        return@withContext list.sortedWith(
            compareBy<LrclibSearchResultItem> { !it.isSynced }
                .thenByDescending {
                    maxOf(
                        calculateTitleMatchScore(it.trackName, cleanQuery),
                        if (queryAfterHyphen.isNotBlank()) calculateTitleMatchScore(it.trackName, queryAfterHyphen) else 0,
                        if (queryBeforeHyphen.isNotBlank()) calculateTitleMatchScore(it.trackName, queryBeforeHyphen) else 0
                    )
                }
                .thenBy { if (targetDurationSec > 0 && it.durationSeconds > 0) Math.abs(it.durationSeconds - targetDurationSec) else 9999 }
        )
    }

    // ==========================================
    // 6. UTILITY HELPERS
    // ==========================================
    fun getHighResArtworkUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return when {
            rawUrl.contains("=w") || rawUrl.contains("=s") -> {
                rawUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1200-h1200-l90-rj")
                    .replace(Regex("=s\\d+.*"), "=s1200")
            }
            rawUrl.contains("/vi/") -> {
                val videoId = rawUrl.substringAfter("/vi/").substringBefore("/")
                if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" else rawUrl
            }
            else -> rawUrl
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
        val cleanedTitle = cleanTitleForLyrics(title)
        val cleanedArtist = cleanArtistForLyrics(artist)
        return Pair(cleanedTitle, cleanedArtist)
    }
}
