package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.data.remote.innertube.InnerTubePlaylist
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YouTubeAuthManager(
    private val context: Context,
    private val onlineRepo: OnlineMusicRepository,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "MUESO_YTM_AUTH"
        private const val PREFS_NAME = "mueso_yt_auth"
        private const val KEY_AUTH_COOKIE = "ytm_auth_cookie"
        private const val KEY_USER_NAME = "ytm_user_name"
        private const val KEY_USER_AVATAR = "ytm_user_avatar"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<TrackEntity>>(emptyList())
    val likedSongs: StateFlow<List<TrackEntity>> = _likedSongs.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<InnerTubePlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<InnerTubePlaylist>> = _userPlaylists.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary: StateFlow<Boolean> = _isLoadingLibrary.asStateFlow()

    var onSessionChanged: (() -> Unit)? = null

    init {
        loadSavedSession()
    }

    fun loadSavedSession() {
        val savedCookie = prefs.getString(KEY_AUTH_COOKIE, null)
        if (!savedCookie.isNullOrBlank()) {
            onlineRepo.innerTube.setAuthCookie(savedCookie)
            val isValid = onlineRepo.innerTube.isLoggedIn()
            _isLoggedIn.value = isValid
            _userName.value = prefs.getString(KEY_USER_NAME, "YouTube Music User")
            _userAvatar.value = prefs.getString(KEY_USER_AVATAR, null)

            if (isValid) {
                refreshLibrary()
            }
        } else {
            _isLoggedIn.value = false
        }
        onSessionChanged?.invoke()
    }

    fun saveCookies(cookieString: String, name: String? = null, avatar: String? = null) {
        if (cookieString.isBlank()) return

        prefs.edit()
            .putString(KEY_AUTH_COOKIE, cookieString)
            .putString(KEY_USER_NAME, name ?: "YouTube Music User")
            .putString(KEY_USER_AVATAR, avatar)
            .apply()

        onlineRepo.innerTube.setAuthCookie(cookieString)
        _isLoggedIn.value = onlineRepo.innerTube.isLoggedIn()
        _userName.value = name ?: "YouTube Music User"
        _userAvatar.value = avatar

        Log.d(TAG, "Saved YouTube Music auth cookie successfully (logged in: ${_isLoggedIn.value})")
        refreshLibrary()
        onSessionChanged?.invoke()
    }

    fun logout() {
        prefs.edit().clear().apply()
        onlineRepo.innerTube.setAuthCookie(null)
        _isLoggedIn.value = false
        _userName.value = null
        _userAvatar.value = null
        _likedSongs.value = emptyList()
        _userPlaylists.value = emptyList()
        Log.d(TAG, "Logged out of YouTube Music")
        onSessionChanged?.invoke()
    }

    fun refreshLibrary() {
        if (!_isLoggedIn.value) return

        coroutineScope.launch(Dispatchers.IO) {
            _isLoadingLibrary.value = true
            try {
                // 1. Fetch Liked Songs ("LM")
                val likedTracks = onlineRepo.innerTube.getLikedSongs().map { it.toTrackEntity() }
                _likedSongs.value = likedTracks
                Log.d(TAG, "Fetched ${likedTracks.size} Liked Songs from YouTube Music")

                // 2. Fetch User Playlists
                val playlists = onlineRepo.innerTube.getUserPlaylists()
                _userPlaylists.value = playlists
                Log.d(TAG, "Fetched ${playlists.size} User Playlists from YouTube Music")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh library from YouTube Music", e)
            } finally {
                _isLoadingLibrary.value = false
            }
        }
    }

    fun toggleLikeSong(track: TrackEntity, isLiked: Boolean) {
        val videoId = onlineRepo.extractVideoId(track)
        if (videoId.isBlank() || !_isLoggedIn.value) return

        coroutineScope.launch(Dispatchers.IO) {
            val success = onlineRepo.innerTube.setTrackLiked(videoId, isLiked)
            if (success) {
                val current = _likedSongs.value.toMutableList()
                if (isLiked) {
                    if (current.none { it.id == track.id }) {
                        current.add(0, track)
                    }
                } else {
                    current.removeAll { it.id == track.id }
                }
                _likedSongs.value = current
            }
        }
    }
}
