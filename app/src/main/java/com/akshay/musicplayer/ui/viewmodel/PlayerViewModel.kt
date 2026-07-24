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

    val currentTrackIndexState: StateFlow<Int> = combine(_playbackState, _uiState) { state, _ ->
        val currentTrackId = state.currentTrackId ?: return@combine 0
        val index = currentTracks.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
        Log.d("MUESO_SYNC", "ViewModel currentTrackIndexState: calculated index $index for track $currentTrackId")
        index
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var currentTracks: List<TrackEntity> = emptyList()
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

        val localMatches = currentTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        _searchResults.value = localMatches
        _isSearchingOnline.value = true

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
    
    private val _playlists = MutableStateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
        loadOnlineTrendingTracks()
        observePlaybackState()
        observeMediaEvents()
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
                currentTracks = tracks
                if (tracks.isEmpty()) {
                    _uiState.value = PlayerUiState.Empty
                } else {
                    _uiState.value = PlayerUiState.Success(tracks)

                    // Attempt to restore persistent playback state
                    val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
                    val lastPosition = sharedPreferences.getLong("last_position", 0L)
                    
                    if (lastTrackId != -1L) {
                        val index = tracks.indexOfFirst { it.id == lastTrackId }.takeIf { it >= 0 } ?: 0
                        mediaPlayerController.restoreQueue(tracks, index, lastPosition)
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

    fun restoreLastPlaybackStateOrOffline(context: android.content.Context? = null) {
        val isOnline = sharedPreferences.getBoolean("last_track_is_online", false)
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        val lastPosition = sharedPreferences.getLong("last_position", 0L)
        val hasNet = context?.let { isNetworkAvailable(it) } ?: true

        if (isOnline && lastTrackId != -1L && hasNet) {
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

            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    currentTracks = listOf(restoredTrack)
                    _uiState.value = PlayerUiState.Success(currentTracks)
                }

                val resolved = resolveTrack(restoredTrack)
                withContext(Dispatchers.Main) {
                    currentTracks = listOf(resolved)
                    _uiState.value = PlayerUiState.Success(currentTracks)
                    mediaPlayerController.restoreQueue(currentTracks, 0, lastPosition)
                }

                val recommendations = onlineRepository.getRelatedRecommendations(restoredTrack)
                val existingIds = currentTracks.map { it.id }.toSet()
                val uniqueRecs = recommendations.filter { it.id !in existingIds }
                if (uniqueRecs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentTracks = currentTracks + uniqueRecs
                        if (_uiState.value is PlayerUiState.Success) {
                            _uiState.value = PlayerUiState.Success(currentTracks)
                        }
                        mediaPlayerController.appendTracksToQueue(uniqueRecs)
                        prefetchAndKeepQueueAlive(0)
                    }
                }
            }
        } else {
            loadLocalTracks(forceReload = true)
        }
    }

    fun loadOnlineTrendingTracks() {
        viewModelScope.launch {
            _onlineUiState.value = PlayerUiState.Loading
            val tracks = onlineRepository.getTrendingTracks()
            if (tracks.isNotEmpty()) {
                _onlineUiState.value = PlayerUiState.Success(tracks)
                // If no track is currently prepared (fresh start, no history), auto-queue the trending list
                val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
                if (lastTrackId == -1L && currentTracks.isEmpty()) {
                    currentTracks = tracks
                    _uiState.value = PlayerUiState.Success(tracks)
                    mediaPlayerController.restoreQueue(tracks, 0, 0L)
                }
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
                        val curTrack = currentTracks[currentTrackIdx]
                        val isOnline = curTrack.filePath.startsWith("online:") || curTrack.filePath.startsWith("http")
                        sharedPreferences.edit()
                            .putLong("last_track_id", curTrack.id)
                            .putString("last_track_title", curTrack.title)
                            .putString("last_track_artist", curTrack.artist)
                            .putString("last_track_filepath", curTrack.filePath)
                            .putString("last_track_artwork_url", curTrack.artworkUrl)
                            .putLong("last_track_duration", curTrack.duration)
                            .putBoolean("last_track_is_online", isOnline)
                            .putLong("last_position", state.currentPositionMs)
                            .apply()

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
                        if (_uiState.value is PlayerUiState.Success) {
                            _uiState.value = PlayerUiState.Success(currentTracks)
                        }
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
                            if (_uiState.value is PlayerUiState.Success) {
                                _uiState.value = PlayerUiState.Success(currentTracks)
                            }
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
                            if (_uiState.value is PlayerUiState.Success) {
                                _uiState.value = PlayerUiState.Success(currentTracks)
                            }
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
                    if (_uiState.value is PlayerUiState.Success) {
                        _uiState.value = PlayerUiState.Success(currentTracks)
                    }
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

                // Step 3: Save to Music/Mueso/[SanitizedTitle]ext
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                val muesoDir = java.io.File(musicDir, "Mueso")
                if (!muesoDir.exists()) muesoDir.mkdirs()

                val destFile = java.io.File(muesoDir, "$sanitizedTitle$ext")
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()

                // Scan into Android MediaStore
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

                _downloadStates.value = _downloadStates.value + (track.id to DownloadProgress(isDownloading = false, isDownloaded = true, progress = 1f))
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved \"${track.title}\" to Music/Mueso", android.widget.Toast.LENGTH_SHORT).show()
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
                        // Check if "end of playlist" sleep mode should stop playback
                        if (_activeSleepMode.value == SleepTimerMode.END_OF_PLAYLIST) {
                            val currentIdx = getCurrentTrackIndex()
                            if (currentIdx >= currentTracks.size - 1) {
                                // Last track finished — stop
                                clearSleepTimer()
                                return@collect  // Don't play next
                            }
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

    private suspend fun resolveTrack(track: TrackEntity): TrackEntity {
        return if (track.filePath.startsWith("online:")) {
            val videoId = track.filePath.removePrefix("online:")
            val streamUrl = onlineRepository.getStreamUrl(videoId)
            track.copy(filePath = streamUrl)
        } else {
            track
        }
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
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
                _uiState.value = PlayerUiState.Success(currentTracks)
                mediaPlayerController.setPlaylistAndPlay(currentTracks, startIndex)
                prefetchAndKeepQueueAlive(startIndex)
            }
        }
    }

    fun playTrack(track: TrackEntity) {
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
                _uiState.value = PlayerUiState.Success(currentTracks)
                mediaPlayerController.setPlaylistAndPlay(currentTracks, 0)
            }

            if (isOnline) {
                val recommendations = onlineRepository.getRelatedRecommendations(track)
                val existingIds = currentTracks.map { it.id }.toSet()
                val uniqueRecs = recommendations.filter { it.id !in existingIds }
                if (uniqueRecs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentTracks = currentTracks + uniqueRecs
                        if (_uiState.value is PlayerUiState.Success) {
                            _uiState.value = PlayerUiState.Success(currentTracks)
                        }
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
        playTrack(track)
    }

    fun playTrackAtIndex(index: Int) {
        if (index >= 0 && index < currentTracks.size) {
            playTrack(currentTracks[index])
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

        _uiState.value = PlayerUiState.Success(currentTracks)
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

    fun getQueueTracks(): List<TrackEntity> = currentTracks
}
