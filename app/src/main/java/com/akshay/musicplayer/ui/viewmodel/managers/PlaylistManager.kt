package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.SharedPreferences
import android.util.Log
import com.akshay.musicplayer.data.db.OnlinePlaylistDao
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity
import com.akshay.musicplayer.data.db.PlaylistDao
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.data.db.PlaylistTrackCrossRef
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PlaylistManager(
    private val playlistDao: PlaylistDao,
    private val onlinePlaylistDao: OnlinePlaylistDao,
    private val onlineRepository: OnlineMusicRepository,
    private val sharedPreferences: SharedPreferences,
    private val coroutineScope: CoroutineScope,
    private val markDirty: () -> Unit,
    private val getLocalTracks: () -> List<TrackEntity>
) {
    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _onlinePlaylists = MutableStateFlow<List<OnlinePlaylistEntity>>(emptyList())
    val onlinePlaylists: StateFlow<List<OnlinePlaylistEntity>> = _onlinePlaylists.asStateFlow()

    init {
        // Clear any stale/corrupted curated cache
        val allKeys = sharedPreferences.all.keys
        val cacheKeys = allKeys.filter { it.startsWith("curated_cache_") }
        if (cacheKeys.isNotEmpty()) {
            val editor = sharedPreferences.edit()
            cacheKeys.forEach { editor.remove(it) }
            editor.apply()
            Log.d("MUESO_CACHE", "Purged ${cacheKeys.size} stale curated playlist cache entries")
        }

        loadPlaylists()
        loadOnlinePlaylists()
    }

    private fun loadPlaylists() {
        coroutineScope.launch {
            playlistDao.getAllPlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    private fun loadOnlinePlaylists() {
        coroutineScope.launch {
            onlinePlaylistDao.getAllOnlinePlaylists().collect { list ->
                _onlinePlaylists.value = list
            }
        }
    }

    fun touchPlaylist(playlistId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylistTimestamp(playlistId)
        }
    }

    fun touchOnlinePlaylist(playlistId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.updateOnlinePlaylistTimestamp(playlistId)
        }
    }

    fun createPlaylist(name: String) {
        coroutineScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            playlistDao.renamePlaylist(playlistId, newName)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            val maxIndex = playlistDao.getMaxOrderIndex(playlistId)
            playlistDao.insertTrackIntoPlaylist(
                PlaylistTrackCrossRef(playlistId, trackId, maxIndex + 1)
            )
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        coroutineScope.launch(Dispatchers.IO) {
            var maxIndex = playlistDao.getMaxOrderIndex(playlistId)
            trackIds.forEach { trackId ->
                maxIndex++
                playlistDao.insertTrackIntoPlaylist(
                    PlaylistTrackCrossRef(playlistId, trackId, maxIndex)
                )
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            val crossRefs = playlistDao.getPlaylistTracksSync(playlistId).toMutableList()
            val updated = crossRefs.mapIndexed { index, ref -> ref.copy(orderIndex = index) }
            playlistDao.updatePlaylistTracks(updated)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            val crossRefs = playlistDao.getPlaylistTracksSync(playlistId).toMutableList()
            if (fromIndex in crossRefs.indices && toIndex in crossRefs.indices) {
                val item = crossRefs.removeAt(fromIndex)
                crossRefs.add(toIndex, item)
                val updated = crossRefs.mapIndexed { index, ref -> ref.copy(orderIndex = index) }
                playlistDao.updatePlaylistTracks(updated)
            }
        }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> {
        return playlistDao.getTrackIdsForPlaylist(playlistId).map { trackIds ->
            val localTracks = getLocalTracks()
            trackIds.mapNotNull { trackId ->
                localTracks.find { it.id == trackId }
            }
        }
    }

    // Online Playlist Logic

    fun createOnlinePlaylist(name: String, description: String? = null) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.insertOnlinePlaylist(
                OnlinePlaylistEntity(name = name, description = description)
            )
            markDirty()
        }
    }

    fun deleteOnlinePlaylist(playlistId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.deleteOnlinePlaylist(playlistId)
            onlinePlaylistDao.clearOnlinePlaylistTracks(playlistId)
            markDirty()
        }
    }

    fun renameOnlinePlaylist(playlistId: Long, newName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.renameOnlinePlaylist(playlistId, newName)
            markDirty()
        }
    }

    fun updateCachedPlaylistArtwork(playlistId: Long) {
        val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
        val firstArtwork = tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
        onlinePlaylistDao.updateOnlinePlaylistArtwork(playlistId, firstArtwork)
    }

    fun refreshAllPlaylistArtworks() {
        coroutineScope.launch(Dispatchers.IO) {
            val playlists = onlinePlaylistDao.getAllOnlinePlaylistsSync()
            playlists.forEach { p ->
                val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                val firstArtwork = tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                onlinePlaylistDao.updateOnlinePlaylistArtwork(p.id, firstArtwork)
            }
        }
    }

    fun addTrackToOnlinePlaylist(playlistId: Long, track: TrackEntity) {
        coroutineScope.launch(Dispatchers.IO) {
            val existing = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
            val nextOrder = existing.size
            onlinePlaylistDao.insertOnlineTrack(
                OnlinePlaylistTrackEntity(
                    onlinePlaylistId = playlistId,
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    artworkUrl = track.artworkUrl,
                    filePath = track.filePath,
                    duration = track.duration,
                    orderIndex = nextOrder
                )
            )
            updateCachedPlaylistArtwork(playlistId)
            markDirty()
        }
    }

    fun addTracksToOnlinePlaylist(playlistId: Long, tracks: List<TrackEntity>) {
        coroutineScope.launch(Dispatchers.IO) {
            val existing = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
            var nextOrder = existing.size
            tracks.forEach { track ->
                onlinePlaylistDao.insertOnlineTrack(
                    OnlinePlaylistTrackEntity(
                        onlinePlaylistId = playlistId,
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        filePath = track.filePath,
                        duration = track.duration,
                        orderIndex = nextOrder++
                    )
                )
            }
            updateCachedPlaylistArtwork(playlistId)
            markDirty()
        }
    }

    fun updateOnlinePlaylistDetails(playlistId: Long, name: String, description: String) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.updateOnlinePlaylistDetails(playlistId, name, description)
            markDirty()
        }
    }

    fun moveTrackInOnlinePlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId).toMutableList()
            if (fromIndex in tracks.indices && toIndex in tracks.indices) {
                val item = tracks.removeAt(fromIndex)
                tracks.add(toIndex, item)
                val updated = tracks.mapIndexed { index, track -> track.copy(orderIndex = index) }
                onlinePlaylistDao.updateOnlinePlaylistTracks(updated)
                updateCachedPlaylistArtwork(playlistId)
                markDirty()
            }
        }
    }

    fun removeTrackFromOnlinePlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.removeOnlineTrack(playlistId, trackId)
            updateCachedPlaylistArtwork(playlistId)
            markDirty()
        }
    }

    fun getOnlinePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> {
        return onlinePlaylistDao.getOnlinePlaylistTracks(playlistId).map { tracks ->
            tracks.map {
                TrackEntity(
                    id = it.trackId,
                    title = it.title,
                    artist = it.artist,
                    album = "Online Playlist Track",
                    duration = it.duration,
                    albumId = 0L,
                    filePath = it.filePath,
                    artworkUrl = it.artworkUrl
                )
            }
        }
    }

    fun clearCuratedCache() {
        val editor = sharedPreferences.edit()
        sharedPreferences.all.keys.filter { it.startsWith("curated_cache_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
        Log.d("MUESO_CACHE", "Cleared all curated playlist caches")
    }

    suspend fun getCuratedPlaylistTracks(query: String): List<TrackEntity> {
        val isBrowse = query.startsWith("browse:")
        val preferredLanguage = if (isBrowse) "" else (sharedPreferences.getString("preferred_language", "Telugu") ?: "Telugu")
        val cacheKey = if (isBrowse) {
            "curated_cache_" + query.lowercase().replace(Regex("[^a-z0-9]"), "_")
        } else {
            "curated_cache_" + preferredLanguage.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + query.lowercase().replace(Regex("[^a-z0-9]"), "_")
        }
        val lastFetchedTime = sharedPreferences.getLong("${cacheKey}_time", 0L)
        val cachedJson = sharedPreferences.getString("${cacheKey}_json", null)
        val currentTime = System.currentTimeMillis()

        val isCacheValid = (currentTime - lastFetchedTime < 86_400_000L) && !cachedJson.isNullOrBlank()

        if (isCacheValid) {
            try {
                val jsonArr = JSONArray(cachedJson)
                val cachedTracks = mutableListOf<TrackEntity>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    cachedTracks.add(
                        TrackEntity(
                            id = obj.getLong("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            album = "Curated Playlist",
                            duration = obj.getLong("duration"),
                            albumId = 0L,
                            filePath = obj.getString("filePath"),
                            artworkUrl = obj.optString("artworkUrl", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
                if (cachedTracks.isNotEmpty()) {
                    Log.d("MUESO_CACHE", "Serving curated playlist '$query' from 24-hr local cache (0ms delay, ${cachedTracks.size} tracks)")
                    return cachedTracks
                }
            } catch (e: Exception) {
                Log.w("MUESO_CACHE", "Failed to parse 24-hr cache for '$query'", e)
            }
        }

        Log.d("MUESO_CACHE", "Fetching fresh curated tracks for '$query' from API...")
        val freshTracks = if (query.contains("top 50 global", ignoreCase = true)) {
            onlineRepository.fetchRealTop50GlobalCharts()
        } else {
            onlineRepository.fetchCuratedPlaylistTracks(query, preferredLanguage)
        }

        if (freshTracks.isNotEmpty()) {
            try {
                val jsonArr = JSONArray()
                for (t in freshTracks) {
                    val obj = JSONObject()
                    obj.put("id", t.id)
                    obj.put("title", t.title)
                    obj.put("artist", t.artist)
                    obj.put("filePath", t.filePath)
                    obj.put("artworkUrl", t.artworkUrl ?: "")
                    obj.put("duration", t.duration)
                    jsonArr.put(obj)
                }
                sharedPreferences.edit()
                    .putLong("${cacheKey}_time", currentTime)
                    .putString("${cacheKey}_json", jsonArr.toString())
                    .apply()
                Log.d("MUESO_CACHE", "Saved ${freshTracks.size} tracks to 24-hr cache for '$query'")
            } catch (e: Exception) {
                Log.w("MUESO_CACHE", "Error saving 24-hr cache for '$query'", e)
            }
        }
        return freshTracks
    }

    suspend fun exportPlaylistsToJson(context: android.content.Context): String? = withContext(Dispatchers.IO) {
        try {
            val playlists = onlinePlaylistDao.getAllOnlinePlaylistsSync()
            val rootArray = JSONArray()
            playlists.forEach { p ->
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("name", p.name)
                pObj.put("description", p.description ?: "")
                pObj.put("artworkUrl", p.artworkUrl ?: "")
                pObj.put("dateCreated", p.dateCreated)

                val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                val tracksArr = JSONArray()
                tracks.forEach { t ->
                    val tObj = JSONObject()
                    tObj.put("trackId", t.trackId)
                    tObj.put("title", t.title)
                    tObj.put("artist", t.artist)
                    tObj.put("artworkUrl", t.artworkUrl ?: "")
                    tObj.put("filePath", t.filePath)
                    tObj.put("duration", t.duration)
                    tObj.put("orderIndex", t.orderIndex)
                    tracksArr.put(tObj)
                }
                pObj.put("tracks", tracksArr)
                rootArray.put(pObj)
            }
            val jsonString = rootArray.toString(2)

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetDir = if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) downloadsDir else (context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            val file = java.io.File(targetDir, "mueso_playlists_backup.json")
            file.writeText(jsonString)
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MUESO_EXPORT", "Failed to export playlists JSON", e)
            null
        }
    }

    suspend fun importPlaylistsFromJson(context: android.content.Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootArray = JSONArray(jsonString)
            val existingPlaylists = onlinePlaylistDao.getAllOnlinePlaylistsSync()
            for (i in 0 until rootArray.length()) {
                val pObj = rootArray.getJSONObject(i)
                val name = pObj.getString("name")
                val desc = pObj.optString("description", "").takeIf { it.isNotBlank() }
                val artwork = pObj.optString("artworkUrl", "").takeIf { it.isNotBlank() }

                val existing = existingPlaylists.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val playlistId = if (existing != null) {
                    if (desc != null && existing.description != desc) {
                        onlinePlaylistDao.updateOnlinePlaylistDetails(existing.id, existing.name, desc)
                    }
                    existing.id
                } else {
                    onlinePlaylistDao.insertOnlinePlaylist(
                        OnlinePlaylistEntity(
                            name = name,
                            description = desc,
                            artworkUrl = artwork
                        )
                    )
                }

                val tracksArr = pObj.optJSONArray("tracks")
                if (tracksArr != null) {
                    val existingTracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
                    val existingTrackIds = existingTracks.map { it.trackId }.toSet()

                    for (j in 0 until tracksArr.length()) {
                        val tObj = tracksArr.getJSONObject(j)
                        val tId = tObj.optLong("trackId", System.currentTimeMillis() + j)
                        if (!existingTrackIds.contains(tId)) {
                            onlinePlaylistDao.insertOnlineTrack(
                                OnlinePlaylistTrackEntity(
                                    onlinePlaylistId = playlistId,
                                    trackId = tId,
                                    title = tObj.getString("title"),
                                    artist = tObj.optString("artist", "Unknown Artist"),
                                    artworkUrl = tObj.optString("artworkUrl", "").takeIf { it.isNotBlank() },
                                    filePath = tObj.getString("filePath"),
                                    duration = tObj.optLong("duration", 0L),
                                    orderIndex = tObj.optInt("orderIndex", j)
                                )
                            )
                        }
                    }
                }
                updateCachedPlaylistArtwork(playlistId)
            }
            markDirty()
            true
        } catch (e: Exception) {
            Log.e("MUESO_IMPORT", "Failed to import playlists JSON", e)
            false
        }
    }
}
