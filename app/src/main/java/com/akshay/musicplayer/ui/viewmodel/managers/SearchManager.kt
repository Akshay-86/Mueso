package com.akshay.musicplayer.ui.viewmodel.managers

import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.data.remote.innertube.InnerTubeArtist
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchManager(
    private val onlineRepository: OnlineMusicRepository,
    private val coroutineScope: CoroutineScope,
    private val getCurrentTracks: () -> List<TrackEntity>
) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCategory = MutableStateFlow("All")
    val searchCategory: StateFlow<String> = _searchCategory.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackEntity>>(emptyList())
    val searchResults: StateFlow<List<TrackEntity>> = _searchResults.asStateFlow()

    private val _artistResults = MutableStateFlow<List<InnerTubeArtist>>(emptyList())
    val artistResults: StateFlow<List<InnerTubeArtist>> = _artistResults.asStateFlow()

    private val _playlistResults = MutableStateFlow<List<InnerTubePlaylist>>(emptyList())
    val playlistResults: StateFlow<List<InnerTubePlaylist>> = _playlistResults.asStateFlow()

    private data class CachedCategoryResult(
        val tracks: List<TrackEntity> = emptyList(),
        val artists: List<InnerTubeArtist> = emptyList(),
        val playlists: List<InnerTubePlaylist> = emptyList()
    )

    private val searchCache = HashMap<String, CachedCategoryResult>()
    private var searchJob: Job? = null

    fun setSearchCategory(category: String) {
        if (_searchCategory.value == category) return
        _searchCategory.value = category
        triggerSearch(_searchQuery.value, category)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        triggerSearch(query, _searchCategory.value)
    }

    private fun triggerSearch(query: String, category: String) {
        searchJob?.cancel()
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            _isSearchingOnline.value = false
            _searchResults.value = emptyList()
            _artistResults.value = emptyList()
            _playlistResults.value = emptyList()
            return
        }

        val cacheKey = "$q#${category.trim().lowercase()}"
        val cached = searchCache[cacheKey]

        val currentTracks = getCurrentTracks()
        val localMatches = if (category.equals("All", ignoreCase = true) || category.equals("Songs", ignoreCase = true)) {
            currentTracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        } else {
            emptyList()
        }

        if (cached != null) {
            _searchResults.value = cached.tracks
            _artistResults.value = cached.artists
            _playlistResults.value = cached.playlists
            _isSearchingOnline.value = false
            return
        }

        // If switching from "All" to "Songs", keep existing songs visible while fetching extended list
        if (category.equals("Songs", ignoreCase = true) && _searchResults.value.isNotEmpty()) {
            _isSearchingOnline.value = false
        } else if (category.equals("Artists", ignoreCase = true) && _artistResults.value.isNotEmpty()) {
            _isSearchingOnline.value = false
        } else if ((category.equals("Albums", ignoreCase = true) || category.equals("Playlists", ignoreCase = true)) && _playlistResults.value.isNotEmpty()) {
            _isSearchingOnline.value = false
        } else {
            _searchResults.value = localMatches
            _isSearchingOnline.value = true
        }

        searchJob = coroutineScope.launch {
            delay(250)
            try {
                when (category.trim().lowercase()) {
                    "artists", "artist" -> {
                        _searchResults.value = emptyList()
                        _playlistResults.value = emptyList()
                        val artists = onlineRepository.searchArtists(query.trim())
                        _artistResults.value = artists
                        searchCache[cacheKey] = CachedCategoryResult(artists = artists)
                    }
                    "albums", "album" -> {
                        _searchResults.value = emptyList()
                        _artistResults.value = emptyList()
                        val albums = onlineRepository.searchPlaylists(query.trim(), "EgWKAQIYAWoSEAQQCRADEAUQEBAKEBUQERAO")
                        _playlistResults.value = albums
                        searchCache[cacheKey] = CachedCategoryResult(playlists = albums)
                    }
                    "playlists", "playlist" -> {
                        _searchResults.value = emptyList()
                        _artistResults.value = emptyList()
                        val playlists = onlineRepository.searchPlaylists(query.trim(), "EgeKAQQoAEABahIQBBAJEAMQBRAQEAoQFRAREA4%3D")
                        _playlistResults.value = playlists
                        searchCache[cacheKey] = CachedCategoryResult(playlists = playlists)
                    }
                    "all" -> {
                        val tracksDeferred = async { onlineRepository.searchOnlineTracks(query.trim(), "All") }
                        val artistsDeferred = async { onlineRepository.searchArtists(query.trim()) }
                        val albumsDeferred = async { onlineRepository.searchPlaylists(query.trim(), "EgWKAQIYAWoSEAQQCRADEAUQEBAKEBUQERAO") }

                        val onlineResults = tracksDeferred.await()
                        val localIds = localMatches.map { it.id }.toSet()
                        val combinedTracks = localMatches + onlineResults.filter { it.id !in localIds }
                        val artists = artistsDeferred.await().take(4)
                        val albums = albumsDeferred.await().take(4)

                        _searchResults.value = combinedTracks
                        _artistResults.value = artists
                        _playlistResults.value = albums

                        searchCache[cacheKey] = CachedCategoryResult(
                            tracks = combinedTracks,
                            artists = artists,
                            playlists = albums
                        )
                    }
                    else -> {
                        _artistResults.value = emptyList()
                        _playlistResults.value = emptyList()
                        val onlineResults = onlineRepository.searchOnlineTracks(query.trim(), category)
                        val localIds = localMatches.map { it.id }.toSet()
                        val combinedTracks = localMatches + onlineResults.filter { it.id !in localIds }
                        _searchResults.value = combinedTracks
                        searchCache[cacheKey] = CachedCategoryResult(tracks = combinedTracks)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun getSearchResults(): List<TrackEntity> = _searchResults.value
}

