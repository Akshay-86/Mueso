package com.akshay.musicplayer.ui.state

import com.akshay.musicplayer.domain.models.TrackEntity

sealed class PlayerUiState {
    data object Loading : PlayerUiState()
    data class Success(val tracks: List<TrackEntity>) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
    data object Empty : PlayerUiState()
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)
