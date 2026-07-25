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
                markDirty()
            }
        }
    }

    fun removeTrackFromOnlinePlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.removeOnlineTrack(playlistId, trackId)
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

    suspend fun getCuratedPlaylistTracks(query: String): List<TrackEntity> {
        val cacheKey = "curated_cache_" + query.lowercase().replace(Regex("[^a-z0-9]"), "_")
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
            onlineRepository.searchOnlineTracks(query)
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
}
