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
    val showAlternatives: Boolean = false
)

class SpotifyImportManager(
    private val spotifyRepo: SpotifyImportRepository,
    private val onlineRepo: OnlineMusicRepository,
    private val onlinePlaylistDao: OnlinePlaylistDao,
    private val coroutineScope: CoroutineScope,
    private val markDirty: () -> Unit
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

    private var importJob: Job? = null
    private var lastContext: Context? = null

    fun fetchAndMatch(context: Context, url: String) {
        lastContext = context.applicationContext
        importJob?.cancel()
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
            _matchResults.value = playlistData.tracks.map { TrackMatchResult(spotifyTrack = it) }

            _importState.value = SpotifyImportState.MatchingTracks

            // Search YT for each track sequentially
            for (i in playlistData.tracks.indices) {
                val spotifyTrack = playlistData.tracks[i]
                val query = "${spotifyTrack.title} ${spotifyTrack.artist}"

                Log.d(TAG, "Matching [${i + 1}/${playlistData.tracks.size}]: \"$query\"")

                // Mark this track as searching
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[i] = list[i].copy(isSearching = true)
                }
                _matchProgress.value = i

                // Post system notification progress
                NotificationHelper.showImportProgress(context.applicationContext, playlistData.name, i, playlistData.tracks.size)

                try {
                    val searchResults = onlineRepo.searchOnlineTracks(query)
                    val bestMatch = searchResults.firstOrNull()

                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(
                            matchedTrack = bestMatch,
                            isSearching = false,
                            alternativeResults = searchResults.take(5)
                        )
                    }

                    if (bestMatch != null) {
                        Log.d(TAG, "  ✓ Matched: \"${bestMatch.title}\" by ${bestMatch.artist}")
                    } else {
                        Log.d(TAG, "  ✗ No match found")
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

            Log.d(TAG, "Manual re-search for track $index with query: \"$query\"")

            _matchResults.value = _matchResults.value.toMutableList().also { list ->
                list[index] = list[index].copy(isSearching = true, showAlternatives = true)
            }

            try {
                val searchResults = onlineRepo.searchOnlineTracks(query)
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[index] = list[index].copy(
                        isSearching = false,
                        alternativeResults = searchResults.take(8),
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
                showAlternatives = false
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

    fun createPlaylist() {
        val playlistData = _spotifyPlaylistData.value ?: return
        val matches = _matchResults.value

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating

            try {
                val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                    OnlinePlaylistEntity(
                        name = playlistData.name,
                        artworkUrl = playlistData.artworkUrl,
                        description = playlistData.description ?: "Imported from Spotify"
                    )
                )

                var orderIndex = 0
                for (match in matches) {
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
                Log.d(TAG, "Created playlist \"${playlistData.name}\" with $orderIndex tracks (ID: $playlistId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist", e)
                _errorMessage.value = "Failed to create playlist: ${e.message}"
                _importState.value = SpotifyImportState.Error
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
    }
}
