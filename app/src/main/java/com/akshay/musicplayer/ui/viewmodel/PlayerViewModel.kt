package com.akshay.musicplayer.ui.viewmodel

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val getLocalTracksUseCase: GetLocalTracksUseCase,
    private val mediaPlayerController: MediaPlayerController,
    private val playlistDao: com.akshay.musicplayer.data.db.PlaylistDao,
    private val sharedPreferences: android.content.SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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

    fun getCurrentTrackIndexState(): StateFlow<Int> = _playbackState.map { state ->
        val currentTrackId = state.currentTrackId ?: return@map 0
        currentTracks.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var currentTracks: List<TrackEntity> = emptyList()
    
    private val _playlists = MutableStateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<com.akshay.musicplayer.data.db.PlaylistEntity>> = _playlists.asStateFlow()

    init {
        loadLocalTracks()
        loadPlaylists()
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

    fun loadLocalTracks() {
        if (isLoaded) return
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

    private fun observePlaybackState() {
        viewModelScope.launch {
            mediaPlayerController.playbackState().collect { state ->
                val oldTrackId = _playbackState.value.currentTrackId
                _playbackState.value = state

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
        viewModelScope.launch {
            mediaPlayerController.setPlaylistAndPlay(tracks, startIndex)
        }
    }

    fun playTrack(track: TrackEntity) {
        viewModelScope.launch {
            val index = currentTracks.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            mediaPlayerController.setPlaylistAndPlay(currentTracks, index)
        }
    }

    fun playTrackIfChanged(track: TrackEntity) {
        if (_playbackState.value.currentTrackId != track.id) {
            // When swiping pagers manually, we just seek to that item in the current playlist
            // instead of reloading the entire playlist to keep it smooth
            val index = currentTracks.indexOfFirst { it.id == track.id }
            if (index >= 0) {
                // If playlist is already loaded, we ideally just want to seek to the index.
                // For simplicity, we will reload the playlist starting at the new track,
                // or we could add a `seekToDefaultPosition(index)` to MediaPlayerController.
                // Let's just use playTrack for now.
                playTrack(track)
            }
        }
    }

    fun playTrackAtIndex(index: Int) {
        if (index >= 0 && index < currentTracks.size) {
            playTrack(currentTracks[index])
        }
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
