package com.akshay.musicplayer.data.remote

import android.util.Log
import com.akshay.musicplayer.domain.models.TrackEntity

class OnlineMusicRepository {

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
        Log.d("MUESO_TRENDING", "getTrendingTracks called for country=$country via yt-dlp")
        return searchOnlineTracks("trending songs $country")
    }

    suspend fun getStreamUrl(videoId: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

    suspend fun searchOnlineTracks(query: String): List<TrackEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                TrackEntity(
                    id = videoId.hashCode().toLong(),
                    title = title,
                    artist = artist,
                    album = "Online Search",
                    duration = duration,
                    albumId = 0L,
                    filePath = "online:$videoId",
                    artworkUrl = thumb,
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
}
