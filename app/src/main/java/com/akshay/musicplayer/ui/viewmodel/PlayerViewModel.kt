package com.akshay.musicplayer.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.domain.usecase.GetLocalTracksUseCase
import com.akshay.musicplayer.media.player.MediaPlayerController
import com.akshay.musicplayer.media.player.PlayerEvent
import com.akshay.musicplayer.ui.components.SleepTimerMode
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.state.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.LyricsData
enum class LyricsFetchStatus {
    IDLE,
    FETCHING,
    FOUND,
    NOT_FOUND
}

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isDownloaded: Boolean = false,
    val error: String? = null
)

class PlayerViewModel(
    private val getLocalTracksUseCase: GetLocalTracksUseCase,
    private val mediaPlayerController: MediaPlayerController,
    private val playlistDao: com.akshay.musicplayer.data.db.PlaylistDao,
    private val onlinePlaylistDao: com.akshay.musicplayer.data.db.OnlinePlaylistDao,
    private val sharedPreferences: android.content.SharedPreferences
) : ViewModel() {

    private val onlineRepository = OnlineMusicRepository()

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _onlineUiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val onlineUiState: StateFlow<PlayerUiState> = _onlineUiState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _offlineLibraryTab = MutableStateFlow(sharedPreferences.getInt("offline_library_tab", 0))
    val offlineLibraryTab: StateFlow<Int> = _offlineLibraryTab.asStateFlow()

    // Repeat mode: 0 = off, 1 = all, 2 = one
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Sleep timer state
    private val _activeSleepMode = MutableStateFlow<SleepTimerMode?>(null)
    val activeSleepMode: StateFlow<SleepTimerMode?> = _activeSleepMode.asStateFlow()

    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft: StateFlow<Int?> = _sleepTimerMinutesLeft.asStateFlow()

    private val _sleepAfterSongId = MutableStateFlow<Long?>(null)
    val sleepAfterSongId: StateFlow<Long?> = _sleepAfterSongId.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var previousTrackId: Long? = null

    // Sleep timer sheet visibility
    private val _showSleepTimerSheet = MutableStateFlow(false)
    val showSleepTimerSheet: StateFlow<Boolean> = _showSleepTimerSheet.asStateFlow()

    // Queue sheet visibility
    private val _showQueueSheet = MutableStateFlow(false)
    val showQueueSheet: StateFlow<Boolean> = _showQueueSheet.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val activeQueue: StateFlow<List<TrackEntity>> = _activeQueue.asStateFlow()

    val currentTrackIndexState: StateFlow<Int> = combine(_playbackState, _activeQueue) { state, queue ->
        val currentTrackId = state.currentTrackId ?: return@combine 0
        val index = queue.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
        Log.d("MUESO_SYNC", "ViewModel currentTrackIndexState: calculated index $index for track $currentTrackId")
        index
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var currentTracks: List<TrackEntity> = emptyList()
        set(value) {
            field = value
            _activeQueue.value = value
        }
    // Guard against re-entrant play calls during async IPC transitions
    private var lastRequestedTrackId: Long? = null

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackEntity>>(emptyList())
    val searchResults: StateFlow<List<TrackEntity>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            _isSearchingOnline.value = false
            _searchResults.value = emptyList()
            return
        }

        _isSearchingOnline.value = true
        val localMatches = currentTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        _searchResults.value = localMatches

        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val onlineResults = onlineRepository.searchOnlineTracks(query.trim())
                val localIds = localMatches.map { it.id }.toSet()
                _searchResults.value = localMatches + onlineResults.filter { it.id !in localIds }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun getSearchResults(): List<TrackEntity> = _searchResults.value

    /** Read the restored track index synchronously — used for initial pager page */
    fun getRestoredTrackIndex(): Int {
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        if (lastTrackId == -1L) return 0
        return currentTracks.indexOfFirst { it.id == lastTrackId }.takeIf { it >= 0 } ?: 0
    }

    private fun initRestoredTrackPreview() {
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        if (lastTrackId != -1L) {
            val title = sharedPreferences.getString("last_track_title", "") ?: ""
            val artist = sharedPreferences.getString("last_track_artist", "") ?: ""
            val filePath = sharedPreferences.getString("last_track_filepath", "") ?: ""
            val artworkUrl = sharedPreferences.getString("last_track_artwork_url", null)
            val duration = sharedPreferences.getLong("last_track_duration", 0L)
            if (title.isNotBlank()) {
                val previewTrack = TrackEntity(
                    id = lastTrackId,
                    title = title,
                    artist = artist,
                    album = "Last Played",
                    duration = duration,
                    albumId = 0L,
                    filePath = filePath,
                    artworkUrl = artworkUrl
                )
                currentTracks = listOf(previewTrack)
            }
        }
    }
    
    private val _playlists = MutableStateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>> = _playlists.asStateFlow()

    init {
        initRestoredTrackPreview()
        loadPlaylists()
        loadOnlinePlaylists()
        observePlaybackState()
        observeMediaEvents()
    }

    private val _onlinePlaylists = MutableStateFlow<List<com.akshay.musicplayer.data.db.OnlinePlaylistEntity>>(emptyList())
    val onlinePlaylists: StateFlow<List<com.akshay.musicplayer.data.db.OnlinePlaylistEntity>> = _onlinePlaylists.asStateFlow()

    private fun loadOnlinePlaylists() {
        viewModelScope.launch {
            onlinePlaylistDao.getAllOnlinePlaylists().collect { list ->
                _onlinePlaylists.value = list
            }
        }
    }

    fun createOnlinePlaylist(name: String, description: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.insertOnlinePlaylist(
                com.akshay.musicplayer.data.db.OnlinePlaylistEntity(name = name, description = description)
            )
            markDirty()
        }
    }

    fun deleteOnlinePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.deleteOnlinePlaylist(playlistId)
            onlinePlaylistDao.clearOnlinePlaylistTracks(playlistId)
            markDirty()
        }
    }

    fun renameOnlinePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.renameOnlinePlaylist(playlistId, newName)
            markDirty()
        }
    }

    fun addTrackToOnlinePlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
            val nextOrder = existing.size
            onlinePlaylistDao.insertOnlineTrack(
                com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
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

    private val _isPlaylistContext = MutableStateFlow(false)
    val isPlaylistContext: StateFlow<Boolean> = _isPlaylistContext.asStateFlow()

    private val _playlistTrackCount = MutableStateFlow(0)
    val playlistTrackCount: StateFlow<Int> = _playlistTrackCount.asStateFlow()

    fun moveTrackInOnlinePlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.removeOnlineTrack(playlistId, trackId)
            markDirty()
        }
    }

    fun getOnlinePlaylistTracks(playlistId: Long): kotlinx.coroutines.flow.Flow<List<TrackEntity>> {
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

        // 24 Hours in milliseconds = 86,400,000 ms
        val isCacheValid = (currentTime - lastFetchedTime < 86_400_000L) && !cachedJson.isNullOrBlank()

        if (isCacheValid) {
            try {
                val jsonArr = org.json.JSONArray(cachedJson)
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

        // Fetch fresh tracks from open-source API (iTunes Top 50 / open search)
        Log.d("MUESO_CACHE", "Fetching fresh curated tracks for '$query' from API...")
        val freshTracks = if (query.contains("top 50 global", ignoreCase = true)) {
            onlineRepository.fetchRealTop50GlobalCharts()
        } else {
            onlineRepository.searchOnlineTracks(query)
        }

        if (freshTracks.isNotEmpty()) {
            try {
                val jsonArr = org.json.JSONArray()
                for (t in freshTracks) {
                    val obj = org.json.JSONObject()
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

    fun playOnlinePlaylist(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        _isPlaylistContext.value = true
        _playlistTrackCount.value = tracks.size
        playQueue(tracks, startIndex)
    }

    private suspend fun resolveTrack(track: TrackEntity): TrackEntity {
        return if (track.filePath.startsWith("online:")) {
            val queryOrId = track.filePath.removePrefix("online:")
            val videoId = if (queryOrId.length == 11 && !queryOrId.contains(" ")) {
                queryOrId
            } else {
                val searchResults = onlineRepository.searchOnlineTracks(queryOrId)
                searchResults.firstOrNull()?.filePath?.removePrefix("online:") ?: ""
            }
            if (videoId.isNotBlank()) {
                val streamUrl = onlineRepository.getStreamUrl(videoId)
                val artwork = track.artworkUrl ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                track.copy(filePath = streamUrl, artworkUrl = artwork)
            } else {
                track
            }
        } else {
            track
        }
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(com.akshay.musicplayer.data.db.PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.renamePlaylist(playlistId, newName)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxIndex = playlistDao.getMaxOrderIndex(playlistId)
            playlistDao.insertTrackIntoPlaylist(
                com.akshay.musicplayer.data.db.PlaylistTrackCrossRef(playlistId, trackId, maxIndex + 1)
            )
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            // Re-order remaining tracks to avoid gaps
            val crossRefs = playlistDao.getPlaylistTracksSync(playlistId).toMutableList()
            val updated = crossRefs.mapIndexed { index, ref -> ref.copy(orderIndex = index) }
            playlistDao.updatePlaylistTracks(updated)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
            val trackMap = currentTracks.associateBy { it.id }
            trackIds.mapNotNull { trackMap[it] }
        }
    }

    private var isLoaded = false

    fun loadLocalTracks(forceReload: Boolean = false) {
        if (isLoaded && !forceReload) return
        isLoaded = true
        
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            getLocalTracksUseCase().onSuccess { tracks ->
                val isOnline = sharedPreferences.getBoolean("last_track_is_online", false)
                if (!isOnline && tracks.isNotEmpty() && currentTracks.isEmpty()) {
                    currentTracks = tracks
                }

                if (tracks.isEmpty()) {
                    _uiState.value = PlayerUiState.Empty
                } else {
                    _uiState.value = PlayerUiState.Success(tracks)

                    if (!isOnline) {
                        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
                        val lastPosition = sharedPreferences.getLong("last_position", 0L)
                        if (lastTrackId != -1L) {
                            val index = tracks.indexOfFirst { it.id == lastTrackId }.takeIf { it >= 0 } ?: 0
                            mediaPlayerController.restoreQueue(tracks, index, lastPosition)
                        }
                    }
                }
            }.onFailure { exception ->
                _uiState.value = PlayerUiState.Error(exception.message ?: "Unknown error")
            }
        }
    }

    private fun isNetworkAvailable(context: android.content.Context): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private var restorationJob: Job? = null

    private fun cancelRestoration() {
        if (restorationJob?.isActive == true) {
            Log.d("MUESO_RESTORE", "Cancelling background restoration because user initiated playback")
            restorationJob?.cancel()
            restorationJob = null
        }
    }

    fun restoreLastPlaybackStateOrOffline(context: android.content.Context? = null) {
        val isOnline = sharedPreferences.getBoolean("last_track_is_online", false)
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        val lastPosition = sharedPreferences.getLong("last_position", 0L)
        val hasNet = context?.let { isNetworkAvailable(it) } ?: true

        // Always load local device tracks into _uiState (Offline Library Tab)
        loadLocalTracks(forceReload = true)

        // If last played track was online and network is available, restore online playback state & queue
        if (isOnline && lastTrackId != -1L && hasNet) {
            val isPlaylist = sharedPreferences.getBoolean("last_is_playlist_context", false)
            val savedCount = sharedPreferences.getInt("last_playlist_track_count", 0)
            val queueJson = sharedPreferences.getString("last_playlist_queue_json", null)

            if (isPlaylist && !queueJson.isNullOrBlank()) {
                try {
                    val jsonArr = org.json.JSONArray(queueJson)
                    val restoredPlaylistTracks = mutableListOf<TrackEntity>()
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        restoredPlaylistTracks.add(
                            TrackEntity(
                                id = obj.getLong("id"),
                                title = obj.getString("title"),
                                artist = obj.getString("artist"),
                                album = "Online Playlist Track",
                                duration = obj.getLong("duration"),
                                albumId = 0L,
                                filePath = obj.getString("filePath"),
                                artworkUrl = obj.optString("artworkUrl", "").takeIf { it.isNotBlank() }
                            )
                        )
                    }

                    if (restoredPlaylistTracks.isNotEmpty()) {
                        val startIndex = restoredPlaylistTracks.indexOfFirst { it.id == lastTrackId }.coerceAtLeast(0)
                        restorationJob = viewModelScope.launch(Dispatchers.IO) {
                            val mutableList = restoredPlaylistTracks.toMutableList()
                            if (startIndex in mutableList.indices) {
                                mutableList[startIndex] = resolveTrack(mutableList[startIndex])
                            }

                            val lastPlaylistTrack = mutableList.last()
                            val recs = onlineRepository.getRelatedRecommendations(lastPlaylistTrack)
                            val existingIds = mutableList.map { it.id }.toSet()
                            val uniqueRecs = recs.filter { it.id !in existingIds }
                            val fullQueue = mutableList + uniqueRecs

                            if (!coroutineContext.isActive) return@launch

                            withContext(Dispatchers.Main) {
                                _isPlaylistContext.value = true
                                _playlistTrackCount.value = savedCount.coerceAtLeast(restoredPlaylistTracks.size)
                                currentTracks = fullQueue
                                mediaPlayerController.restoreQueue(fullQueue, startIndex, lastPosition)
                                prefetchAndKeepQueueAlive(startIndex)
                            }
                        }
                        return
                    }
                } catch (e: Exception) {
                    Log.w("MUESO_RESTORE", "Failed to restore playlist queue from JSON cache", e)
                }
            }

            val title = sharedPreferences.getString("last_track_title", "") ?: ""
            val artist = sharedPreferences.getString("last_track_artist", "") ?: ""
            val filePath = sharedPreferences.getString("last_track_filepath", "") ?: ""
            val artworkUrl = sharedPreferences.getString("last_track_artwork_url", null)
            val duration = sharedPreferences.getLong("last_track_duration", 0L)

            val restoredTrack = TrackEntity(
                id = lastTrackId,
                title = title,
                artist = artist,
                album = "Online Track",
                duration = duration,
                albumId = 0L,
                filePath = filePath,
                artworkUrl = artworkUrl
            )

            restorationJob = viewModelScope.launch(Dispatchers.IO) {
                val resolved = resolveTrack(restoredTrack)
                val recommendations = onlineRepository.getRelatedRecommendations(restoredTrack)
                val existingIds = setOf(resolved.id)
                val uniqueRecs = recommendations.filter { it.id !in existingIds }
                val fullQueue = listOf(resolved) + uniqueRecs

                if (!coroutineContext.isActive) return@launch

                withContext(Dispatchers.Main) {
                    currentTracks = fullQueue
                    mediaPlayerController.restoreQueue(fullQueue, 0, lastPosition)
                    prefetchAndKeepQueueAlive(0)
                }
            }
        }
    }

    fun loadOnlineTrendingTracks() {
        viewModelScope.launch {
            _onlineUiState.value = PlayerUiState.Loading
            val tracks = onlineRepository.getTrendingTracks()
            if (tracks.isNotEmpty()) {
                _onlineUiState.value = PlayerUiState.Success(tracks)
            } else {
                _onlineUiState.value = PlayerUiState.Error("Failed to load trending tracks")
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            mediaPlayerController.playbackState().collect { state ->
                val oldTrackId = _playbackState.value.currentTrackId
                _playbackState.value = state

                Log.d("MUESO_SYNC", "ViewModel observePlaybackState: oldTrackId=$oldTrackId, newTrackId=${state.currentTrackId}, lastRequested=$lastRequestedTrackId")

                // Clear the async guard when ExoPlayer confirms the requested track
                if (state.currentTrackId == lastRequestedTrackId) {
                    Log.d("MUESO_SYNC", "ViewModel: Clearing lastRequestedTrackId")
                    lastRequestedTrackId = null
                }

                if (state.currentTrackId != null) {
                    val currentTrackIdx = currentTracks.indexOfFirst { it.id == state.currentTrackId }
                    if (currentTrackIdx >= 0) {
                        // Check if player stepped past the end of an active playlist
                        if (_isPlaylistContext.value && _playlistTrackCount.value > 0) {
                            val count = _playlistTrackCount.value
                            if (currentTrackIdx >= count) {
                                Log.d("MUESO_PLAYLIST", "Player reached end of playlist ($currentTrackIdx >= $count)")
                                if (_activeSleepMode.value == SleepTimerMode.END_OF_PLAYLIST) {
                                    Log.d("MUESO_PLAYLIST", "Sleep timer END_OF_PLAYLIST active. Stopping playback.")
                                    mediaPlayerController.pause()
                                    clearSleepTimer()
                                    return@collect
                                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                                    Log.d("MUESO_PLAYLIST", "Repeat REPEAT_MODE_ALL active. Looping back to index 0.")
                                    playTrackAtIndex(0)
                                    return@collect
                                }
                            }
                        }

                        val curTrack = currentTracks[currentTrackIdx]
                        val isOnline = curTrack.filePath.startsWith("online:") || curTrack.filePath.startsWith("http")
                        val videoId = if (curTrack.filePath.startsWith("online:")) {
                            curTrack.filePath.removePrefix("online:")
                        } else if (curTrack.artworkUrl != null && curTrack.artworkUrl.contains("/vi/")) {
                            curTrack.artworkUrl.substringAfter("/vi/").substringBefore("/")
                        } else ""

                        val persistentFilePath = if (isOnline && videoId.isNotBlank()) "online:$videoId" else curTrack.filePath

                        sharedPreferences.edit()
                            .putLong("last_track_id", curTrack.id)
                            .putString("last_track_title", curTrack.title)
                            .putString("last_track_artist", curTrack.artist)
                            .putString("last_track_filepath", persistentFilePath)
                            .putString("last_track_artwork_url", curTrack.artworkUrl)
                            .putLong("last_track_duration", curTrack.duration)
                            .putBoolean("last_track_is_online", isOnline)
                            .putLong("last_position", state.currentPositionMs)
                            .apply()

                        saveQueueToPreferences()
                        fetchLyricsForTrack(curTrack)
                        prefetchAndKeepQueueAlive(currentTrackIdx)
                        checkAndApplySponsorBlock(curTrack, state.currentPositionMs, state.isPlaying)
                    }
                }

                // Check "after song" sleep mode on track transition
                if (oldTrackId != null && state.currentTrackId != null && oldTrackId != state.currentTrackId) {
                    checkSleepAfterSong(oldTrackId)
                }
                previousTrackId = state.currentTrackId
            }
        }
    }

    private fun saveQueueToPreferences() {
        val editor = sharedPreferences.edit()
        val isPlaylist = _isPlaylistContext.value
        val count = _playlistTrackCount.value
        editor.putBoolean("last_is_playlist_context", isPlaylist)
        editor.putInt("last_playlist_track_count", count)

        if (isPlaylist && count > 0 && currentTracks.isNotEmpty()) {
            val jsonArr = org.json.JSONArray()
            val playlistTracks = currentTracks.take(count)
            for (t in playlistTracks) {
                val obj = org.json.JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("filePath", t.filePath)
                obj.put("artworkUrl", t.artworkUrl ?: "")
                obj.put("duration", t.duration)
                jsonArr.put(obj)
            }
            editor.putString("last_playlist_queue_json", jsonArr.toString())
        } else {
            editor.remove("last_playlist_queue_json")
        }
        editor.apply()
    }

    private var activeSponsorSegments: List<com.akshay.musicplayer.data.remote.SponsorSegment> = emptyList()
    private var activeSponsorTrackId: Long? = null
    private var isFetchingSponsorSegments = false

    private fun checkAndApplySponsorBlock(track: TrackEntity, currentPositionMs: Long, isPlaying: Boolean) {
        val videoId = if (track.filePath.startsWith("online:")) {
            track.filePath.removePrefix("online:")
        } else if (track.artworkUrl != null && track.artworkUrl.contains("/vi/")) {
            track.artworkUrl.substringAfter("/vi/").substringBefore("/")
        } else ""

        if (videoId.isNotBlank() && activeSponsorTrackId != track.id) {
            activeSponsorTrackId = track.id
            activeSponsorSegments = emptyList()
            if (!isFetchingSponsorSegments) {
                isFetchingSponsorSegments = true
                viewModelScope.launch(Dispatchers.IO) {
                    val segments = onlineRepository.getSponsorSkipSegments(videoId)
                    withContext(Dispatchers.Main) {
                        activeSponsorSegments = segments
                        isFetchingSponsorSegments = false
                    }
                }
            }
        }

        if (isPlaying && activeSponsorSegments.isNotEmpty()) {
            for (seg in activeSponsorSegments) {
                val shouldSkipCategory = when (seg.category) {
                    "sponsor" -> _skipSponsor.value
                    "selfpromo" -> _skipSelfPromo.value
                    "interaction" -> _skipInteraction.value
                    "intro", "outro" -> _skipIntroOutro.value
                    "music_offtopic", "filler" -> _skipNonMusicOffTopic.value
                    else -> true
                }
                if (!shouldSkipCategory) continue

                val isIntroAtStart = seg.startMs <= 1500L
                if (isIntroAtStart && currentPositionMs in 0L until (seg.endMs - 300L)) {
                    Log.d("MUESO_SPONSOR", "Auto-skipping intro segment from 0ms to ${seg.endMs}ms for '${track.title}'")
                    mediaPlayerController.seekTo(seg.endMs)
                    break
                } else if (!isIntroAtStart && currentPositionMs in (seg.startMs - 200L)..(seg.endMs - 300L)) {
                    Log.d("MUESO_SPONSOR", "Auto-skipping SponsorBlock '${seg.category}' segment from ${seg.startMs}ms to ${seg.endMs}ms for '${track.title}'")
                    mediaPlayerController.seekTo(seg.endMs)
                    break
                }
            }
        }
    }

    private val resolvingTrackIds = mutableSetOf<Long>()
    private var isFetchingMoreQueue = false

    private fun prefetchAndKeepQueueAlive(currentIndex: Int) {
        if (currentIndex !in currentTracks.indices) return
        val currentTrack = currentTracks[currentIndex]

        // 1. Ensure current playing track is resolved
        if (currentTrack.filePath.startsWith("online:") && !resolvingTrackIds.contains(currentTrack.id)) {
            resolvingTrackIds.add(currentTrack.id)
            viewModelScope.launch(Dispatchers.IO) {
                Log.d("MUESO_QUEUE", "Resolving playing track at index $currentIndex: ${currentTrack.title}")
                val resolved = resolveTrack(currentTrack)
                withContext(Dispatchers.Main) {
                    val list = currentTracks.toMutableList()
                    if (currentIndex in list.indices) {
                        list[currentIndex] = resolved
                        currentTracks = list
                        mediaPlayerController.updateTrackInQueue(currentIndex, resolved)
                    }
                    resolvingTrackIds.remove(currentTrack.id)
                }
            }
        }

        // 2. Pre-fetch next track in background so next song is ready
        val nextIndex = currentIndex + 1
        if (nextIndex in currentTracks.indices) {
            val nextTrack = currentTracks[nextIndex]
            if (nextTrack.filePath.startsWith("online:") && !resolvingTrackIds.contains(nextTrack.id)) {
                resolvingTrackIds.add(nextTrack.id)
                viewModelScope.launch(Dispatchers.IO) {
                    Log.d("MUESO_QUEUE", "Pre-fetching next track at index $nextIndex: ${nextTrack.title}")
                    val resolvedNext = resolveTrack(nextTrack)
                    withContext(Dispatchers.Main) {
                        val list = currentTracks.toMutableList()
                        if (nextIndex in list.indices) {
                            list[nextIndex] = resolvedNext
                            currentTracks = list
                            mediaPlayerController.updateTrackInQueue(nextIndex, resolvedNext)
                        }
                        resolvingTrackIds.remove(nextTrack.id)
                    }
                }
            }
        }

        // 3. Keep queue alive: Auto-fetch related suggestions when nearing the end of online queue
        if (currentIndex >= currentTracks.size - 2 && !isFetchingMoreQueue && (currentTrack.filePath.contains("http") || currentTrack.filePath.contains("online:"))) {
            isFetchingMoreQueue = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val query = if (currentTrack.artist != "Unknown Artist" && currentTrack.artist.isNotBlank()) {
                        "${currentTrack.artist} top songs"
                    } else {
                        "${currentTrack.title} songs"
                    }
                    Log.d("MUESO_QUEUE", "Nearing queue end. Auto-fetching related tracks for query: '$query'")
                    val newTracks = onlineRepository.searchOnlineTracks(query)
                    val existingIds = currentTracks.map { it.id }.toSet()
                    val uniqueNew = newTracks.filter { it.id !in existingIds }

                    if (uniqueNew.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val updatedQueue = currentTracks + uniqueNew
                            currentTracks = updatedQueue
                            mediaPlayerController.appendTracksToQueue(uniqueNew)
                            Log.d("MUESO_QUEUE", "Successfully appended ${uniqueNew.size} tracks to keep queue alive!")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MUESO_QUEUE", "Error auto-fetching queue suggestions", e)
                } finally {
                    isFetchingMoreQueue = false
                }
            }
        }
    }

    private val _lyricsFetchStatus = MutableStateFlow<Map<Long, LyricsFetchStatus>>(emptyMap())
    val lyricsFetchStatus: StateFlow<Map<Long, LyricsFetchStatus>> = _lyricsFetchStatus.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    fun adjustLyricsOffset(deltaMs: Long) {
        _lyricsOffsetMs.value += deltaMs
    }

    fun resetLyricsOffset() {
        _lyricsOffsetMs.value = 0L
    }

    private val fetchedLyricsTrackIds = mutableSetOf<Long>()

    private fun fetchLyricsForTrack(track: TrackEntity) {
        if (track.lyrics != null || fetchedLyricsTrackIds.contains(track.id)) return
        fetchedLyricsTrackIds.add(track.id)

        _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.FETCHING)

        viewModelScope.launch(Dispatchers.IO) {
            val lyricsData = onlineRepository.fetchLyrics(track.title, track.artist)
            withContext(Dispatchers.Main) {
                if (lyricsData != null && (lyricsData.lines.isNotEmpty() || !lyricsData.rawText.isNullOrBlank())) {
                    val updatedTracks = currentTracks.map {
                        if (it.id == track.id) it.copy(lyrics = lyricsData) else it
                    }
                    currentTracks = updatedTracks
                    _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.FOUND)
                } else {
                    _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.NOT_FOUND)
                }
            }
        }
    }

    private val _downloadStates = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadProgress>> = _downloadStates.asStateFlow()

    fun downloadOnlineTrack(context: android.content.Context, track: TrackEntity) {
        if (_downloadStates.value[track.id]?.isDownloading == true || _downloadStates.value[track.id]?.isDownloaded == true) return

        viewModelScope.launch(Dispatchers.IO) {
            _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.01f))
            try {
                val videoId = if (track.filePath.startsWith("online:")) track.filePath.removePrefix("online:") else null
                val downloadUrl = if (videoId != null) onlineRepository.getStreamUrl(videoId) else track.filePath
                
                if (!downloadUrl.startsWith("http")) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "Stream URL unavailable"))
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to get audio stream for download", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = "HTTP error ${response.code}"))
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Download failed with HTTP ${response.code}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val contentLength = body.contentLength()
                val ext = if (downloadUrl.contains("mime=audio%2Fmp4") || downloadUrl.contains(".m4a") || downloadUrl.contains("mime=video%2Fmp4")) ".m4a" else ".mp3"
                val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                
                val tempFile = java.io.File(context.cacheDir, "temp_dl_${track.id}$ext")
                if (tempFile.exists()) tempFile.delete()

                val inputStream = body.byteStream()
                val outputStream = java.io.FileOutputStream(tempFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val prog = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0.01f, 0.95f)
                        _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = prog))
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Step 2: Embed Metadata & Artwork using Python Mutagen
                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = true, progress = 0.96f))
                onlineRepository.embedMetadata(tempFile.absolutePath, track.title, track.artist, "Mueso Downloads", track.artworkUrl)

                // Step 3: Save to Target Directory based on settings (downloadFolder)
                val folderSetting = _downloadFolder.value
                val targetDir = when {
                    folderSetting == "Downloads" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    folderSetting == "Internal App Storage" -> context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
                    folderSetting.startsWith("/") -> java.io.File(folderSetting)
                    else -> {
                        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                        java.io.File(musicDir, folderSetting.removePrefix("Music/"))
                    }
                }
                if (!targetDir.exists()) targetDir.mkdirs()

                val destFile = java.io.File(targetDir, "$sanitizedTitle$ext")
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()

                // Scan into Android MediaStore
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved \"${track.title}\" to ${targetDir.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MUESO_DOWNLOAD", "Error downloading track ${track.title}", e)
                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(error = e.message))
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeMediaEvents() {
        viewModelScope.launch {
            mediaPlayerController.mediaEvents().collect { event ->
                when (event) {
                    is PlayerEvent.TrackEnded -> {
                        val currentIdx = getCurrentTrackIndex()
                        val isPlaylist = _isPlaylistContext.value && _playlistTrackCount.value > 0
                        val playlistEndIdx = if (isPlaylist) _playlistTrackCount.value - 1 else currentTracks.size - 1

                        if (isPlaylist && currentIdx >= playlistEndIdx) {
                            if (_activeSleepMode.value == SleepTimerMode.END_OF_PLAYLIST) {
                                Log.d("MUESO_PLAYLIST", "TrackEnded at playlist end with END_OF_PLAYLIST sleep timer. Pausing.")
                                mediaPlayerController.pause()
                                clearSleepTimer()
                                return@collect
                            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                                Log.d("MUESO_PLAYLIST", "TrackEnded at playlist end with REPEAT_MODE_ALL. Playing index 0.")
                                playTrackAtIndex(0)
                                return@collect
                            }
                        }

                        if (_activeSleepMode.value == SleepTimerMode.END_OF_PLAYLIST && currentIdx >= currentTracks.size - 1) {
                            mediaPlayerController.pause()
                            clearSleepTimer()
                            return@collect
                        }

                        playNextTrack()
                    }
                    is PlayerEvent.PlaybackError -> {
                        Log.w("MUESO_STREAM", "Playback error encountered: ${event.message}. Auto-advancing to next track.")
                        delay(500)
                        playNextTrack()
                    }
                }
            }
        }
    }

    fun playNextTrack() {
        viewModelScope.launch {
            mediaPlayerController.seekToNext()
        }
    }

    fun setOfflineLibraryTab(tabIndex: Int) {
        _offlineLibraryTab.value = tabIndex
        sharedPreferences.edit().putInt("offline_library_tab", tabIndex).apply()
    }

    fun playPreviousTrack() {
        viewModelScope.launch {
            mediaPlayerController.seekToPrevious()
        }
    }

    private val _isResolvingTrack = MutableStateFlow(false)
    val isResolvingTrack: StateFlow<Boolean> = _isResolvingTrack.asStateFlow()

    private val _resolvingTrackTitle = MutableStateFlow<String?>(null)
    val resolvingTrackTitle: StateFlow<String?> = _resolvingTrackTitle.asStateFlow()

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        cancelRestoration()
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (startIndex in tracks.indices) tracks[startIndex] else null
            if (target != null && target.filePath.startsWith("online:")) {
                _resolvingTrackTitle.value = target.title
                _isResolvingTrack.value = true
            }

            val mutableTracks = tracks.toMutableList()
            if (startIndex in mutableTracks.indices) {
                mutableTracks[startIndex] = resolveTrack(mutableTracks[startIndex])
            }

            withContext(Dispatchers.Main) {
                _isResolvingTrack.value = false
                _resolvingTrackTitle.value = null
                currentTracks = mutableTracks
                mediaPlayerController.setPlaylistAndPlay(currentTracks, startIndex)
                prefetchAndKeepQueueAlive(startIndex)
            }
        }
    }

    fun playTrack(track: TrackEntity) {
        cancelRestoration()
        _isPlaylistContext.value = false
        _playlistTrackCount.value = 0
        lastRequestedTrackId = track.id
        Log.d("MUESO_SYNC", "ViewModel playTrack: requested track.id=${track.id}")
        viewModelScope.launch(Dispatchers.IO) {
            val isOnline = track.filePath.startsWith("online:")
            if (isOnline) {
                _resolvingTrackTitle.value = track.title
                _isResolvingTrack.value = true
            }

            val resolved = resolveTrack(track)

            withContext(Dispatchers.Main) {
                _isResolvingTrack.value = false
                _resolvingTrackTitle.value = null
                currentTracks = listOf(resolved)
                mediaPlayerController.setPlaylistAndPlay(currentTracks, 0)
            }

            if (isOnline) {
                val recommendations = onlineRepository.getRelatedRecommendations(track)
                val existingIds = currentTracks.map { it.id }.toSet()
                val uniqueRecs = recommendations.filter { it.id !in existingIds }
                if (uniqueRecs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentTracks = currentTracks + uniqueRecs
                        mediaPlayerController.appendTracksToQueue(uniqueRecs)
                        prefetchAndKeepQueueAlive(0)
                    }
                }
            }
        }
    }


    fun playTrackIfChanged(track: TrackEntity) {
        Log.d("MUESO_SYNC", "ViewModel playTrackIfChanged: asked for ${track.id}. current=${_playbackState.value.currentTrackId}, lastRequested=$lastRequestedTrackId")
        // Skip if this is already what we asked ExoPlayer to play (async guard)
        // or if ExoPlayer already confirmed it's playing this track
        if (track.id == lastRequestedTrackId || track.id == _playbackState.value.currentTrackId) {
            Log.d("MUESO_SYNC", "ViewModel playTrackIfChanged: SKIPPING.")
            return
        }
        val index = currentTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playTrackAtIndex(index)
        } else {
            playTrack(track)
        }
    }

    fun playTrackAtIndex(index: Int) {
        if (index >= 0 && index < currentTracks.size) {
            val track = currentTracks[index]
            lastRequestedTrackId = track.id
            if (index >= _playlistTrackCount.value) {
                // Stepped past the playlist boundary into radio recommendations
                _isPlaylistContext.value = false
                _playlistTrackCount.value = 0
            }
            mediaPlayerController.seekToIndex(index)
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= currentTracks.size) return
        if (toIndex < 0 || toIndex >= currentTracks.size) return
        if (fromIndex == toIndex) return

        Log.d("MUESO_SYNC", "ViewModel moveInQueue: from=$fromIndex, to=$toIndex")

        val mutableList = currentTracks.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        currentTracks = mutableList

        // Synchronize with ExoPlayer
        mediaPlayerController.moveQueueItem(fromIndex, toIndex)
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            mediaPlayerController.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            mediaPlayerController.seekTo(positionMs)
        }
    }

    fun getCurrentTrack(): TrackEntity? {
        val currentTrackId = _playbackState.value.currentTrackId ?: return null
        return currentTracks.find { it.id == currentTrackId }
    }

    fun getTrackAtIndex(index: Int): TrackEntity? {
        return if (index >= 0 && index < currentTracks.size) currentTracks[index] else null
    }

    fun getTotalTracks(): Int = currentTracks.size

    fun getUpcomingTrackCount(): Int {
        val currentId = _playbackState.value.currentTrackId ?: return currentTracks.size
        val currentIndex = currentTracks.indexOfFirst { it.id == currentId }
        return if (currentIndex >= 0) currentTracks.size - currentIndex - 1 else currentTracks.size
    }

    fun getCurrentTrackIndex(): Int {
        val currentTrackId = _playbackState.value.currentTrackId ?: return 0
        return currentTracks.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
    }

    // --- Repeat Mode ---
    fun cycleRepeatMode() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode
        mediaPlayerController.setRepeatMode(newMode)
    }

    // --- Sleep Timer ---
    fun showSleepTimerSheet() {
        _showSleepTimerSheet.value = true
    }

    fun dismissSleepTimerSheet() {
        _showSleepTimerSheet.value = false
    }

    fun setSleepTimer(minutes: Int) {
        clearSleepTimerInternal()
        _activeSleepMode.value = SleepTimerMode.TIMER
        _sleepTimerMinutesLeft.value = minutes

        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (isActive && remaining > 0) {
                delay(60_000L)
                remaining--
                _sleepTimerMinutesLeft.value = if (remaining > 0) remaining else null
            }
            if (isActive) {
                mediaPlayerController.togglePlayPause()
                clearSleepTimerInternal()
            }
        }
    }

    fun setSleepAfterSong(trackId: Long) {
        clearSleepTimerInternal()
        _activeSleepMode.value = SleepTimerMode.AFTER_SONG
        _sleepAfterSongId.value = trackId
    }

    fun setSleepEndOfPlaylist() {
        clearSleepTimerInternal()
        _activeSleepMode.value = SleepTimerMode.END_OF_PLAYLIST
    }

    fun clearSleepTimer() {
        clearSleepTimerInternal()
    }

    private fun clearSleepTimerInternal() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _activeSleepMode.value = null
        _sleepTimerMinutesLeft.value = null
        _sleepAfterSongId.value = null
    }

    private fun checkSleepAfterSong(finishedTrackId: Long) {
        if (_activeSleepMode.value == SleepTimerMode.AFTER_SONG &&
            _sleepAfterSongId.value == finishedTrackId) {
            // The target song just finished — pause
            mediaPlayerController.togglePlayPause()
            clearSleepTimerInternal()
        }
    }

    // --- Queue ---
    fun toggleQueueSheet() {
        _showQueueSheet.value = !_showQueueSheet.value
    }

    fun dismissQueueSheet() {
        _showQueueSheet.value = false
    }

    // --- Settings & SponsorBlock Toggles ---
    private val _showSettingsSheet = MutableStateFlow(false)
    val showSettingsSheet: StateFlow<Boolean> = _showSettingsSheet.asStateFlow()

    private val _skipSponsor = MutableStateFlow(sharedPreferences.getBoolean("skip_sponsor", true))
    val skipSponsor: StateFlow<Boolean> = _skipSponsor.asStateFlow()

    private val _skipSelfPromo = MutableStateFlow(sharedPreferences.getBoolean("skip_selfpromo", true))
    val skipSelfPromo: StateFlow<Boolean> = _skipSelfPromo.asStateFlow()

    private val _skipInteraction = MutableStateFlow(sharedPreferences.getBoolean("skip_interaction", true))
    val skipInteraction: StateFlow<Boolean> = _skipInteraction.asStateFlow()

    private val _skipIntroOutro = MutableStateFlow(sharedPreferences.getBoolean("skip_intro_outro", true))
    val skipIntroOutro: StateFlow<Boolean> = _skipIntroOutro.asStateFlow()

    private val _skipNonMusicOffTopic = MutableStateFlow(sharedPreferences.getBoolean("skip_non_music_offtopic", true))
    val skipNonMusicOffTopic: StateFlow<Boolean> = _skipNonMusicOffTopic.asStateFlow()

    fun toggleSettingsSheet() { _showSettingsSheet.value = !_showSettingsSheet.value }
    fun dismissSettingsSheet() { _showSettingsSheet.value = false }

    fun setSkipSponsor(enabled: Boolean) {
        _skipSponsor.value = enabled
        sharedPreferences.edit().putBoolean("skip_sponsor", enabled).apply()
    }
    fun setSkipSelfPromo(enabled: Boolean) {
        _skipSelfPromo.value = enabled
        sharedPreferences.edit().putBoolean("skip_selfpromo", enabled).apply()
    }
    fun setSkipInteraction(enabled: Boolean) {
        _skipInteraction.value = enabled
        sharedPreferences.edit().putBoolean("skip_interaction", enabled).apply()
    }
    fun setSkipIntroOutro(enabled: Boolean) {
        _skipIntroOutro.value = enabled
        sharedPreferences.edit().putBoolean("skip_intro_outro", enabled).apply()
    }
    fun setSkipNonMusicOffTopic(enabled: Boolean) {
        _skipNonMusicOffTopic.value = enabled
        sharedPreferences.edit().putBoolean("skip_non_music_offtopic", enabled).apply()
    }

    // --- Hero Playlist & Online Playlist Management ---
    private val _heroPlaylistId = MutableStateFlow(sharedPreferences.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global")
    val heroPlaylistId: StateFlow<String> = _heroPlaylistId.asStateFlow()

    fun setHeroPlaylistId(id: String) {
        _heroPlaylistId.value = id
        sharedPreferences.edit().putString("hero_playlist_id", id).apply()
    }

    fun updateOnlinePlaylistDetails(playlistId: Long, name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            onlinePlaylistDao.updateOnlinePlaylistDetails(playlistId, name, description)
        }
    }

    // --- Audio, Thumbnail, Download & App Settings ---
    private val _audioQuality = MutableStateFlow(sharedPreferences.getString("audio_quality", "High (320 kbps)") ?: "High (320 kbps)")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _thumbnailQuality = MutableStateFlow(sharedPreferences.getString("thumbnail_quality", "Highest (1080p Maxres)") ?: "Highest (1080p Maxres)")
    val thumbnailQuality: StateFlow<String> = _thumbnailQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(sharedPreferences.getString("download_quality", "Highest (320 kbps)") ?: "Highest (320 kbps)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _downloadFolder = MutableStateFlow(sharedPreferences.getString("download_folder", "Music/Mueso") ?: "Music/Mueso")
    val downloadFolder: StateFlow<String> = _downloadFolder.asStateFlow()

    private val _enableLyrics = MutableStateFlow(sharedPreferences.getBoolean("enable_lyrics", true))
    val enableLyrics: StateFlow<Boolean> = _enableLyrics.asStateFlow()

    private val _isDarkMode = MutableStateFlow(sharedPreferences.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _showOnLockscreen = MutableStateFlow(sharedPreferences.getBoolean("show_on_lockscreen", true))
    val showOnLockscreen: StateFlow<Boolean> = _showOnLockscreen.asStateFlow()

    private val _highRefreshRate = MutableStateFlow(sharedPreferences.getBoolean("high_refresh_rate", true))
    val highRefreshRate: StateFlow<Boolean> = _highRefreshRate.asStateFlow()

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        sharedPreferences.edit().putString("audio_quality", quality).apply()
    }

    fun setThumbnailQuality(quality: String) {
        _thumbnailQuality.value = quality
        sharedPreferences.edit().putString("thumbnail_quality", quality).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        sharedPreferences.edit().putString("download_quality", quality).apply()
    }

    fun setDownloadFolder(folder: String) {
        _downloadFolder.value = folder
        sharedPreferences.edit().putString("download_folder", folder).apply()
    }

    fun setEnableLyrics(enabled: Boolean) {
        _enableLyrics.value = enabled
        sharedPreferences.edit().putBoolean("enable_lyrics", enabled).apply()
    }

    fun setShowOnLockscreen(enabled: Boolean) {
        _showOnLockscreen.value = enabled
        sharedPreferences.edit().putBoolean("show_on_lockscreen", enabled).apply()
    }

    fun setHighRefreshRate(enabled: Boolean) {
        _highRefreshRate.value = enabled
        sharedPreferences.edit().putBoolean("high_refresh_rate", enabled).apply()
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPreferences.edit().putBoolean("is_dark_mode", enabled).apply()
    }

    fun playNext(track: TrackEntity) {
        val currentIndex = currentTracks.indexOfFirst { it.id == _playbackState.value.currentTrackId }
        val insertIndex = if (currentIndex >= 0) currentIndex + 1 else currentTracks.size
        val updated = currentTracks.toMutableList()
        updated.add(insertIndex, track)
        currentTracks = updated
        mediaPlayerController.setPlaylistAndPlay(currentTracks, if (currentIndex >= 0) currentIndex else 0)
    }

    fun addToQueue(track: TrackEntity) {
        val updated = currentTracks.toMutableList()
        updated.add(track)
        currentTracks = updated
        mediaPlayerController.appendTracksToQueue(listOf(track))
    }

    fun forceRefreshAll(context: android.content.Context) {
        val editor = sharedPreferences.edit()
        val keys = sharedPreferences.all.keys.filter { it.startsWith("curated_cache_") }
        for (k in keys) {
            editor.remove(k)
        }
        editor.apply()

        loadLocalTracks(forceReload = true)
        android.widget.Toast.makeText(context, "App refreshed! Caches cleared & songs rescanned.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun getQueueTracks(): List<TrackEntity> = currentTracks

    // --- Google Drive Backup & Dirty State Tracking ---
    private val _hasUnbackedUpChanges = MutableStateFlow(sharedPreferences.getBoolean("has_unbacked_up_changes", false))
    val hasUnbackedUpChanges: StateFlow<Boolean> = _hasUnbackedUpChanges.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow(sharedPreferences.getLong("last_backup_timestamp", 0L))
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _isRestoreInProgress = MutableStateFlow(false)
    val isRestoreInProgress: StateFlow<Boolean> = _isRestoreInProgress.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow<String?>(sharedPreferences.getString("google_account_email", null))
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleAccount = MutableStateFlow<com.google.android.gms.auth.api.signin.GoogleSignInAccount?>(null)
    val googleAccount: StateFlow<com.google.android.gms.auth.api.signin.GoogleSignInAccount?> = _googleAccount.asStateFlow()

    fun markDirty() {
        _hasUnbackedUpChanges.value = true
        sharedPreferences.edit().putBoolean("has_unbacked_up_changes", true).apply()
    }

    fun setGoogleAccountEmail(email: String?) {
        _googleAccountEmail.value = email
        if (email != null) {
            sharedPreferences.edit().putString("google_account_email", email).apply()
        } else {
            sharedPreferences.edit().remove("google_account_email").apply()
        }
    }

    fun initGoogleDriveAccount(context: android.content.Context) {
        val repo = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context)
        val lastAcc = repo.getSignedInAccount()
        _googleAccount.value = lastAcc
        if (lastAcc?.email != null) {
            setGoogleAccountEmail(lastAcc.email)
        }
    }

    fun setGoogleAccount(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        _googleAccount.value = account
        if (account?.email != null) {
            setGoogleAccountEmail(account.email)
        }
    }

    fun connectAndBackupGoogleAccount(context: android.content.Context, email: String, onResult: (Boolean, String) -> Unit) {
        if (_isBackupInProgress.value) return
        _isBackupInProgress.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context)

                // Collect online playlists
                val onlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first()
                val backupOnlineList = onlineEntities.map { p ->
                    val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupOnlinePlaylist(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        dateCreated = p.dateCreated,
                        tracks = tracks.map { t ->
                            com.akshay.musicplayer.data.backup.BackupTrack(
                                title = t.title,
                                artist = t.artist,
                                artworkUrl = t.artworkUrl,
                                filePath = t.filePath,
                                duration = t.duration,
                                orderIndex = t.orderIndex
                            )
                        }
                    )
                }

                // Collect local playlists
                val localEntities = playlistDao.getAllPlaylists().first()
                val backupLocalList = localEntities.map { p ->
                    val refs = playlistDao.getPlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupLocalPlaylist(
                        name = p.name,
                        dateCreated = p.dateCreated,
                        tracks = refs.map { ref ->
                            com.akshay.musicplayer.data.backup.BackupLocalTrackInfo(
                                title = "",
                                artist = "",
                                orderIndex = ref.orderIndex
                            )
                        }
                    )
                }

                val backupData = com.akshay.musicplayer.data.backup.MuesoBackupData(
                    onlinePlaylists = backupOnlineList,
                    localPlaylists = backupLocalList
                )

                val result = driveRepo.uploadBackup(email, backupData)
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    if (result.isSuccess) {
                        setGoogleAccountEmail(email)
                        _hasUnbackedUpChanges.value = false
                        val now = System.currentTimeMillis()
                        _lastBackupTimestamp.value = now
                        sharedPreferences.edit()
                            .putBoolean("has_unbacked_up_changes", false)
                            .putLong("last_backup_timestamp", now)
                            .apply()
                        onResult(true, "Playlists backed up to Google Drive!")
                    } else {
                        onResult(false, result.exceptionOrNull()?.message ?: "Sign-in verification failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    onResult(false, e.message ?: "Sign-in verification failed")
                }
            }
        }
    }

    fun signOutGoogle(context: android.content.Context) {
        val client = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context).getGoogleSignInClient(context)
        client.signOut().addOnCompleteListener {
            _googleAccount.value = null
            setGoogleAccountEmail(null)
        }
    }

    fun performDriveBackup(context: android.content.Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val email = _googleAccountEmail.value ?: _googleAccount.value?.email
        if (email.isNullOrBlank()) return onResult(false, "Not signed in to Google")
        if (_isBackupInProgress.value) return
        _isBackupInProgress.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context)
                
                // Collect online playlists
                val onlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first()
                val backupOnlineList = onlineEntities.map { p ->
                    val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupOnlinePlaylist(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        dateCreated = p.dateCreated,
                        tracks = tracks.map { t ->
                            com.akshay.musicplayer.data.backup.BackupTrack(
                                title = t.title,
                                artist = t.artist,
                                artworkUrl = t.artworkUrl,
                                filePath = t.filePath,
                                duration = t.duration,
                                orderIndex = t.orderIndex
                            )
                        }
                    )
                }

                // Collect local playlists
                val localEntities = playlistDao.getAllPlaylists().first()
                val backupLocalList = localEntities.map { p ->
                    val refs = playlistDao.getPlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupLocalPlaylist(
                        name = p.name,
                        dateCreated = p.dateCreated,
                        tracks = refs.map { ref ->
                            com.akshay.musicplayer.data.backup.BackupLocalTrackInfo(
                                title = "",
                                artist = "",
                                orderIndex = ref.orderIndex
                            )
                        }
                    )
                }

                val backupData = com.akshay.musicplayer.data.backup.MuesoBackupData(
                    onlinePlaylists = backupOnlineList,
                    localPlaylists = backupLocalList
                )

                val result = driveRepo.uploadBackup(email, backupData)
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    if (result.isSuccess) {
                        _hasUnbackedUpChanges.value = false
                        val now = System.currentTimeMillis()
                        _lastBackupTimestamp.value = now
                        sharedPreferences.edit()
                            .putBoolean("has_unbacked_up_changes", false)
                            .putLong("last_backup_timestamp", now)
                            .apply()
                        onResult(true, "Playlists backed up to Google Drive!")
                    } else {
                        onResult(false, result.exceptionOrNull()?.message ?: "Backup failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    onResult(false, e.message ?: "Backup failed")
                }
            }
        }
    }

    fun performDriveRestore(context: android.content.Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val email = _googleAccountEmail.value ?: _googleAccount.value?.email
        if (email.isNullOrBlank()) return onResult(false, "Not signed in to Google")
        if (_isRestoreInProgress.value) return
        _isRestoreInProgress.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context)
                val result = driveRepo.downloadBackup(email)

                withContext(Dispatchers.Main) {
                    _isRestoreInProgress.value = false
                    if (result.isSuccess) {
                        val backupData = result.getOrNull()
                        if (backupData != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                backupData.onlinePlaylists.forEach { op ->
                                    val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                                        com.akshay.musicplayer.data.db.OnlinePlaylistEntity(
                                            name = op.name,
                                            description = op.description,
                                            artworkUrl = op.artworkUrl,
                                            dateCreated = op.dateCreated
                                        )
                                    )
                                    op.tracks.forEach { t ->
                                        onlinePlaylistDao.insertOnlineTrack(
                                            com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
                                                onlinePlaylistId = playlistId,
                                                trackId = System.currentTimeMillis() + (0..10000).random(),
                                                title = t.title,
                                                artist = t.artist,
                                                artworkUrl = t.artworkUrl,
                                                filePath = t.filePath,
                                                duration = t.duration,
                                                orderIndex = t.orderIndex
                                            )
                                        )
                                    }
                                }
                            }
                            onResult(true, "Restored ${backupData.onlinePlaylists.size} online playlists successfully!")
                        } else {
                            onResult(false, "No backup data found")
                        }
                    } else {
                        onResult(false, result.exceptionOrNull()?.message ?: "Restore failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isRestoreInProgress.value = false
                    onResult(false, e.message ?: "Restore failed")
                }
            }
        }
    }
}
