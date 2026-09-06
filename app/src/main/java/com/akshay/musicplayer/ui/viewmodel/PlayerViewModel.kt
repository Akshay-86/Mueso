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
import com.akshay.musicplayer.media.notification.NotificationHelper

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
    val backupManager = BackupManager(sharedPreferences, playlistDao, onlinePlaylistDao, viewModelScope, settingsManager)
    val searchManager = SearchManager(onlineRepository, viewModelScope) { currentTracks }
    val downloadManager = DownloadManager(
        onlineRepository = onlineRepository,
        getDownloadFolder = { settingsManager.downloadFolder.value },
        getCurrentTrack = {
            val id = _playbackState.value.currentTrackId
            if (id != null) currentTracks.firstOrNull { it.id == id } else null
        },
        getSavedLyrics = { trackId -> getSavedCustomLyrics(trackId) },
        coroutineScope = viewModelScope
    )
    val playlistManager = PlaylistManager(playlistDao, onlinePlaylistDao, onlineRepository, sharedPreferences, viewModelScope, { backupManager.markDirty() }, { currentTracks })
    val youtubeAuthManager = com.akshay.musicplayer.ui.viewmodel.managers.YouTubeAuthManager(
        context = com.akshay.musicplayer.AppContainer.getContext(),
        onlineRepo = onlineRepository,
        coroutineScope = viewModelScope
    )
    val spotifyImportManager = SpotifyImportManager(
        spotifyRepo = SpotifyImportRepository(),
        onlineRepo = onlineRepository,
        onlinePlaylistDao = onlinePlaylistDao,
        coroutineScope = viewModelScope,
        markDirty = { backupManager.markDirty() },
        onYouTubeRefresh = { youtubeAuthManager.refreshLibrary() }
    )
    val updateManager = com.akshay.musicplayer.ui.viewmodel.managers.UpdateManager(viewModelScope)

    val isYouTubeLoggedIn = youtubeAuthManager.isLoggedIn
    val youtubeUserName = youtubeAuthManager.userName
    val youtubeUserHandle = youtubeAuthManager.userHandle
    val youtubeUserAvatar = youtubeAuthManager.userAvatar
    val youtubeSavedAccounts = youtubeAuthManager.savedAccounts
    val youtubeLikedSongs = youtubeAuthManager.likedSongs
    val youtubeUserPlaylists = youtubeAuthManager.userPlaylists
    val isLoadingYouTubeLibrary = youtubeAuthManager.isLoadingLibrary

    fun saveYouTubeCookies(cookieString: String, name: String? = null, avatar: String? = null) = youtubeAuthManager.saveCookies(cookieString, name, avatar)
    fun switchYouTubeAccount(accountId: String) = youtubeAuthManager.switchAccount(accountId)
    fun removeYouTubeAccount(accountId: String) = youtubeAuthManager.removeAccount(accountId)
    fun logoutYouTube() = youtubeAuthManager.logout()
    fun refreshYouTubeLibrary() = youtubeAuthManager.refreshLibrary()
    fun toggleYouTubeLike(track: TrackEntity, isLiked: Boolean) = youtubeAuthManager.toggleLikeSong(track, isLiked)

    private val _exploreShelves = kotlinx.coroutines.flow.MutableStateFlow<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf>>(emptyList())
    val exploreShelves: kotlinx.coroutines.flow.StateFlow<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf>> = _exploreShelves

    private val _chartsShelves = kotlinx.coroutines.flow.MutableStateFlow<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf>>(emptyList())
    val chartsShelves: kotlinx.coroutines.flow.StateFlow<List<com.akshay.musicplayer.data.remote.innertube.InnerTubeShelf>> = _chartsShelves

    private val _selectedMoodCategory = kotlinx.coroutines.flow.MutableStateFlow<String>("All")
    val selectedMoodCategory: kotlinx.coroutines.flow.StateFlow<String> = _selectedMoodCategory

    private val _isExploreLoading = kotlinx.coroutines.flow.MutableStateFlow<Boolean>(false)
    val isExploreLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _isExploreLoading

    fun selectMoodCategory(mood: String) {
        if (_selectedMoodCategory.value == mood) return
        _selectedMoodCategory.value = mood
        loadExploreAndCharts(mood = mood, force = true)
    }

    fun loadExploreAndCharts(mood: String = _selectedMoodCategory.value, force: Boolean = false) {
        if (!force && _exploreShelves.value.isNotEmpty() && _selectedMoodCategory.value == mood) {
            Log.d("MUESO_EXPLORE", "loadExploreAndCharts skipped (already loaded for $mood)")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isExploreLoading.value = true
            try {
                val isAuthed = youtubeAuthManager.isLoggedIn.value
                Log.d("MUESO_EXPLORE", "loadExploreAndCharts called (mood: $mood, isAuthed: $isAuthed, force: $force)")
                val explore = if (mood == "All") {
                    if (isAuthed) {
                        onlineRepository.innerTube.getHomeFeedShelves()
                    } else {
                        onlineRepository.fetchExploreShelves()
                    }
                } else {
                    onlineRepository.fetchMoodShelves(mood)
                }
                _exploreShelves.value = explore
                if (mood == "All") {
                    val charts = onlineRepository.fetchChartsShelves()
                    _chartsShelves.value = charts
                } else {
                    _chartsShelves.value = emptyList()
                }
                Log.d("MUESO_EXPLORE", "Loaded explore shelves (${explore.size}): ${explore.map { it.title }}")
            } catch (e: Exception) {
                Log.e("MUESO_EXPLORE", "Failed to load explore and charts", e)
            } finally {
                _isExploreLoading.value = false
            }
        }
    }

    val isCheckingUpdate = updateManager.isChecking
    val updateInfo = updateManager.updateInfo
    val updateDownloadProgress = updateManager.downloadProgress
    val updateStatusMessage = updateManager.statusMessage
    fun checkForUpdates(context: android.content.Context, showToast: Boolean = false) = updateManager.checkForUpdates(context, showToast)
    fun downloadAndInstallUpdate(context: android.content.Context) = updateManager.downloadAndInstallApk(context)
    fun installPreBuildRelease(context: android.content.Context) = updateManager.installPreBuildRelease(context)
    fun resetUpdateState() = updateManager.resetUpdateState()
    fun checkAndResumePendingInstall(context: android.content.Context) = updateManager.checkAndResumePendingInstall(context)

    val isDarkMode = settingsManager.isDarkMode
    val heroPlaylistId = settingsManager.heroPlaylistId
    val showOnLockscreen = settingsManager.showOnLockscreen
    val highRefreshRate = settingsManager.highRefreshRate
    val audioQuality = settingsManager.audioQuality
    val thumbnailQuality = settingsManager.thumbnailQuality
    val downloadQuality = settingsManager.downloadQuality
    val downloadFolder = settingsManager.downloadFolder
    val enableLyrics = settingsManager.enableLyrics
    val enableVideoMode = settingsManager.enableVideoMode
    val embedLyricsInDownload = settingsManager.embedLyricsInDownload
    val preferredLanguage = settingsManager.preferredLanguage
    val playButtonPosition = settingsManager.playButtonPosition
    val enableSponsorBlock = settingsManager.enableSponsorBlock
    val skipSponsor = settingsManager.skipSponsor
    val skipSelfPromo = settingsManager.skipSelfPromo
    val skipInteraction = settingsManager.skipInteraction
    val skipIntroOutro = settingsManager.skipIntroOutro
    val skipNonMusicOffTopic = settingsManager.skipNonMusicOffTopic

    private val _isVideoModeActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isVideoModeActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isVideoModeActive.asStateFlow()
    fun setVideoModeActive(active: Boolean) {
        _isVideoModeActive.value = active
    }

    fun getOnlinePlayerView(): com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView? = mediaPlayerController.getOnlinePlayerView()

    fun setDarkMode(enabled: Boolean) = settingsManager.setDarkMode(enabled)
    fun setHeroPlaylistId(id: String) = settingsManager.setHeroPlaylistId(id)
    fun setShowOnLockscreen(enabled: Boolean) = settingsManager.setShowOnLockscreen(enabled)
    fun setHighRefreshRate(enabled: Boolean) = settingsManager.setHighRefreshRate(enabled)
    fun setAudioQuality(quality: String) = settingsManager.setAudioQuality(quality)
    fun setThumbnailQuality(quality: String) = settingsManager.setThumbnailQuality(quality)
    fun setDownloadQuality(quality: String) = settingsManager.setDownloadQuality(quality)
    fun setDownloadFolder(folder: String) = settingsManager.setDownloadFolder(folder)
    fun setEnableLyrics(enabled: Boolean) = settingsManager.setEnableLyrics(enabled)
    fun setEnableVideoMode(enabled: Boolean) = settingsManager.setEnableVideoMode(enabled)
    fun setEmbedLyricsInDownload(enabled: Boolean) = settingsManager.setEmbedLyricsInDownload(enabled)
    fun setPreferredLanguage(language: String) {
        settingsManager.setPreferredLanguage(language)
        playlistManager.clearCuratedCache()
    }
    fun setPlayButtonPosition(position: String) = settingsManager.setPlayButtonPosition(position)
    fun setEnableSponsorBlock(enabled: Boolean) = settingsManager.setEnableSponsorBlock(enabled)
    fun setSkipSponsor(enabled: Boolean) = settingsManager.setSkipSponsor(enabled)
    fun setSkipSelfPromo(enabled: Boolean) = settingsManager.setSkipSelfPromo(enabled)
    fun setSkipInteraction(enabled: Boolean) = settingsManager.setSkipInteraction(enabled)
    fun setSkipIntroOutro(enabled: Boolean) = settingsManager.setSkipIntroOutro(enabled)
    fun setSkipNonMusicOffTopic(enabled: Boolean) = settingsManager.setSkipNonMusicOffTopic(enabled)

    val searchQuery = searchManager.searchQuery
    val searchCategory = searchManager.searchCategory
    val isSearchingOnline = searchManager.isSearchingOnline
    val searchResults = searchManager.searchResults
    val artistResults = searchManager.artistResults
    val playlistResults = searchManager.playlistResults
    fun setSearchQuery(query: String) = searchManager.setSearchQuery(query)
    fun setSearchCategory(category: String) = searchManager.setSearchCategory(category)
    fun getSearchResults() = searchManager.getSearchResults()

    private val _selectedArtistPage = MutableStateFlow<com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage?>(null)
    val selectedArtistPage: StateFlow<com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage?> = _selectedArtistPage.asStateFlow()

    private val _isLoadingArtistPage = MutableStateFlow(false)
    val isLoadingArtistPage: StateFlow<Boolean> = _isLoadingArtistPage.asStateFlow()

    private var loadArtistJob: Job? = null

    fun openArtist(browseId: String, initialName: String? = null, initialThumb: String? = null) {
        loadArtistJob?.cancel()
        _isLoadingArtistPage.value = true
        _selectedArtistPage.value = com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage(
            id = browseId,
            name = initialName ?: "Loading...",
            thumbnailUrl = initialThumb,
            bannerUrl = initialThumb
        )
        loadArtistJob = viewModelScope.launch {
            try {
                val page = onlineRepository.fetchArtistPage(browseId)
                if (page != null) {
                    _selectedArtistPage.value = page
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading artist $browseId", e)
            } finally {
                _isLoadingArtistPage.value = false
            }
        }
    }

    fun closeArtist() {
        loadArtistJob?.cancel()
        _selectedArtistPage.value = null
        _isLoadingArtistPage.value = false
    }

    fun playArtistRadio(artistPage: com.akshay.musicplayer.data.remote.innertube.InnerTubeArtistPage) {
        viewModelScope.launch {
            if (!artistPage.radioVideoId.isNullOrBlank()) {
                val track = TrackEntity(
                    id = artistPage.radioVideoId.hashCode().toLong(),
                    title = "${artistPage.name} Radio",
                    artist = artistPage.name,
                    album = "YouTube Music",
                    duration = 0L,
                    albumId = 0L,
                    filePath = "online:${artistPage.radioVideoId}",
                    artworkUrl = artistPage.thumbnailUrl
                )
                playTrack(track)
            } else if (artistPage.topSongs.isNotEmpty()) {
                playOnlinePlaylist(artistPage.topSongs.map { it.toTrackEntity() }.shuffled(), 0)
            }
        }
    }

    fun playArtistTopSongs(songs: List<com.akshay.musicplayer.data.remote.innertube.InnerTubeTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        val entities = songs.map { it.toTrackEntity() }
        if (entities.isNotEmpty()) {
            if (shuffle) {
                playOnlinePlaylist(entities.shuffled(), 0)
            } else {
                playOnlinePlaylist(entities, startIndex.coerceIn(0, entities.size - 1))
            }
        }
    }


    val hasUnbackedUpChanges = backupManager.hasUnbackedUpChanges
    val lastBackupTimestamp = backupManager.lastBackupTimestamp
    val lastBackupSizeBytes = backupManager.lastBackupSizeBytes
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

    fun getTrackBackgroundBias(track: com.akshay.musicplayer.domain.models.TrackEntity?): Float {
        if (track == null) return 0f
        val idKey = "bg_bias_${track.id}"
        if (sharedPreferences.contains(idKey)) {
            return sharedPreferences.getFloat(idKey, 0f)
        }
        val videoId = onlineRepository.extractVideoId(track)
        val videoKey = "bg_bias_$videoId"
        if (sharedPreferences.contains(videoKey)) {
            return sharedPreferences.getFloat(videoKey, 0f)
        }
        val titleArtistKey = "bg_bias_${track.title.trim().lowercase()}_${track.artist.trim().lowercase()}"
        return sharedPreferences.getFloat(titleArtistKey, 0f)
    }

    fun saveTrackBackgroundBias(track: com.akshay.musicplayer.domain.models.TrackEntity?, bias: Float) {
        if (track == null) return
        val idKey = "bg_bias_${track.id}"
        val videoId = onlineRepository.extractVideoId(track)
        val videoKey = "bg_bias_$videoId"
        val titleArtistKey = "bg_bias_${track.title.trim().lowercase()}_${track.artist.trim().lowercase()}"
        
        sharedPreferences.edit()
            .putFloat(idKey, bias)
            .putFloat(videoKey, bias)
            .putFloat(titleArtistKey, bias)
            .apply()
        
        backupManager.markDirty()
    }

    val downloadStates = downloadManager.downloadStates
    fun downloadOnlineTrack(context: android.content.Context, track: com.akshay.musicplayer.domain.models.TrackEntity) = downloadManager.downloadOnlineTrack(context, track)
    fun cancelDownload(trackId: Long) = downloadManager.cancelDownload(trackId)

    val playlists = playlistManager.playlists
    val onlinePlaylists = playlistManager.onlinePlaylists
    fun createPlaylist(name: String) = playlistManager.createPlaylist(name)
    fun deletePlaylist(playlistId: Long) = playlistManager.deletePlaylist(playlistId)
    fun renamePlaylist(playlistId: Long, newName: String) = playlistManager.renamePlaylist(playlistId, newName)
    fun addTrackToPlaylist(playlistId: Long, trackId: Long) = playlistManager.addTrackToPlaylist(playlistId, trackId)
    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) = playlistManager.addTracksToPlaylist(playlistId, trackIds)
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = playlistManager.removeTrackFromPlaylist(playlistId, trackId)
    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistManager.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
    fun getPlaylistTracks(playlistId: Long) = playlistManager.getPlaylistTracks(playlistId)
    fun touchPlaylist(playlistId: Long) = playlistManager.touchPlaylist(playlistId)
    fun touchOnlinePlaylist(playlistId: Long) = playlistManager.touchOnlinePlaylist(playlistId)
    fun createOnlinePlaylist(name: String, description: String? = null) = playlistManager.createOnlinePlaylist(name, description)
    fun deleteOnlinePlaylist(playlistId: Long) = playlistManager.deleteOnlinePlaylist(playlistId)
    fun renameOnlinePlaylist(playlistId: Long, newName: String) = playlistManager.renameOnlinePlaylist(playlistId, newName)
    fun addTrackToOnlinePlaylist(playlistId: Long, track: com.akshay.musicplayer.domain.models.TrackEntity) = playlistManager.addTrackToOnlinePlaylist(playlistId, track)
    fun addTracksToOnlinePlaylist(playlistId: Long, tracks: List<com.akshay.musicplayer.domain.models.TrackEntity>) = playlistManager.addTracksToOnlinePlaylist(playlistId, tracks)
    fun removeTrackFromOnlinePlaylist(playlistId: Long, trackId: Long) = playlistManager.removeTrackFromOnlinePlaylist(playlistId, trackId)
    fun moveTrackInOnlinePlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistManager.moveTrackInOnlinePlaylist(playlistId, fromIndex, toIndex)
    fun getOnlinePlaylistTracks(playlistId: Long) = playlistManager.getOnlinePlaylistTracks(playlistId)
    suspend fun getCuratedPlaylistTracks(query: String) = playlistManager.getCuratedPlaylistTracks(query)
    fun getPlaylistDescription(playlistId: String): String? = onlineRepository.getPlaylistDescription(playlistId)
    suspend fun fetchPlaylistDescription(playlistId: String): String? = onlineRepository.fetchPlaylistDescription(playlistId)
    fun updateOnlinePlaylistDetails(playlistId: Long, name: String, description: String) = playlistManager.updateOnlinePlaylistDetails(playlistId, name, description)
    fun refreshAllPlaylistArtworks() = playlistManager.refreshAllPlaylistArtworks()
    suspend fun exportPlaylistsToJson(context: android.content.Context) = playlistManager.exportPlaylistsToJson(context)
    suspend fun importPlaylistsFromJson(context: android.content.Context, jsonString: String) = playlistManager.importPlaylistsFromJson(context, jsonString)
    fun addTrackToYouTubePlaylist(playlist: com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist, track: com.akshay.musicplayer.domain.models.TrackEntity, onResult: (Boolean) -> Unit = {}) =
        youtubeAuthManager.addTrackToYouTubePlaylist(playlist, track, onResult)
    fun createYouTubePlaylist(title: String, description: String = "", onResult: (String?) -> Unit = {}) =
        youtubeAuthManager.createYouTubePlaylist(title, description, onResult)
    fun editYouTubePlaylistDetails(playlistId: String, newName: String, newDescription: String = "", onResult: (Boolean) -> Unit = {}) =
        youtubeAuthManager.editYouTubePlaylistDetails(playlistId, newName, newDescription, onResult)
    fun renameYouTubePlaylist(playlistId: String, newName: String, onResult: (Boolean) -> Unit = {}) =
        youtubeAuthManager.renameYouTubePlaylist(playlistId, newName, onResult)
    fun deleteYouTubePlaylist(playlistId: String, onResult: (Boolean) -> Unit = {}) =
        youtubeAuthManager.deleteYouTubePlaylist(playlistId, onResult)
    fun removeTrackFromYouTubePlaylist(playlistId: String, videoId: String, onResult: (Boolean) -> Unit = {}) =
        youtubeAuthManager.removeTrackFromYouTubePlaylist(playlistId, videoId, onResult)

    // Spotify Import delegates

    val spotifyImportState = spotifyImportManager.importState
    val spotifyPlaylistData = spotifyImportManager.spotifyPlaylistData
    val spotifyMatchResults = spotifyImportManager.matchResults
    val spotifyMatchProgress = spotifyImportManager.matchProgress
    val spotifyErrorMessage = spotifyImportManager.errorMessage
    val spotifyCreatedDestination = spotifyImportManager.createdDestination
    fun fetchSpotifyPlaylist(context: android.content.Context, url: String) = spotifyImportManager.fetchAndMatch(context, url)
    fun retrySpotifyMatch(index: Int, query: String) = spotifyImportManager.retryMatch(index, query)
    fun selectSpotifyMatch(index: Int, track: com.akshay.musicplayer.domain.models.TrackEntity) = spotifyImportManager.selectMatch(index, track)
    fun toggleSpotifyAlternatives(index: Int) = spotifyImportManager.toggleAlternatives(index)
    fun createSpotifyPlaylist() = spotifyImportManager.createPlaylist()
    fun createSpotifyYouTubePlaylist(onComplete: (Boolean) -> Unit = {}) = spotifyImportManager.createYouTubePlaylist(onComplete)
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

    // Shuffle mode state
    private val _isShuffleModeEnabled = MutableStateFlow(false)
    val isShuffleModeEnabled: StateFlow<Boolean> = _isShuffleModeEnabled.asStateFlow()

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

    private var currentTracks: List<TrackEntity> = emptyList()
        set(value) {
            field = value
            _activeQueue.value = value
        }

    val currentTrackIndexState: StateFlow<Int> = combine(_playbackState, _activeQueue) { state, queue ->
        val currentTrackId = state.currentTrackId
        if (currentTrackId != null) {
            val index = queue.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 }
            if (index != null) {
                Log.d("MUESO_SYNC", "ViewModel currentTrackIndexState: calculated index $index for track $currentTrackId")
                return@combine index
            }
        }
        // Use the pending requested track as a secondary lookup (e.g. user clicked song #4 but ExoPlayer hasn't confirmed yet)
        val requestedId = lastRequestedTrackId
        if (requestedId != null) {
            val pendingIndex = queue.indexOfFirst { it.id == requestedId }.takeIf { it >= 0 }
            if (pendingIndex != null) {
                Log.d("MUESO_SYNC", "ViewModel currentTrackIndexState: using pending requested index $pendingIndex for track $requestedId")
                return@combine pendingIndex
            }
        }
        val restoredIndex = getRestoredTrackIndex()
        Log.d("MUESO_SYNC", "ViewModel currentTrackIndexState: fallback to restored index $restoredIndex")
        restoredIndex
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getRestoredTrackIndex())

    val upcomingTrackCountState: StateFlow<Int> = combine(_playbackState, _activeQueue) { state, queue ->
        val currentId = state.currentTrackId
        if (currentId != null) {
            val currentIndex = queue.indexOfFirst { it.id == currentId }
            if (currentIndex >= 0) (queue.size - currentIndex - 1).coerceAtLeast(0) else 0
        } else {
            0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Guard against re-entrant play calls during async IPC transitions
    var lastRequestedTrackId: Long? = null
        private set

    // Search

    private val _isPlaylistContext = MutableStateFlow(false)
    val isPlaylistContext: StateFlow<Boolean> = _isPlaylistContext.asStateFlow()

    private val _playlistTrackCount = MutableStateFlow(0)
    val playlistTrackCount: StateFlow<Int> = _playlistTrackCount.asStateFlow()

    /** Read the restored track index synchronously — used for initial pager page */
    fun getRestoredTrackIndex(): Int {
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        if (lastTrackId == -1L) return 0
        
        val tracks = currentTracks ?: emptyList()
        if (tracks.isNotEmpty()) {
            return tracks.indexOfFirst { it.id == lastTrackId }.takeIf { it >= 0 } ?: 0
        }

        val isPlaylist = sharedPreferences.getBoolean("last_is_playlist_context", false)
        val queueJson = sharedPreferences.getString("last_playlist_queue_json", null)
        if (isPlaylist && !queueJson.isNullOrBlank()) {
            try {
                val jsonArr = org.json.JSONArray(queueJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    if (obj.optLong("id", -1L) == lastTrackId) {
                        return i
                    }
                }
            } catch (_: Exception) {}
        }
        return 0
    }








    private fun initRestoredTrackPreview() {
        val lastTrackId = sharedPreferences.getLong("last_track_id", -1L)
        if (lastTrackId == -1L) return

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
                    _isPlaylistContext.value = true
                    _playlistTrackCount.value = savedCount.coerceAtLeast(restoredPlaylistTracks.size)
                    currentTracks = restoredPlaylistTracks
                    return
                }
            } catch (e: Exception) {
                Log.w("MUESO_RESTORE", "Failed to synchronously restore preview queue from JSON cache", e)
            }
        }

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
    

    init {
        initRestoredTrackPreview()
        observePlaybackState()
        observeMediaEvents()
        youtubeAuthManager.onSessionChanged = {
            loadExploreAndCharts()
        }
        loadExploreAndCharts()
        NotificationHelper.onPlayDownloadedTrackRequested = { filePath ->
            playLocalTrackByPath(filePath)
        }
    }

    fun playLocalTrackByPath(filePath: String) {
        viewModelScope.launch {
            loadLocalTracks(forceReload = true)
            val currentState = _uiState.value
            if (currentState is PlayerUiState.Success) {
                val match = currentState.tracks.find { it.filePath == filePath }
                if (match != null) {
                    playTrack(match)
                }
            }
        }
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
                val artwork = track.artworkUrl ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                track.copy(filePath = "online:$videoId", artworkUrl = artwork)
            } else {
                track
            }
        } else {
            track
        }
    }

    private suspend fun reResolveTrackStream(track: TrackEntity): TrackEntity {
        val originalVideoId = if (track.filePath.startsWith("online:")) {
            track.filePath.removePrefix("online:")
        } else if (track.artworkUrl != null && track.artworkUrl.contains("/vi/")) {
            track.artworkUrl.substringAfter("/vi/").substringBefore("/")
        } else ""

        if (originalVideoId.isNotBlank()) {
            val freshStreamUrl = onlineRepository.getStreamUrl(originalVideoId, forceRefresh = true)
            if (freshStreamUrl.isNotBlank() && freshStreamUrl.startsWith("http")) {
                val artwork = track.artworkUrl ?: "https://i.ytimg.com/vi/$originalVideoId/hq720.jpg"
                return track.copy(filePath = freshStreamUrl, artworkUrl = artwork)
            }
        }
        return track
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
                            .putLong("last_track_duration", if (curTrack.duration > 0L) curTrack.duration else state.durationMs)
                            .putBoolean("last_track_is_online", isOnline)
                            .putLong("last_position", state.currentPositionMs)
                            .apply()

                        saveQueueToPreferences()
                        fetchLyricsForTrack(curTrack)
                        // Only prefetch on actual track transitions, not every position update
                        if (oldTrackId != state.currentTrackId) {
                            val newTrackId = state.currentTrackId
                            _lyricsOffsetMs.value = sharedPreferences.getLong("lyrics_offset_$newTrackId", 0L)
                            lastPrefetchedNextIndex = -1
                            prefetchAndKeepQueueAlive(currentTrackIdx)
                        }
                        checkNearEndPrefetch(currentTrackIdx, state.currentPositionMs, state.durationMs)
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

    fun saveCurrentPlaybackPosition() {
        val state = _playbackState.value
        val curTrack = currentTracks.firstOrNull { it.id == state.currentTrackId }
        if (state.currentTrackId != null && curTrack != null) {
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
                .putLong("last_track_duration", if (curTrack.duration > 0L) curTrack.duration else state.durationMs)
                .putBoolean("last_track_is_online", isOnline)
                .putLong("last_position", state.currentPositionMs)
                .apply()
            Log.d("MUESO_RESTORE", "Persisted track '${curTrack.title}' at position: ${state.currentPositionMs}ms")
        }
    }

    private fun isStreamUrlExpired(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val expireParam = url.substringAfter("expire=", "").substringBefore("&")
        val expireSec = expireParam.toLongOrNull() ?: return false
        val currentSec = System.currentTimeMillis() / 1000
        return currentSec >= (expireSec - 60)
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

                val isOnline = t.filePath.startsWith("online:") || t.filePath.startsWith("http")
                val videoId = if (t.filePath.startsWith("online:")) {
                    t.filePath.removePrefix("online:")
                } else if (t.artworkUrl != null && t.artworkUrl.contains("/vi/")) {
                    t.artworkUrl.substringAfter("/vi/").substringBefore("/")
                } else ""
                val persistentPath = if (isOnline && videoId.isNotBlank()) "online:$videoId" else t.filePath

                obj.put("filePath", persistentPath)
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

    private var activeCurrentStreamJob: Job? = null
    private var activeNextStreamJob: Job? = null
    private var lastPrefetchTrackId: Long? = null

    private var lastPrefetchedNextIndex: Int = -1

    private fun checkNearEndPrefetch(currentIndex: Int, currentPositionMs: Long, durationMs: Long) {
        val nextIndex = currentIndex + 1
        if (nextIndex !in currentTracks.indices) return
        if (lastPrefetchedNextIndex == nextIndex) return

        // Only pre-fetch next track when current track is near its end (within 30s of finish or > 85% through)
        val isNearEnd = (durationMs > 10_000L && currentPositionMs >= (durationMs - 30_000L)) ||
                        (durationMs > 10_000L && currentPositionMs >= (durationMs * 0.85).toLong())

        if (isNearEnd) {
            val nextTrack = currentTracks[nextIndex]
            val isUnresolved = nextTrack.filePath.startsWith("online:") || isStreamUrlExpired(nextTrack.filePath)
            if (isUnresolved) {
                lastPrefetchedNextIndex = nextIndex
                activeNextStreamJob?.cancel()
                activeNextStreamJob = viewModelScope.launch(Dispatchers.IO) {
                    Log.d("MUESO_QUEUE", "Near-end pre-fetching next track at index $nextIndex: ${nextTrack.title}")
                    val resolvedNext = resolveTrack(nextTrack)
                    withContext(Dispatchers.Main) {
                        val nList = currentTracks.toMutableList()
                        if (nextIndex in nList.indices && nList[nextIndex].id == nextTrack.id) {
                            nList[nextIndex] = resolvedNext
                            currentTracks = nList
                            mediaPlayerController.updateTrackInQueue(nextIndex, resolvedNext)
                        }
                    }
                }
            }
        }
    }

    private fun prefetchAndKeepQueueAlive(currentIndex: Int) {
        if (currentIndex !in currentTracks.indices) return
        val currentTrack = currentTracks[currentIndex]
        lastPrefetchTrackId = currentTrack.id

        _isResolvingTrack.value = false
        _resolvingTrackTitle.value = null

        // 2. Keep queue alive: Auto-fetch related suggestions when nearing the end of online queue
        if (currentIndex >= currentTracks.size - 2 && !isFetchingMoreQueue && (currentTrack.filePath.contains("http") || currentTrack.filePath.contains("online:"))) {
            isFetchingMoreQueue = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    Log.d("MUESO_QUEUE", "Nearing queue end. Auto-fetching related tracks via YouTube Music Radio for: '${currentTrack.title}'")
                    val newTracks = onlineRepository.getRelatedRecommendations(currentTrack)
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
        val newOffset = _lyricsOffsetMs.value + deltaMs
        _lyricsOffsetMs.value = newOffset
        val curId = playbackState.value.currentTrackId
        if (curId != null && isTrackInUserPlaylists(curId)) {
            if (newOffset == 0L) {
                sharedPreferences.edit().remove("lyrics_offset_$curId").apply()
            } else {
                sharedPreferences.edit().putLong("lyrics_offset_$curId", newOffset).apply()
            }
            backupManager.markDirty()
        }
    }

    fun resetLyricsOffset() {
        _lyricsOffsetMs.value = 0L
        val curId = playbackState.value.currentTrackId
        if (curId != null) {
            sharedPreferences.edit().remove("lyrics_offset_$curId").apply()
            if (isTrackInUserPlaylists(curId)) {
                backupManager.markDirty()
            }
        }
    }

    private var activeLyricsFetchJob: Job? = null

    private fun fetchLyricsForTrack(track: TrackEntity) {
        if (track.lyrics != null) {
            _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.FOUND)
            return
        }

        val currentStatus = _lyricsFetchStatus.value[track.id]
        if (currentStatus == LyricsFetchStatus.FETCHING) return

        // 1. Check local saved custom lyrics first
        val savedLyrics = getSavedCustomLyrics(track.id)
        if (savedLyrics != null && (savedLyrics.lines.isNotEmpty() || !savedLyrics.rawText.isNullOrBlank())) {
            currentTracks = currentTracks.map {
                if (it.id == track.id) it.copy(lyrics = savedLyrics) else it
            }
            _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.FOUND)
            return
        }

        _lyricsFetchStatus.value = _lyricsFetchStatus.value + (track.id to LyricsFetchStatus.FETCHING)

        activeLyricsFetchJob?.cancel()
        activeLyricsFetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400) // Debounce rapid swiping
            if (_playbackState.value.currentTrackId != track.id) return@launch

            val lyricsData = onlineRepository.fetchLyrics(track.title, track.artist, preferredLanguage.value)
            if (_playbackState.value.currentTrackId != track.id) return@launch

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

    fun searchAndApplyLyrics(trackId: Long, customTitle: String, customArtist: String = "", customLang: String = "") {
        _lyricsFetchStatus.value = _lyricsFetchStatus.value + (trackId to LyricsFetchStatus.FETCHING)
        viewModelScope.launch(Dispatchers.IO) {
            val lyricsData = onlineRepository.fetchLyrics(customTitle, customArtist, customLang)
            withContext(Dispatchers.Main) {
                if (lyricsData != null && (lyricsData.lines.isNotEmpty() || !lyricsData.rawText.isNullOrBlank())) {
                    currentTracks = currentTracks.map {
                        if (it.id == trackId) it.copy(lyrics = lyricsData) else it
                    }
                    _lyricsFetchStatus.value = _lyricsFetchStatus.value + (trackId to LyricsFetchStatus.FOUND)
                } else {
                    _lyricsFetchStatus.value = _lyricsFetchStatus.value + (trackId to LyricsFetchStatus.NOT_FOUND)
                }
            }
        }
    }

    suspend fun searchLrclibCandidates(query: String): List<com.akshay.musicplayer.domain.models.LrclibSearchResultItem> {
        return onlineRepository.searchLrclibCandidates(query)
    }

    fun applyLrclibCandidate(trackId: Long, candidate: com.akshay.musicplayer.domain.models.LrclibSearchResultItem) {
        val rawSynced = candidate.syncedLyrics?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val rawPlain = candidate.plainLyrics?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

        val lyricsData = if (rawSynced != null) {
            val parsedLines = com.akshay.musicplayer.domain.models.LrcParser.parse(rawSynced)
            com.akshay.musicplayer.domain.models.LyricsData(
                lines = parsedLines,
                rawText = rawPlain ?: rawSynced
            )
        } else if (rawPlain != null) {
            com.akshay.musicplayer.domain.models.LyricsData(
                lines = emptyList(),
                rawText = rawPlain
            )
        } else return

        currentTracks = currentTracks.map {
            if (it.id == trackId) it.copy(lyrics = lyricsData) else it
        }
        _lyricsFetchStatus.value = _lyricsFetchStatus.value + (trackId to LyricsFetchStatus.FOUND)

        // Save custom lyrics
        saveCustomLyrics(trackId, lyricsData)
    }

    fun getSavedCustomLyrics(trackId: Long): com.akshay.musicplayer.domain.models.LyricsData? {
        val jsonStr = sharedPreferences.getString("custom_lyrics_$trackId", null) ?: return null
        return try {
            val jsonObj = org.json.JSONObject(jsonStr)
            val rawText = jsonObj.optString("rawText", "")
            val linesArr = jsonObj.optJSONArray("lines")
            val linesList = mutableListOf<com.akshay.musicplayer.domain.models.LyricLine>()
            if (linesArr != null) {
                for (i in 0 until linesArr.length()) {
                    val lineObj = linesArr.getJSONObject(i)
                    val time = lineObj.getLong("time")
                    val text = lineObj.getString("text")
                    linesList.add(com.akshay.musicplayer.domain.models.LyricLine(time, text))
                }
            }
            com.akshay.musicplayer.domain.models.LyricsData(lines = linesList, rawText = rawText.ifBlank { null })
        } catch (e: Exception) {
            null
        }
    }

    fun saveCustomLyrics(trackId: Long, lyricsData: com.akshay.musicplayer.domain.models.LyricsData) {
        if (!isTrackInUserPlaylists(trackId)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonObj = org.json.JSONObject()
                jsonObj.put("rawText", lyricsData.rawText ?: "")
                val linesArr = org.json.JSONArray()
                lyricsData.lines.forEach { line ->
                    val lObj = org.json.JSONObject()
                    lObj.put("time", line.timestampMs)
                    lObj.put("text", line.text)
                    linesArr.put(lObj)
                }
                jsonObj.put("lines", linesArr)
                sharedPreferences.edit().putString("custom_lyrics_$trackId", jsonObj.toString()).apply()
                backupManager.markDirty()
            } catch (e: Exception) {
                android.util.Log.e("MUESO_LYRICS", "Failed to save custom lyrics", e)
            }
        }
    }

    fun isTrackInUserPlaylists(trackId: Long): Boolean {
        return try {
            val onlineList = playlistManager.onlinePlaylists.value
            val inOnline = onlineList.any { p ->
                val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                tracks.any { it.trackId == trackId }
            }
            if (inOnline) return true

            val localList = playlistManager.playlists.value
            localList.any { p ->
                val refs = playlistDao.getPlaylistTracksSync(p.id)
                refs.any { it.trackId == trackId }
            }
        } catch (e: Exception) {
            false
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
                        val is403Error = event.message.contains("403", ignoreCase = true) ||
                                         event.message.contains("InvalidResponseCode", ignoreCase = true) ||
                                         event.message.contains("ERROR_CODE_IO_BAD_HTTP_STATUS", ignoreCase = true) ||
                                         event.message.contains("2004", ignoreCase = true)
                        val isRecentUserAction = (System.currentTimeMillis() - lastUserSkipTime) < 8000
                        val isTransientError = event.message.contains("cancel", ignoreCase = true) ||
                                               event.message.contains("interrupted", ignoreCase = true) ||
                                               event.message.contains("Malformed", ignoreCase = true)

                        if (is403Error) {
                            Log.w("MUESO_STREAM", "HTTP 403 detected in error chain. Refreshing stream URL...")
                            val currentIdx = currentTracks.indexOfFirst { it.id == _playbackState.value.currentTrackId }
                            if (currentIdx in currentTracks.indices) {
                                val track = currentTracks[currentIdx]
                                viewModelScope.launch(Dispatchers.IO) {
                                    val refreshedTrack = reResolveTrackStream(track)
                                    if (refreshedTrack.filePath.startsWith("http") && refreshedTrack.filePath != track.filePath) {
                                        withContext(Dispatchers.Main) {
                                            val list = currentTracks.toMutableList()
                                            list[currentIdx] = refreshedTrack
                                            currentTracks = list
                                            mediaPlayerController.updateTrackInQueue(currentIdx, refreshedTrack)
                                            if (!_playbackState.value.isPlaying) {
                                                mediaPlayerController.togglePlayPause()
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isRecentUserAction || isTransientError) {
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

    private var originalUnshuffledTracks: List<TrackEntity>? = null

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        originalUnshuffledTracks = null
        val target = if (startIndex in tracks.indices) tracks[startIndex] else null
        _lyricsOffsetMs.value = target?.let { sharedPreferences.getLong("lyrics_offset_${it.id}", 0L) } ?: 0L
        lastUserSkipTime = System.currentTimeMillis()
        cancelRestoration()
        lastRequestedTrackId = target?.id
        val isOnline = target != null && (target.filePath.startsWith("online:") || isStreamUrlExpired(target.filePath))
        if (isOnline) {
            mediaPlayerController.pause()
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
        originalUnshuffledTracks = null
        _lyricsOffsetMs.value = sharedPreferences.getLong("lyrics_offset_${track.id}", 0L)
        cancelRestoration()
        _isPlaylistContext.value = false
        _playlistTrackCount.value = 0
        lastRequestedTrackId = track.id
        val isOnline = track.filePath.startsWith("online:") || isStreamUrlExpired(track.filePath)
        if (isOnline) {
            mediaPlayerController.pause()
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
        lastUserSkipTime = System.currentTimeMillis()
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
        lastUserSkipTime = System.currentTimeMillis()
        if (index >= 0 && index < currentTracks.size) {
            val track = currentTracks[index]
            lastRequestedTrackId = track.id
            if (index >= _playlistTrackCount.value) {
                // Stepped past the playlist boundary into radio recommendations
                _isPlaylistContext.value = false
                _playlistTrackCount.value = 0
            }

            val isUnresolvedOrExpired = track.filePath.startsWith("online:") || isStreamUrlExpired(track.filePath)
            if (isUnresolvedOrExpired) {
                // Immediately cut off playback of previous track while resolving new track
                mediaPlayerController.pause()
                Log.d("MUESO_SYNC", "ViewModel playTrackAtIndex: track at index $index is unresolved/expired, resolving first...")
                _resolvingTrackTitle.value = track.title
                _isResolvingTrack.value = true
                activeCurrentStreamJob?.cancel()
                activeNextStreamJob?.cancel()
                lastPrefetchTrackId = track.id
                activeCurrentStreamJob = viewModelScope.launch(Dispatchers.IO) {
                    val resolved = resolveTrack(track)
                    if (lastPrefetchTrackId != track.id) {
                        Log.d("MUESO_SYNC", "ViewModel playTrackAtIndex: stale resolve for '${track.title}', discarding")
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        _isResolvingTrack.value = false
                        _resolvingTrackTitle.value = null
                        val list = currentTracks.toMutableList()
                        if (index in list.indices && list[index].id == track.id) {
                            list[index] = resolved
                            currentTracks = list
                            mediaPlayerController.updateTrackInQueue(index, resolved)
                            mediaPlayerController.seekToIndex(index)
                        }
                    }
                }
            } else {
                mediaPlayerController.seekToIndex(index)
            }
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

    fun playNext(track: TrackEntity) {
        playNextTracks(listOf(track))
    }

    fun playNextTracks(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val currentIdx = currentTrackIndexState.value.coerceAtLeast(0)
        val insertIndex = (currentIdx + 1).coerceAtMost(currentTracks.size)
        val updated = currentTracks.toMutableList()
        updated.addAll(insertIndex, tracks)
        currentTracks = updated

        if (_isPlaylistContext.value) {
            _playlistTrackCount.value += tracks.size
        } else {
            _playlistTrackCount.value = 1 + tracks.size
            _isPlaylistContext.value = true
        }

        mediaPlayerController.insertTracksToQueue(insertIndex, tracks)
    }

    fun playShuffle(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        playQueue(shuffled, 0)
    }

    fun playOnlineShuffle(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        playOnlinePlaylist(shuffled, 0)
    }

    fun startMix(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val seed = tracks.randomOrNull() ?: tracks.first()
            val recommendations = try {
                onlineRepository.getRelatedRecommendations(seed)
            } catch (e: Exception) {
                emptyList()
            }
            val initialMix = if (recommendations.isNotEmpty()) {
                val uniqueRecs = recommendations.filter { it.id != seed.id }
                listOf(seed) + uniqueRecs
            } else {
                tracks.shuffled()
            }
            withContext(Dispatchers.Main) {
                _isPlaylistContext.value = false
                _playlistTrackCount.value = 0
                playQueue(initialMix, 0)
            }
        }
    }

    fun addToQueue(track: TrackEntity) {
        addTracksToQueue(listOf(track))
    }

    fun addTracksToQueue(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return

        val currentIdx = currentTrackIndexState.value.coerceAtLeast(0)
        val isPlaylist = _isPlaylistContext.value && _playlistTrackCount.value > 0

        val insertIndex = if (isPlaylist && currentIdx < _playlistTrackCount.value) {
            // Playing a playlist -> insert right after the playlist tracks (before radio recommendations)
            _playlistTrackCount.value.coerceAtMost(currentTracks.size)
        } else {
            // Playing a single track (or past the playlist) -> insert right after currently playing track
            (currentIdx + 1).coerceAtMost(currentTracks.size)
        }

        val updated = currentTracks.toMutableList()
        updated.addAll(insertIndex, tracks)
        currentTracks = updated

        if (_isPlaylistContext.value) {
            _playlistTrackCount.value += tracks.size
        } else {
            _playlistTrackCount.value = 1 + tracks.size
            _isPlaylistContext.value = true
        }

        mediaPlayerController.insertTracksToQueue(insertIndex, tracks)
    }

    fun toggleShuffleMode() {
        val newMode = !_isShuffleModeEnabled.value
        setShuffleMode(newMode)
    }

    fun clearUpcomingQueue() {
        val currentIdx = currentTrackIndexState.value.coerceAtLeast(0)
        if (currentIdx < currentTracks.size - 1) {
            currentTracks = currentTracks.subList(0, currentIdx + 1)
            _playlistTrackCount.value = currentIdx + 1
            mediaPlayerController.clearUpcomingQueue(currentIdx)
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        _isShuffleModeEnabled.value = enabled
        mediaPlayerController.setShuffleEnabled(enabled)

        if (currentTracks.isEmpty()) return
        val currentIdx = currentTrackIndexState.value.coerceAtLeast(0)
        if (currentIdx >= currentTracks.size) return

        val pastTracks = currentTracks.subList(0, currentIdx + 1)
        val upcomingTracks = currentTracks.subList(currentIdx + 1, currentTracks.size)

        if (enabled) {
            if (upcomingTracks.size > 1) {
                if (originalUnshuffledTracks == null) {
                    originalUnshuffledTracks = currentTracks.toList()
                }
                val shuffledUpcoming = upcomingTracks.shuffled()
                val newTracks = pastTracks + shuffledUpcoming
                currentTracks = newTracks
                mediaPlayerController.clearUpcomingQueue(currentIdx)
                mediaPlayerController.insertTracksToQueue(currentIdx + 1, shuffledUpcoming)
            }
        } else {
            originalUnshuffledTracks?.let { orig ->
                val curTrackId = currentTracks.getOrNull(currentIdx)?.id
                if (curTrackId != null) {
                    val origCurrentIdx = orig.indexOfFirst { it.id == curTrackId }
                    val restoredUpcoming = if (origCurrentIdx >= 0 && origCurrentIdx < orig.size - 1) {
                        orig.subList(origCurrentIdx + 1, orig.size)
                    } else {
                        emptyList()
                    }
                    if (restoredUpcoming.isNotEmpty()) {
                        val newTracks = pastTracks + restoredUpcoming
                        currentTracks = newTracks
                        mediaPlayerController.clearUpcomingQueue(currentIdx)
                        mediaPlayerController.insertTracksToQueue(currentIdx + 1, restoredUpcoming)
                    }
                }
                originalUnshuffledTracks = null
            }
        }
    }

    fun forceRefreshAll(context: android.content.Context) {
        val editor = sharedPreferences.edit()
        val keys = sharedPreferences.all.keys.filter {
            it.startsWith("curated_cache_") || it.startsWith("custom_lyrics_") || it.startsWith("lyrics_offset_")
        }
        for (k in keys) {
            editor.remove(k)
        }
        editor.apply()
        _lyricsOffsetMs.value = 0L

        playlistManager.refreshAllPlaylistArtworks()
        loadLocalTracks(forceReload = true)
        android.widget.Toast.makeText(context, "App refreshed! Lyrics, offsets & caches reset.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun getQueueTracks(): List<TrackEntity> = currentTracks

}
