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

    private val _searchCategory = MutableStateFlow("All")
    val searchCategory: StateFlow<String> = _searchCategory.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackEntity>>(emptyList())
    val searchResults: StateFlow<List<TrackEntity>> = _searchResults.asStateFlow()

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
            return
        }

        _isSearchingOnline.value = true
        val currentTracks = getCurrentTracks()
        val localMatches = if (category == "All" || category == "Songs") {
            currentTracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        } else {
            emptyList()
        }
        _searchResults.value = localMatches

        searchJob = coroutineScope.launch {
            delay(300)
            try {
                val onlineResults = onlineRepository.searchOnlineTracks(query.trim(), category)
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

