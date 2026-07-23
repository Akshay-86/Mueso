package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.domain.models.LrcParser
import com.akshay.musicplayer.domain.models.LyricsData
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

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

        val noiseRegex = Regex("(?i)(\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(official.*?\\)|\\[official.*?\\]|\\(lyrical.*?\\)|\\[lyrical.*?\\]|full video song|video song|lyrical video|official video|full video|audio song|official audio|visualizer|\\(hd\\)|\\[hd\\])", RegexOption.IGNORE_CASE)
        cTitle = noiseRegex.replace(cTitle, "").trim()
        cArtist = noiseRegex.replace(cArtist, "").trim()

        cTitle = cTitle.replace("\"", "").replace("'", "").trim()
        cArtist = cArtist.replace("\"", "").replace("'", "").trim()

        return Pair(cTitle, cArtist)
    }

    suspend fun fetchLyrics(title: String, artist: String): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val (cleanTitle, cleanArtist) = cleanTitleAndArtist(title, artist)
            Log.d("MUESO_LYRICS", "Original ('$title', '$artist') -> Cleaned ('$cleanTitle', '$cleanArtist')")

            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://verome-api.deno.dev/api/lyrics?title=$encTitle&artist=$encArtist"
            Log.d("MUESO_LYRICS", "Fetching lyrics from Verome API: $url")
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
                            Log.d("MUESO_LYRICS", "Successfully parsed ${parsedLines.size} synced LRC lines for '$cleanTitle'")
                            return@withContext LyricsData(lines = parsedLines)
                        } else if (plainText.isNotBlank()) {
                            Log.d("MUESO_LYRICS", "Plain lyrics found for '$cleanTitle'")
                            return@withContext LyricsData(rawText = plainText)
                        }
                    }
                } else {
                    Log.w("MUESO_LYRICS", "Lyrics request failed with code: ${response.code}")
                }
            }

            // Fallback search with title only
            val fallbackUrl = "https://verome-api.deno.dev/api/lyrics?title=$encTitle"
            val fallbackRequest = Request.Builder()
                .url(fallbackUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(fallbackRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val syncedText = jsonObj.optString("syncedLyrics", "")
                        val plainText = jsonObj.optString("plainLyrics", "")

                        if (syncedText.isNotBlank()) {
                            val parsedLines = LrcParser.parse(syncedText)
                            Log.d("MUESO_LYRICS", "Successfully parsed ${parsedLines.size} synced LRC lines (fallback) for '$cleanTitle'")
                            return@withContext LyricsData(lines = parsedLines)
                        } else if (plainText.isNotBlank()) {
                            Log.d("MUESO_LYRICS", "Plain lyrics found (fallback) for '$cleanTitle'")
                            return@withContext LyricsData(rawText = plainText)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("MUESO_LYRICS", "Error fetching lyrics from Verome API for $title", e)
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

            val tracks = pyList.mapNotNull { pyObj ->
                val videoId = pyObj.callAttr("get", "videoId")?.toString() ?: return@mapNotNull null
                val title = pyObj.callAttr("get", "title")?.toString() ?: "Unknown Title"
                val artist = pyObj.callAttr("get", "artist")?.toString() ?: "Unknown Artist"
                val duration = (pyObj.callAttr("get", "duration")?.toLong() ?: 0L) * 1000L
                val thumb = pyObj.callAttr("get", "thumbnail")?.toString()
                val highresThumb = getHighResArtworkUrl(thumb)
                TrackEntity(
                    id = videoId.hashCode().toLong(),
                    title = title,
                    artist = artist,
                    album = "Online Search",
                    duration = duration,
                    albumId = 0L,
                    filePath = "online:$videoId",
                    artworkUrl = highresThumb,
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

    private fun getHighResArtworkUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrEmpty()) return null
        var url = rawUrl
        if (url.contains("yt3.googleusercontent.com") || url.contains("ggpht.com")) {
            url = url.replace(Regex("=w\\d+-h\\d+.*"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1080")
        }
        if (url.contains("i.ytimg.com/vi/")) {
            url = url.replace("hqdefault.jpg", "hq720.jpg")
                .replace("sddefault.jpg", "hq720.jpg")
                .replace("mqdefault.jpg", "hq720.jpg")
                .replace("maxresdefault.jpg", "hq720.jpg")
                .replace("default.jpg", "hq720.jpg")
        }
        return url
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
