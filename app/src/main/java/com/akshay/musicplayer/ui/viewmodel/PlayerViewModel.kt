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
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.data.remote.RetrofitClient

class PlayerViewModel(
    private val getLocalTracksUseCase: GetLocalTracksUseCase,
    private val mediaPlayerController: MediaPlayerController,
    private val playlistDao: com.akshay.musicplayer.data.db.PlaylistDao,
    private val sharedPreferences: android.content.SharedPreferences
) : ViewModel() {

    private val onlineRepository = OnlineMusicRepository(RetrofitClient.apiService)

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getSearchResults(): List<TrackEntity> {
        val q = _searchQuery.value.trim().lowercase()
        if (q.isEmpty()) return currentTracks
        return currentTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

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
                    sharedPreferences.edit()
                        .putLong("last_track_id", state.currentTrackId)
                        .putLong("last_position", state.currentPositionMs)
                        .apply()
                }

                // Check "after song" sleep mode on track transition
                if (oldTrackId != null && state.currentTrackId != null && oldTrackId != state.currentTrackId) {
                    checkSleepAfterSong(oldTrackId)
                }
                previousTrackId = state.currentTrackId
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

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        currentTracks = tracks
        _uiState.value = PlayerUiState.Success(currentTracks)
        viewModelScope.launch {
            mediaPlayerController.setPlaylistAndPlay(tracks, startIndex)
        }
    }

    fun playTrack(track: TrackEntity) {
        lastRequestedTrackId = track.id
        Log.d("MUESO_SYNC", "ViewModel playTrack: requested track.id=${track.id}")
        viewModelScope.launch {
            val index = currentTracks.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            mediaPlayerController.setPlaylistAndPlay(currentTracks, index)
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
