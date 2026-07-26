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

import com.akshay.musicplayer.ui.viewmodel.managers.SettingsManager
import com.akshay.musicplayer.ui.viewmodel.managers.BackupManager
import com.akshay.musicplayer.ui.viewmodel.managers.SearchManager
import com.akshay.musicplayer.ui.viewmodel.managers.DownloadManager
import com.akshay.musicplayer.ui.viewmodel.managers.PlaylistManager
import com.akshay.musicplayer.ui.viewmodel.managers.SpotifyImportManager
import com.akshay.musicplayer.data.remote.SpotifyImportRepository

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

    val settingsManager = SettingsManager(sharedPreferences)
    val backupManager = BackupManager(sharedPreferences, playlistDao, onlinePlaylistDao, viewModelScope)
    val searchManager = SearchManager(onlineRepository, viewModelScope) { currentTracks }
    val downloadManager = DownloadManager(onlineRepository, { settingsManager.downloadFolder.value }, viewModelScope)
    val playlistManager = PlaylistManager(playlistDao, onlinePlaylistDao, onlineRepository, sharedPreferences, viewModelScope, { backupManager.markDirty() }, { currentTracks })
    val spotifyImportManager = SpotifyImportManager(SpotifyImportRepository(), onlineRepository, onlinePlaylistDao, viewModelScope, { backupManager.markDirty() })
    val updateManager = com.akshay.musicplayer.ui.viewmodel.managers.UpdateManager(viewModelScope)

    val isCheckingUpdate = updateManager.isChecking
    val updateInfo = updateManager.updateInfo
    val updateDownloadProgress = updateManager.downloadProgress
    val updateStatusMessage = updateManager.statusMessage
    fun checkForUpdates(context: android.content.Context, showToast: Boolean = false) = updateManager.checkForUpdates(context, showToast)
    fun downloadAndInstallUpdate(context: android.content.Context) = updateManager.downloadAndInstallApk(context)
    fun installPreBuildRelease(context: android.content.Context) = updateManager.installPreBuildRelease(context)

    val isDarkMode = settingsManager.isDarkMode
    val heroPlaylistId = settingsManager.heroPlaylistId
    val showOnLockscreen = settingsManager.showOnLockscreen
    val highRefreshRate = settingsManager.highRefreshRate
    val audioQuality = settingsManager.audioQuality
    val thumbnailQuality = settingsManager.thumbnailQuality
    val downloadQuality = settingsManager.downloadQuality
    val downloadFolder = settingsManager.downloadFolder
    val enableLyrics = settingsManager.enableLyrics
    val playButtonPosition = settingsManager.playButtonPosition
    val enableSponsorBlock = settingsManager.enableSponsorBlock
    val skipSponsor = settingsManager.skipSponsor
    val skipSelfPromo = settingsManager.skipSelfPromo
    val skipInteraction = settingsManager.skipInteraction
    val skipIntroOutro = settingsManager.skipIntroOutro
    val skipNonMusicOffTopic = settingsManager.skipNonMusicOffTopic

    fun setDarkMode(enabled: Boolean) = settingsManager.setDarkMode(enabled)
    fun setHeroPlaylistId(id: String) = settingsManager.setHeroPlaylistId(id)
    fun setShowOnLockscreen(enabled: Boolean) = settingsManager.setShowOnLockscreen(enabled)
    fun setHighRefreshRate(enabled: Boolean) = settingsManager.setHighRefreshRate(enabled)
    fun setAudioQuality(quality: String) = settingsManager.setAudioQuality(quality)
    fun setThumbnailQuality(quality: String) = settingsManager.setThumbnailQuality(quality)
    fun setDownloadQuality(quality: String) = settingsManager.setDownloadQuality(quality)
    fun setDownloadFolder(folder: String) = settingsManager.setDownloadFolder(folder)
    fun setEnableLyrics(enabled: Boolean) = settingsManager.setEnableLyrics(enabled)
    fun setPlayButtonPosition(position: String) = settingsManager.setPlayButtonPosition(position)
    fun setEnableSponsorBlock(enabled: Boolean) = settingsManager.setEnableSponsorBlock(enabled)
    fun setSkipSponsor(enabled: Boolean) = settingsManager.setSkipSponsor(enabled)
    fun setSkipSelfPromo(enabled: Boolean) = settingsManager.setSkipSelfPromo(enabled)
    fun setSkipInteraction(enabled: Boolean) = settingsManager.setSkipInteraction(enabled)
    fun setSkipIntroOutro(enabled: Boolean) = settingsManager.setSkipIntroOutro(enabled)
    fun setSkipNonMusicOffTopic(enabled: Boolean) = settingsManager.setSkipNonMusicOffTopic(enabled)

    val searchQuery = searchManager.searchQuery
    val isSearchingOnline = searchManager.isSearchingOnline
    val searchResults = searchManager.searchResults
    fun setSearchQuery(query: String) = searchManager.setSearchQuery(query)
    fun getSearchResults() = searchManager.getSearchResults()

    val hasUnbackedUpChanges = backupManager.hasUnbackedUpChanges
    val lastBackupTimestamp = backupManager.lastBackupTimestamp
    val isBackupInProgress = backupManager.isBackupInProgress
    val isRestoreInProgress = backupManager.isRestoreInProgress
    val googleAccountEmail = backupManager.googleAccountEmail
    val googleAccount = backupManager.googleAccount
    fun markDirty() = backupManager.markDirty()
    fun initGoogleDriveAccount(context: android.content.Context) = backupManager.initGoogleDriveAccount(context)
    fun setGoogleAccount(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) = backupManager.setGoogleAccount(account)
    fun connectAndBackupGoogleAccount(context: android.content.Context, email: String, onResult: (Boolean, String) -> Unit) = backupManager.connectAndBackupGoogleAccount(context, email, onResult)
    fun performDriveBackup(context: android.content.Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) = backupManager.performDriveBackup(context, onResult)
    fun performDriveRestore(context: android.content.Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) = backupManager.performDriveRestore(context, onResult)
    fun signOutGoogle(context: android.content.Context) = backupManager.signOutGoogle(context)

    val downloadStates = downloadManager.downloadStates
    fun downloadOnlineTrack(context: android.content.Context, track: com.akshay.musicplayer.domain.models.TrackEntity) = downloadManager.downloadOnlineTrack(context, track)
    fun cancelDownload(trackId: Long) = downloadManager.cancelDownload(trackId)

    val playlists = playlistManager.playlists
    val onlinePlaylists = playlistManager.onlinePlaylists
    fun createPlaylist(name: String) = playlistManager.createPlaylist(name)
    fun deletePlaylist(playlistId: Long) = playlistManager.deletePlaylist(playlistId)
    fun renamePlaylist(playlistId: Long, newName: String) = playlistManager.renamePlaylist(playlistId, newName)
    fun addTrackToPlaylist(playlistId: Long, trackId: Long) = playlistManager.addTrackToPlaylist(playlistId, trackId)
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = playlistManager.removeTrackFromPlaylist(playlistId, trackId)
    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistManager.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
    fun getPlaylistTracks(playlistId: Long) = playlistManager.getPlaylistTracks(playlistId)
    fun createOnlinePlaylist(name: String, description: String? = null) = playlistManager.createOnlinePlaylist(name, description)
    fun deleteOnlinePlaylist(playlistId: Long) = playlistManager.deleteOnlinePlaylist(playlistId)
    fun renameOnlinePlaylist(playlistId: Long, newName: String) = playlistManager.renameOnlinePlaylist(playlistId, newName)
    fun addTrackToOnlinePlaylist(playlistId: Long, track: com.akshay.musicplayer.domain.models.TrackEntity) = playlistManager.addTrackToOnlinePlaylist(playlistId, track)
    fun removeTrackFromOnlinePlaylist(playlistId: Long, trackId: Long) = playlistManager.removeTrackFromOnlinePlaylist(playlistId, trackId)
    fun moveTrackInOnlinePlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistManager.moveTrackInOnlinePlaylist(playlistId, fromIndex, toIndex)
    fun getOnlinePlaylistTracks(playlistId: Long) = playlistManager.getOnlinePlaylistTracks(playlistId)
    suspend fun getCuratedPlaylistTracks(query: String) = playlistManager.getCuratedPlaylistTracks(query)
    fun updateOnlinePlaylistDetails(playlistId: Long, name: String, description: String) = playlistManager.updateOnlinePlaylistDetails(playlistId, name, description)
    fun refreshAllPlaylistArtworks() = playlistManager.refreshAllPlaylistArtworks()
    suspend fun exportPlaylistsToJson(context: android.content.Context) = playlistManager.exportPlaylistsToJson(context)
    suspend fun importPlaylistsFromJson(context: android.content.Context, jsonString: String) = playlistManager.importPlaylistsFromJson(context, jsonString)

    // Spotify Import delegates
    val spotifyImportState = spotifyImportManager.importState
    val spotifyPlaylistData = spotifyImportManager.spotifyPlaylistData
    val spotifyMatchResults = spotifyImportManager.matchResults
    val spotifyMatchProgress = spotifyImportManager.matchProgress
    val spotifyErrorMessage = spotifyImportManager.errorMessage
    fun fetchSpotifyPlaylist(context: android.content.Context, url: String) = spotifyImportManager.fetchAndMatch(context, url)
    fun retrySpotifyMatch(index: Int, query: String) = spotifyImportManager.retryMatch(index, query)
    fun selectSpotifyMatch(index: Int, track: com.akshay.musicplayer.domain.models.TrackEntity) = spotifyImportManager.selectMatch(index, track)
    fun toggleSpotifyAlternatives(index: Int) = spotifyImportManager.toggleAlternatives(index)
    fun createSpotifyPlaylist() = spotifyImportManager.createPlaylist()
    fun resetSpotifyImport() {
        stopSpotifyPreview()
        spotifyImportManager.reset()
    }

    private var previewPlayer: com.akshay.musicplayer.ui.viewmodel.managers.SpotifyPreviewPlayer? = null

    private val _previewingTrackId = MutableStateFlow<Long?>(null)
    val previewingTrackId: StateFlow<Long?> = _previewingTrackId.asStateFlow()

    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading.asStateFlow()

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying.asStateFlow()

    private var previewJobs: List<Job> = emptyList()

    fun toggleSpotifyPreview(context: android.content.Context, track: TrackEntity) {
        val player = previewPlayer ?: com.akshay.musicplayer.ui.viewmodel.managers.SpotifyPreviewPlayer(
            context.applicationContext,
            onlineRepository,
            viewModelScope
        ).also {
            previewPlayer = it
            previewJobs.forEach { j -> j.cancel() }
            previewJobs = listOf(
                viewModelScope.launch { it.previewTrackId.collect { id -> _previewingTrackId.value = id } },
                viewModelScope.launch { it.isLoading.collect { loading -> _isPreviewLoading.value = loading } },
                viewModelScope.launch { it.isPlaying.collect { playing -> _isPreviewPlaying.value = playing } }
            )
        }
        player.togglePreview(track)
    }

    fun stopSpotifyPreview() {
        previewPlayer?.stop()
        _previewingTrackId.value = null
        _isPreviewLoading.value = false
        _isPreviewPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        previewJobs.forEach { j -> j.cancel() }
        previewPlayer?.release()
        previewPlayer = null
    }


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
    

    init {
        initRestoredTrackPreview()
        observePlaybackState()
        observeMediaEvents()
    }







    private val _isPlaylistContext = MutableStateFlow(false)
    val isPlaylistContext: StateFlow<Boolean> = _isPlaylistContext.asStateFlow()

    private val _playlistTrackCount = MutableStateFlow(0)
    val playlistTrackCount: StateFlow<Int> = _playlistTrackCount.asStateFlow()





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

    val pendingDeleteIntent = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingWriteIntent = MutableStateFlow<android.content.IntentSender?>(null)

    fun clearPendingDeleteIntent() {
        pendingDeleteIntent.value = null
    }

    fun clearPendingWriteIntent() {
        pendingWriteIntent.value = null
    }

    fun renameTrack(context: android.content.Context, trackId: Long, newTitle: String) {
        Log.d("MUESO_FILE_OP", "=== RENAME TRACK STARTED ===")
        Log.d("MUESO_FILE_OP", "Track ID: $trackId -> New Title: \"$newTitle\"")

        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
            val targetTrack = (currentState as? PlayerUiState.Success)?.tracks?.find { it.id == trackId }
            var updatedPath: String? = null

            if (targetTrack != null && targetTrack.filePath.isNotBlank()) {
                val contentUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    trackId
                )
                val oldFile = java.io.File(targetTrack.filePath)
                Log.d("MUESO_FILE_OP", "[1/4] Old file path: ${oldFile.absolutePath}, exists: ${oldFile.exists()}")
                var renamedDisk = false
                if (oldFile.exists()) {
                    val sanitizedTitle = newTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val ext = oldFile.extension.ifBlank { "mp3" }
                    val newFile = java.io.File(oldFile.parentFile, "$sanitizedTitle.$ext")
                    renamedDisk = oldFile.renameTo(newFile)
                    if (renamedDisk) {
                        updatedPath = newFile.absolutePath
                        Log.d("MUESO_FILE_OP", "[1/4] File successfully renamed on disk to: $updatedPath")
                    } else {
                        Log.e("MUESO_FILE_OP", "[1/4] File.renameTo() returned false for target: ${newFile.absolutePath}")
                    }
                }
                val pathToUse = updatedPath ?: targetTrack.filePath

                if (!renamedDisk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val pi = android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(contentUri))
                        pendingWriteIntent.value = pi.intentSender
                        Log.d("MUESO_FILE_OP", "[1/4] MediaStore.createWriteRequest created IntentSender")
                    } catch (e: Exception) {
                        Log.e("MUESO_FILE_OP", "[1/4] createWriteRequest error: ${e.message}")
                    }
                }

                // Update MP3 ID3 Tag via Chaquopy Mutagen if available
                try {
                    if (com.chaquo.python.Python.isStarted() && pathToUse.endsWith(".mp3", ignoreCase = true)) {
                        val py = com.chaquo.python.Python.getInstance()
                        val mutagen = py.getModule("mutagen.easyid3")
                        val audio = mutagen.callAttr("EasyID3", pathToUse)
                        audio.callAttr("__setitem__", "title", newTitle)
                        audio.callAttr("save")
                        Log.d("MUESO_FILE_OP", "[2/4] ID3 metadata tag saved successfully via Mutagen")
                    } else {
                        Log.d("MUESO_FILE_OP", "[2/4] Skip Mutagen ID3 tag update (Python started: ${com.chaquo.python.Python.isStarted()})")
                    }
                } catch (e: Exception) {
                    Log.e("MUESO_FILE_OP", "[2/4] ID3 rename error: ${e.message}", e)
                }

                // Update Android MediaStore
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.TITLE, newTitle)
                        if (updatedPath != null) {
                            put(android.provider.MediaStore.Audio.Media.DATA, updatedPath)
                        }
                    }
                    val updatedRows = context.contentResolver.update(contentUri, values, null, null)
                    Log.d("MUESO_FILE_OP", "[3/4] MediaStore updated rows: $updatedRows")
                } catch (rse: android.app.RecoverableSecurityException) {
                    Log.w("MUESO_FILE_OP", "[3/4] RecoverableSecurityException caught! Requesting user permission intent...")
                    pendingWriteIntent.value = rse.userAction.actionIntent.intentSender
                } catch (e: Exception) {
                    Log.e("MUESO_FILE_OP", "[3/4] MediaStore update error: ${e.message}", e)
                }

                // Trigger MediaScanner
                android.media.MediaScannerConnection.scanFile(context, arrayOf(pathToUse), null) { path, uri ->
                    Log.d("MUESO_FILE_OP", "[4/4] MediaScanner scan completed for path=$path, uri=$uri")
                }
            } else {
                Log.e("MUESO_FILE_OP", "Target track not found in state or filePath empty!")
            }

            withContext(Dispatchers.Main) {
                if (currentState is PlayerUiState.Success) {
                    val updatedTracks = currentState.tracks.map { track ->
                        if (track.id == trackId) track.copy(title = newTitle, filePath = updatedPath ?: track.filePath) else track
                    }
                    _uiState.value = PlayerUiState.Success(updatedTracks)
                    if (currentTracks.isNotEmpty()) {
                        currentTracks = currentTracks.map { if (it.id == trackId) it.copy(title = newTitle, filePath = updatedPath ?: it.filePath) else it }
                    }
                    Log.d("MUESO_FILE_OP", "=== RENAME TRACK FINISHED (UI Updated) ===")
                }
            }
        }
    }

    fun deleteTrack(context: android.content.Context, track: TrackEntity) {
        Log.d("MUESO_FILE_OP", "=== DELETE TRACK STARTED ===")
        Log.d("MUESO_FILE_OP", "Track ID: ${track.id}, Title: \"${track.title}\", FilePath: ${track.filePath}")

        viewModelScope.launch(Dispatchers.IO) {
            val contentUri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id
            )

            // 1. Try direct File.delete() first
            var directDeleted = false
            if (track.filePath.isNotBlank()) {
                val file = java.io.File(track.filePath)
                val existsBefore = file.exists()
                Log.d("MUESO_FILE_OP", "[1/4] Direct file exists before delete: $existsBefore (${file.absolutePath})")
                if (existsBefore) {
                    directDeleted = file.delete()
                    Log.d("MUESO_FILE_OP", "[1/4] Direct file.delete() returned: $directDeleted")
                }
            }

            if (!directDeleted) {
                // 2. Perform ContentResolver delete or catch RecoverableSecurityException for system consent dialog
                try {
                    val rows = context.contentResolver.delete(contentUri, null, null)
                    Log.d("MUESO_FILE_OP", "[2/4] ContentResolver delete returned rows: $rows")
                    if (rows == 0 && track.filePath.isNotBlank()) {
                        val rowsData = context.contentResolver.delete(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                            arrayOf(track.filePath)
                        )
                        Log.d("MUESO_FILE_OP", "[2/4] ContentResolver delete by DATA column rows: $rowsData")
                    }
                } catch (rse: android.app.RecoverableSecurityException) {
                    Log.w("MUESO_FILE_OP", "[2/4] RecoverableSecurityException caught! Requesting user permission intent...")
                    pendingDeleteIntent.value = rse.userAction.actionIntent.intentSender
                } catch (e: SecurityException) {
                    Log.w("MUESO_FILE_OP", "[2/4] SecurityException: ${e.message}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val pi = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(contentUri))
                            pendingDeleteIntent.value = pi.intentSender
                            Log.d("MUESO_FILE_OP", "[2/4] MediaStore.createDeleteRequest created IntentSender successfully!")
                        } catch (ex: Exception) {
                            Log.e("MUESO_FILE_OP", "[2/4] createDeleteRequest failed: ${ex.message}", ex)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MUESO_FILE_OP", "[2/4] Delete error: ${e.message}", e)
                }
            }

            // 3. Trigger MediaScanner to rescan file path
            if (track.filePath.isNotBlank()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(track.filePath),
                    null
                ) { path, uri ->
                    Log.d("MUESO_FILE_OP", "[3/4] MediaScanner scan completed for path=$path, uri=$uri")
                }
            }

            withContext(Dispatchers.Main) {
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Success) {
                    val updatedTracks = currentState.tracks.filter { it.id != track.id }
                    _uiState.value = if (updatedTracks.isEmpty()) PlayerUiState.Empty else PlayerUiState.Success(updatedTracks)
                    if (currentTracks.isNotEmpty()) {
                        currentTracks = currentTracks.filter { it.id != track.id }
                    }
                }
                Log.d("MUESO_FILE_OP", "=== DELETE TRACK FINISHED (UI Updated) ===")
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
                val shouldSkipCategory = enableSponsorBlock.value && when (seg.category) {
                    "sponsor" -> skipSponsor.value
                    "selfpromo" -> skipSelfPromo.value
                    "interaction" -> skipInteraction.value
                    "intro", "outro" -> skipIntroOutro.value
                    "music_offtopic", "filler" -> skipNonMusicOffTopic.value
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
                        val isRecentUserAction = (System.currentTimeMillis() - lastUserSkipTime) < 2500
                        val isCancelError = event.message.contains("cancel", ignoreCase = true) || event.message.contains("interrupted", ignoreCase = true)
                        if (isRecentUserAction || isCancelError) {
                            Log.d("MUESO_STREAM", "Ignoring transient error during rapid track switch: ${event.message}")
                        } else {
                            Log.w("MUESO_STREAM", "Playback error encountered: ${event.message}. Auto-advancing to next track.")
                            delay(500)
                            playNextTrack()
                        }
                    }
                }
            }
        }
    }

    private var lastUserSkipTime: Long = 0

    fun playNextTrack() {
        lastUserSkipTime = System.currentTimeMillis()
        viewModelScope.launch {
            mediaPlayerController.seekToNext()
        }
    }

    fun setOfflineLibraryTab(tabIndex: Int) {
        _offlineLibraryTab.value = tabIndex
        sharedPreferences.edit().putInt("offline_library_tab", tabIndex).apply()
    }

    fun playPreviousTrack() {
        lastUserSkipTime = System.currentTimeMillis()
        viewModelScope.launch {
            mediaPlayerController.seekToPrevious()
        }
    }

    private val _isResolvingTrack = MutableStateFlow(false)
    val isResolvingTrack: StateFlow<Boolean> = _isResolvingTrack.asStateFlow()

    private val _resolvingTrackTitle = MutableStateFlow<String?>(null)
    val resolvingTrackTitle: StateFlow<String?> = _resolvingTrackTitle.asStateFlow()

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        lastUserSkipTime = System.currentTimeMillis()
        cancelRestoration()
        val target = if (startIndex in tracks.indices) tracks[startIndex] else null
        val isOnline = target != null && target.filePath.startsWith("online:")
        if (isOnline) {
            _resolvingTrackTitle.value = target?.title
            _isResolvingTrack.value = true
        }
        currentTracks = tracks.toList()
        viewModelScope.launch(Dispatchers.IO) {
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
        val isOnline = track.filePath.startsWith("online:")
        if (isOnline) {
            _resolvingTrackTitle.value = track.title
            _isResolvingTrack.value = true
        }
        currentTracks = listOf(track)
        Log.d("MUESO_SYNC", "ViewModel playTrack: requested track.id=${track.id}")
        viewModelScope.launch(Dispatchers.IO) {
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



    // --- Hero Playlist & Online Playlist Management ---



    // --- Audio, Thumbnail, Download & App Settings ---
















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

        playlistManager.refreshAllPlaylistArtworks()
        loadLocalTracks(forceReload = true)
        android.widget.Toast.makeText(context, "App refreshed! Caches cleared & playlist covers updated.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun getQueueTracks(): List<TrackEntity> = currentTracks

}
