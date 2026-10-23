package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.util.Log
import com.akshay.musicplayer.data.db.OnlinePlaylistDao
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.data.remote.SpotifyImportRepository
import com.akshay.musicplayer.data.remote.SpotifyPlaylistData
import com.akshay.musicplayer.data.remote.SpotifyTrackInfo
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.media.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SpotifyImportState {
    Idle,
    FetchingSpotify,
    MatchingTracks,
    Ready,
    Creating,
    Done,
    Error
}

data class TrackMatchResult(
    val spotifyTrack: SpotifyTrackInfo,
    val matchedTrack: TrackEntity? = null,
    val isSearching: Boolean = false,
    val alternativeResults: List<TrackEntity> = emptyList(),
    val showAlternatives: Boolean = false,
    val isSelected: Boolean = true
)

class SpotifyImportManager(
    private val spotifyRepo: SpotifyImportRepository,
    private val onlineRepo: OnlineMusicRepository,
    private val onlinePlaylistDao: OnlinePlaylistDao,
    private val coroutineScope: CoroutineScope,
    private val markDirty: () -> Unit,
    private val onYouTubeRefresh: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "MUESO_SPOTIFY_IMPORT"
    }

    private val _importState = MutableStateFlow(SpotifyImportState.Idle)
    val importState: StateFlow<SpotifyImportState> = _importState.asStateFlow()

    private val _spotifyPlaylistData = MutableStateFlow<SpotifyPlaylistData?>(null)
    val spotifyPlaylistData: StateFlow<SpotifyPlaylistData?> = _spotifyPlaylistData.asStateFlow()

    private val _matchResults = MutableStateFlow<List<TrackMatchResult>>(emptyList())
    val matchResults: StateFlow<List<TrackMatchResult>> = _matchResults.asStateFlow()

    private val _matchProgress = MutableStateFlow(0)
    val matchProgress: StateFlow<Int> = _matchProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _createdDestination = MutableStateFlow<String>("online")
    val createdDestination: StateFlow<String> = _createdDestination.asStateFlow()

    private var currentSourceUrl: String? = null
    private var syncTargetPlaylistId: Long? = null
    private val _syncTargetPlaylistName = MutableStateFlow<String?>(null)
    val syncTargetPlaylistName: StateFlow<String?> = _syncTargetPlaylistName.asStateFlow()
    val isSyncMode: Boolean get() = syncTargetPlaylistId != null

    private var importJob: Job? = null
    private var lastContext: Context? = null

    private val genericChannelTerms = setOf(
        "vevo", "records", "music", "tseries", "series", "entertainment", "channel",
        "audio", "media", "production", "productions", "studios", "official", "sound", "sounds"
    )

    private fun cleanString(input: String): String {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .trim()
    }

    private fun extractWords(input: String): List<String> {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full|song)\b""", RegexOption.IGNORE_CASE), " ")
            .split(Regex("""[^\p{L}\p{Nd}]+"""))
            .filter { it.length >= 2 }
    }

    private fun isTrackAlreadyPresent(spotifyTrack: SpotifyTrackInfo, existingTracks: List<OnlinePlaylistTrackEntity>): Boolean {
        val cleanSpotifyTitle = cleanString(spotifyTrack.title)
        val spotifyTitleWords = extractWords(spotifyTrack.title)
        val spotifyArtistWords = extractWords(spotifyTrack.artist)

        if (cleanSpotifyTitle.isEmpty()) return false

        return existingTracks.any { existing ->
            val cleanExistingTitle = cleanString(existing.title)
            val cleanExistingArtist = cleanString(existing.artist)
            val existingArtistWords = extractWords(existing.artist)

            // 1. Exact title match
            val isExactTitle = cleanExistingTitle == cleanSpotifyTitle

            // 2. Substring title match
            val isSubstringTitle = (cleanSpotifyTitle.length >= 4 && cleanExistingTitle.contains(cleanSpotifyTitle)) ||
                    (cleanExistingTitle.length >= 4 && cleanSpotifyTitle.contains(cleanExistingTitle))

            // 3. Word token match: all main words of Spotify title are inside the existing title
            val isTokenTitle = spotifyTitleWords.size >= 2 && spotifyTitleWords.all { word ->
                cleanExistingTitle.contains(word)
            }

            val titleMatched = isExactTitle || isSubstringTitle || isTokenTitle
            if (!titleMatched) return@any false

            // Check artist overlap
            val existingFullText = "$cleanExistingTitle $cleanExistingArtist"
            val hasArtistOverlap = spotifyArtistWords.any { artistWord ->
                artistWord.length >= 3 && existingFullText.contains(artistWord)
            }

            if (hasArtistOverlap) return@any true

            // If existing artist is purely a generic channel name (e.g. "T-Series", "SonyMusicSouthVEVO"), trust the title match
            val isGenericChannel = existingArtistWords.isEmpty() || existingArtistWords.all { it in genericChannelTerms }
            if (isGenericChannel && (isExactTitle || isSubstringTitle || isTokenTitle)) {
                return@any true
            }

            false
        }
    }

    fun fetchAndMatch(context: Context, url: String) {
        lastContext = context.applicationContext
        importJob?.cancel()
        currentSourceUrl = url
        syncTargetPlaylistId = null
        _syncTargetPlaylistName.value = null
        importJob = coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.FetchingSpotify
            _errorMessage.value = null
            _matchResults.value = emptyList()
            _matchProgress.value = 0

            Log.d(TAG, "Starting Spotify import for URL: $url")

            val result = spotifyRepo.fetchPlaylist(url)

            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Failed to fetch Spotify playlist: $error")
                _errorMessage.value = error
                _importState.value = SpotifyImportState.Error
                return@launch
            }

            val playlistData = result.getOrNull()!!
            _spotifyPlaylistData.value = playlistData

            Log.d(TAG, "Fetched ${playlistData.tracks.size} tracks from Spotify playlist: \"${playlistData.name}\"")

            // Initialize match results with all tracks as unmatched
            _matchResults.value = playlistData.tracks.map { TrackMatchResult(spotifyTrack = it, isSelected = false) }

            _importState.value = SpotifyImportState.MatchingTracks

            // Search YT for each track sequentially
            for (i in playlistData.tracks.indices) {
                val spotifyTrack = playlistData.tracks[i]
                val cleanTitle = OnlineMusicRepository.cleanTitleForLyrics(spotifyTrack.title)
                val primaryArtist = OnlineMusicRepository.getPrimaryArtist(spotifyTrack.artist)
                val query = OnlineMusicRepository.cleanSearchQuery(cleanTitle, primaryArtist)

                Log.d(TAG, "Matching [${i + 1}/${playlistData.tracks.size}]: \"$query\" (raw: \"${spotifyTrack.title}\" - \"${spotifyTrack.artist}\")")

                // Mark this track as searching
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[i] = list[i].copy(isSearching = true)
                }
                _matchProgress.value = i

                // Post system notification progress
                NotificationHelper.showImportProgress(context.applicationContext, playlistData.name, i, playlistData.tracks.size)

                try {
                    val searchResults = onlineRepo.searchOnlineTracks(query)
                    val ranked = OnlineMusicRepository.rankTracks(searchResults, query)
                    val bestMatch = OnlineMusicRepository.findBestTrackMatch(
                        candidates = ranked,
                        targetTitle = cleanTitle,
                        targetArtist = primaryArtist
                    )

                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(
                            matchedTrack = bestMatch,
                            isSearching = false,
                            alternativeResults = ranked.take(8),
                            isSelected = (bestMatch != null)
                        )
                    }

                    if (bestMatch != null) {
                        Log.d(TAG, "  ✓ Matched: \"${bestMatch.title}\" by ${bestMatch.artist}")
                    } else {
                        Log.d(TAG, "  ✗ No match found for \"$query\"")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ Search error for \"$query\"", e)
                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(isSearching = false)
                    }
                }
            }

            _matchProgress.value = playlistData.tracks.size
            _importState.value = SpotifyImportState.Ready
            val matchedCount = _matchResults.value.count { it.matchedTrack != null }
            NotificationHelper.showImportComplete(context.applicationContext, playlistData.name, matchedCount, playlistData.tracks.size)
            Log.d(TAG, "Matching complete. $matchedCount/${playlistData.tracks.size} tracks matched.")
        }
    }

    fun retryMatch(index: Int, query: String) {
        coroutineScope.launch(Dispatchers.IO) {
            if (index !in _matchResults.value.indices) return@launch

            val cleanQuery = OnlineMusicRepository.cleanSearchQuery(query)
            Log.d(TAG, "Manual re-search for track $index with query: \"$cleanQuery\" (input: \"$query\")")

            _matchResults.value = _matchResults.value.toMutableList().also { list ->
                list[index] = list[index].copy(isSearching = true, showAlternatives = true)
            }

            try {
                val searchResults = onlineRepo.searchOnlineTracks(cleanQuery)
                val ranked = OnlineMusicRepository.rankTracks(searchResults, cleanQuery)
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[index] = list[index].copy(
                        isSearching = false,
                        alternativeResults = ranked.take(10),
                        showAlternatives = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual search error", e)
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[index] = list[index].copy(isSearching = false)
                }
            }
        }
    }

    fun selectMatch(trackIndex: Int, selectedTrack: TrackEntity) {
        if (trackIndex !in _matchResults.value.indices) return
        _matchResults.value = _matchResults.value.toMutableList().also { list ->
            list[trackIndex] = list[trackIndex].copy(
                matchedTrack = selectedTrack,
                showAlternatives = false,
                isSelected = true
            )
        }
        Log.d(TAG, "Manual match selected for track $trackIndex: \"${selectedTrack.title}\"")
    }

    fun toggleAlternatives(index: Int) {
        if (index !in _matchResults.value.indices) return
        _matchResults.value = _matchResults.value.toMutableList().also { list ->
            list[index] = list[index].copy(showAlternatives = !list[index].showAlternatives)
        }
    }

    fun toggleTrackSelection(index: Int) {
        val current = _matchResults.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            if (item.matchedTrack != null) {
                current[index] = item.copy(isSelected = !item.isSelected)
                _matchResults.value = current
            }
        }
    }

    fun selectAllTracks(selected: Boolean) {
        val current = _matchResults.value.map { item ->
            if (item.matchedTrack != null) item.copy(isSelected = selected) else item.copy(isSelected = false)
        }
        _matchResults.value = current
    }

    fun createPlaylist() {
        val playlistData = _spotifyPlaylistData.value ?: return
        val matches = _matchResults.value

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "online"

            try {
                val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                    OnlinePlaylistEntity(
                        name = playlistData.name,
                        artworkUrl = playlistData.artworkUrl,
                        description = playlistData.description ?: "Imported from Spotify",
                        spotifyUrl = currentSourceUrl
                    )
                )

                var orderIndex = 0
                for (match in matches) {
                    if (!match.isSelected) continue
                    val track = match.matchedTrack ?: continue
                    onlinePlaylistDao.insertOnlineTrack(
                        OnlinePlaylistTrackEntity(
                            onlinePlaylistId = playlistId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            filePath = track.filePath,
                            duration = track.duration,
                            orderIndex = orderIndex++
                        )
                    )
                }

                markDirty()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Created online playlist \"${playlistData.name}\" with $orderIndex tracks (ID: $playlistId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist", e)
                _errorMessage.value = "Failed to create playlist: ${e.message}"
                _importState.value = SpotifyImportState.Error
            }
        }
    }

    fun syncPlaylist(
        context: Context,
        playlistId: Long,
        playlistName: String,
        spotifyUrl: String,
        onChecking: () -> Unit = {},
        onNoNewTracks: () -> Unit = {},
        onNewTracksFound: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        lastContext = context.applicationContext
        importJob?.cancel()
        currentSourceUrl = spotifyUrl
        syncTargetPlaylistId = playlistId
        _syncTargetPlaylistName.value = playlistName

        importJob = coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.FetchingSpotify
            _errorMessage.value = null
            _matchResults.value = emptyList()
            _matchProgress.value = 0

            withContext(Dispatchers.Main) { onChecking() }
            Log.d(TAG, "Syncing playlist \"$playlistName\" (ID: $playlistId) from Spotify: $spotifyUrl")

            val result = spotifyRepo.fetchPlaylist(spotifyUrl)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Failed to fetch Spotify playlist"
                Log.e(TAG, "Sync Spotify fetch failed: $error")
                _errorMessage.value = error
                _importState.value = SpotifyImportState.Error
                withContext(Dispatchers.Main) { onError(error) }
                return@launch
            }

            val fullPlaylistData = result.getOrNull()!!
            val existingTracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
            val newTracks = fullPlaylistData.tracks.filter { track ->
                val present = isTrackAlreadyPresent(track, existingTracks)
                if (present) {
                    Log.d(TAG, "  ✓ Skipped already in playlist: \"${track.title}\" - ${track.artist}")
                } else {
                    Log.d(TAG, "  + Found newly added track: \"${track.title}\" - ${track.artist}")
                }
                !present
            }

            Log.d(TAG, "Sync delta: found ${newTracks.size} new track(s) out of ${fullPlaylistData.tracks.size} (existing: ${existingTracks.size})")

            if (newTracks.isEmpty()) {
                _importState.value = SpotifyImportState.Idle
                syncTargetPlaylistId = null
                _syncTargetPlaylistName.value = null
                withContext(Dispatchers.Main) { onNoNewTracks() }
                return@launch
            }

            // New tracks discovered
            withContext(Dispatchers.Main) { onNewTracksFound(newTracks.size) }

            _spotifyPlaylistData.value = fullPlaylistData.copy(tracks = newTracks)
            _matchResults.value = newTracks.map { TrackMatchResult(spotifyTrack = it, isSelected = false) }
            _importState.value = SpotifyImportState.MatchingTracks

            for (i in newTracks.indices) {
                val spotifyTrack = newTracks[i]
                val cleanTitle = OnlineMusicRepository.cleanTitleForLyrics(spotifyTrack.title)
                val primaryArtist = OnlineMusicRepository.getPrimaryArtist(spotifyTrack.artist)
                val query = OnlineMusicRepository.cleanSearchQuery(cleanTitle, primaryArtist)

                Log.d(TAG, "Matching sync track [${i + 1}/${newTracks.size}]: \"$query\" (raw: \"${spotifyTrack.title}\" - \"${spotifyTrack.artist}\")")

                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[i] = list[i].copy(isSearching = true)
                }
                _matchProgress.value = i
                NotificationHelper.showImportProgress(context.applicationContext, "Sync: $playlistName", i, newTracks.size)

                try {
                    val searchResults = onlineRepo.searchOnlineTracks(query)
                    val ranked = OnlineMusicRepository.rankTracks(searchResults, query)
                    val bestMatch = OnlineMusicRepository.findBestTrackMatch(
                        candidates = ranked,
                        targetTitle = cleanTitle,
                        targetArtist = primaryArtist
                    )

                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(
                            matchedTrack = bestMatch,
                            isSearching = false,
                            alternativeResults = ranked.take(8),
                            isSelected = (bestMatch != null)
                        )
                    }

                    if (bestMatch != null) {
                        Log.d(TAG, "  ✓ Matched sync track: \"${bestMatch.title}\" by ${bestMatch.artist}")
                    } else {
                        Log.d(TAG, "  ✗ No match found for sync track \"$query\"")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search error for \"$query\"", e)
                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(isSearching = false)
                    }
                }
            }

            _matchProgress.value = newTracks.size
            _importState.value = SpotifyImportState.Ready
            val matchedCount = _matchResults.value.count { it.matchedTrack != null }
            NotificationHelper.showImportComplete(context.applicationContext, "Sync: $playlistName", matchedCount, newTracks.size)
        }
    }

    fun appendSyncTracksToPlaylist(onComplete: (Int) -> Unit = {}) {
        val playlistId = syncTargetPlaylistId ?: return
        val matches = _matchResults.value
        val validTracks = matches.filter { it.isSelected }.mapNotNull { it.matchedTrack }
        if (validTracks.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "online"

            try {
                val existingTracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
                val maxOrder = existingTracks.maxOfOrNull { it.orderIndex } ?: -1
                var orderIndex = maxOrder + 1
                var addedCount = 0

                for (track in validTracks) {
                    onlinePlaylistDao.insertOnlineTrack(
                        OnlinePlaylistTrackEntity(
                            onlinePlaylistId = playlistId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            filePath = track.filePath,
                            duration = track.duration,
                            orderIndex = orderIndex++
                        )
                    )
                    addedCount++
                }

                onlinePlaylistDao.updateOnlinePlaylistTimestamp(playlistId)
                markDirty()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Appended $addedCount sync tracks to playlist ID: $playlistId")
                withContext(Dispatchers.Main) { onComplete(addedCount) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append sync tracks", e)
                _errorMessage.value = "Failed to add tracks: ${e.message}"
                _importState.value = SpotifyImportState.Error
            }
        }
    }

    fun createYouTubePlaylist(onComplete: (Boolean) -> Unit = {}) {
        val playlistData = _spotifyPlaylistData.value ?: return
        val matches = _matchResults.value

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "youtube"

            try {
                if (!onlineRepo.innerTube.isLoggedIn()) {
                    _errorMessage.value = "Please sign in to YouTube Music first"
                    _importState.value = SpotifyImportState.Error
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                val newPlaylistId = onlineRepo.innerTube.createPlaylist(
                    title = playlistData.name,
                    description = playlistData.description ?: "Imported from Spotify via Mueso"
                )

                if (newPlaylistId == null) {
                    _errorMessage.value = "Failed to create playlist on YouTube Music"
                    _importState.value = SpotifyImportState.Error
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                var addedCount = 0
                val matchedTracks = matches.filter { it.isSelected }.mapNotNull { it.matchedTrack }
                for (track in matchedTracks) {
                    var videoId = onlineRepo.extractVideoId(track)
                    if (videoId.isBlank()) {
                        val search = onlineRepo.innerTube.search("${track.title} ${track.artist}", "Eg-KAQwIABAAGAEgASgB")
                        videoId = search.firstOrNull()?.videoId ?: ""
                    }
                    if (videoId.isNotBlank()) {
                        val ok = onlineRepo.innerTube.addTrackToPlaylist(newPlaylistId, videoId)
                        if (ok) addedCount++
                    }
                }

                onYouTubeRefresh?.invoke()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Created YouTube Music playlist \"${playlistData.name}\" ($newPlaylistId) with $addedCount tracks")
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create YouTube Music playlist", e)
                _errorMessage.value = "Failed to create YouTube playlist: ${e.message}"
                _importState.value = SpotifyImportState.Error
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun reset() {
        importJob?.cancel()
        lastContext?.let { NotificationHelper.dismissImportNotification(it) }
        _importState.value = SpotifyImportState.Idle
        _spotifyPlaylistData.value = null
        _matchResults.value = emptyList()
        _matchProgress.value = 0
        _errorMessage.value = null
        syncTargetPlaylistId = null
        _syncTargetPlaylistName.value = null
    }
}
