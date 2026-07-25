package com.akshay.musicplayer.ui.viewmodel.managers

import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
        val currentTracks = getCurrentTracks()
        val localMatches = currentTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        _searchResults.value = localMatches

        searchJob = coroutineScope.launch {
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
}
