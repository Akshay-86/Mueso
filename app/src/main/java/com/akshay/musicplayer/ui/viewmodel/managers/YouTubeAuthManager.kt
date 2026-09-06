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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class YouTubeAccount(
    val id: String,
    val name: String,
    val handle: String?,
    val avatarUrl: String?,
    val cookieString: String
)

class YouTubeAuthManager(
    private val context: Context,
    private val onlineRepo: OnlineMusicRepository,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "MUESO_YTM_AUTH"
        private const val PREFS_NAME = "mueso_yt_auth"
        private const val KEY_SAVED_ACCOUNTS = "ytm_saved_accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "ytm_active_account_id"
        private const val KEY_AUTH_COOKIE = "ytm_auth_cookie"
        private const val KEY_USER_NAME = "ytm_user_name"
        private const val KEY_USER_HANDLE = "ytm_user_handle"
        private const val KEY_USER_AVATAR = "ytm_user_avatar"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentAccount = MutableStateFlow<YouTubeAccount?>(null)
    val currentAccount: StateFlow<YouTubeAccount?> = _currentAccount.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<YouTubeAccount>>(emptyList())
    val savedAccounts: StateFlow<List<YouTubeAccount>> = _savedAccounts.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userHandle = MutableStateFlow<String?>(null)
    val userHandle: StateFlow<String?> = _userHandle.asStateFlow()

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

    private object KeystoreCrypto {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mueso_ytm_auth_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private fun getOrCreateKey(): javax.crypto.SecretKey {
            val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        fun encrypt(plaintext: String): String {
            if (plaintext.isBlank()) return ""
            return try {
                val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, getOrCreateKey())
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
                val combined = java.nio.ByteBuffer.allocate(1 + iv.size + encrypted.size)
                    .put(iv.size.toByte())
                    .put(iv)
                    .put(encrypted)
                    .array()
                "enc:" + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.w(TAG, "Encryption failed, storing as-is", e)
                plaintext
            }
        }

        fun decrypt(ciphertext: String): String {
            if (ciphertext.isBlank()) return ""
            if (!ciphertext.startsWith("enc:")) return ciphertext
            return try {
                val rawBase64 = ciphertext.removePrefix("enc:")
                val combined = android.util.Base64.decode(rawBase64, android.util.Base64.NO_WRAP)
                val buffer = java.nio.ByteBuffer.wrap(combined)
                val ivLength = buffer.get().toInt()
                val iv = ByteArray(ivLength)
                buffer.get(iv)
                val encrypted = ByteArray(buffer.remaining())
                buffer.get(encrypted)
                val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
                val spec = javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
                val decrypted = cipher.doFinal(encrypted)
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Decryption failed", e)
                ciphertext
            }
        }
    }

    private fun loadAccountsFromPrefs(): List<YouTubeAccount> {
        val jsonStr = prefs.getString(KEY_SAVED_ACCOUNTS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<YouTubeAccount>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawCookie = obj.getString("cookieString")
                    list.add(
                        YouTubeAccount(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            handle = obj.optString("handle").takeIf { it.isNotBlank() },
                            avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                            cookieString = KeystoreCrypto.decrypt(rawCookie)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse saved accounts", e)
            }
        }
        val legacyCookie = prefs.getString(KEY_AUTH_COOKIE, null)
        if (!legacyCookie.isNullOrBlank()) {
            val name = prefs.getString(KEY_USER_NAME, "YouTube Music User") ?: "YouTube Music User"
            val handle = prefs.getString(KEY_USER_HANDLE, null)
            val avatar = prefs.getString(KEY_USER_AVATAR, null)
            return listOf(
                YouTubeAccount(
                    id = handle ?: "account_0",
                    name = name,
                    handle = handle,
                    avatarUrl = avatar,
                    cookieString = KeystoreCrypto.decrypt(legacyCookie)
                )
            )
        }
        return emptyList()
    }

    private fun saveAccountsToPrefs(accounts: List<YouTubeAccount>, activeId: String?) {
        val arr = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("handle", acc.handle ?: "")
            obj.put("avatarUrl", acc.avatarUrl ?: "")
            obj.put("cookieString", KeystoreCrypto.encrypt(acc.cookieString))
            arr.put(obj)
        }
        prefs.edit()
            .putString(KEY_SAVED_ACCOUNTS, arr.toString())
            .putString(KEY_ACTIVE_ACCOUNT_ID, activeId)
            .apply()
    }

    fun loadSavedSession() {
        val accounts = loadAccountsFromPrefs()
        _savedAccounts.value = accounts

        val activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        val activeAccount = accounts.find { it.id == activeId } ?: accounts.firstOrNull()

        if (activeAccount != null && activeAccount.cookieString.isNotBlank()) {
            _currentAccount.value = activeAccount
            _userName.value = activeAccount.name
            _userHandle.value = activeAccount.handle
            _userAvatar.value = activeAccount.avatarUrl

            onlineRepo.setAuthCookie(activeAccount.cookieString)
            val isValid = onlineRepo.innerTube.isLoggedIn()
            _isLoggedIn.value = isValid

            if (isValid) {
                refreshLibrary()
                coroutineScope.launch(Dispatchers.IO) {
                    val info = onlineRepo.innerTube.getAccountInfo()
                    if (info != null) {
                        _userName.value = info.name
                        _userHandle.value = info.handle
                        _userAvatar.value = info.avatarUrl
                        val updated = activeAccount.copy(
                            name = info.name,
                            handle = info.handle,
                            avatarUrl = info.avatarUrl
                        )
                        _currentAccount.value = updated
                        val updatedList = _savedAccounts.value.map { if (it.id == updated.id) updated else it }
                        _savedAccounts.value = updatedList
                        saveAccountsToPrefs(updatedList, updated.id)
                    }
                }
            }
        } else {
            _currentAccount.value = null
            _isLoggedIn.value = false
            _userName.value = null
            _userHandle.value = null
            _userAvatar.value = null
        }
        onSessionChanged?.invoke()
    }

    fun saveCookies(cookieString: String, name: String? = null, avatar: String? = null) {
        if (cookieString.isBlank()) return

        onlineRepo.setAuthCookie(cookieString)
        val isValid = onlineRepo.innerTube.isLoggedIn()
        _isLoggedIn.value = isValid

        coroutineScope.launch(Dispatchers.IO) {
            val info = onlineRepo.innerTube.getAccountInfo()
            val finalName = info?.name ?: name ?: "YouTube Music User"
            val finalHandle = info?.handle
            val finalAvatar = info?.avatarUrl ?: avatar

            _userName.value = finalName
            _userHandle.value = finalHandle
            _userAvatar.value = finalAvatar

            val accountId = finalHandle ?: "account_${System.currentTimeMillis()}"
            val newAccount = YouTubeAccount(
                id = accountId,
                name = finalName,
                handle = finalHandle,
                avatarUrl = finalAvatar,
                cookieString = cookieString
            )

            val currentList = _savedAccounts.value.toMutableList()
            currentList.removeAll { it.id == accountId || it.cookieString == cookieString }
            currentList.add(0, newAccount)
            _savedAccounts.value = currentList
            _currentAccount.value = newAccount

            saveAccountsToPrefs(currentList, newAccount.id)
            Log.d(TAG, "Saved YouTube Music account '$finalName' ($finalHandle). Total accounts: ${currentList.size}")

            refreshLibrary()
            onSessionChanged?.invoke()
        }
    }

    fun switchAccount(accountId: String) {
        val target = _savedAccounts.value.find { it.id == accountId } ?: return
        Log.d(TAG, "Switching to account: ${target.name} (${target.handle})")
        _currentAccount.value = target
        _userName.value = target.name
        _userHandle.value = target.handle
        _userAvatar.value = target.avatarUrl

        onlineRepo.setAuthCookie(target.cookieString)
        _isLoggedIn.value = onlineRepo.innerTube.isLoggedIn()

        saveAccountsToPrefs(_savedAccounts.value, target.id)
        refreshLibrary()
        onSessionChanged?.invoke()
    }

    fun removeAccount(accountId: String) {
        val currentList = _savedAccounts.value.toMutableList()
        currentList.removeAll { it.id == accountId }
        _savedAccounts.value = currentList

        if (_currentAccount.value?.id == accountId) {
            val next = currentList.firstOrNull()
            if (next != null) {
                switchAccount(next.id)
            } else {
                logout()
            }
        } else {
            saveAccountsToPrefs(currentList, _currentAccount.value?.id)
        }
    }

    fun logout() {
        val activeId = _currentAccount.value?.id
        val currentList = _savedAccounts.value.toMutableList()
        if (activeId != null) {
            currentList.removeAll { it.id == activeId }
        } else {
            currentList.clear()
        }
        _savedAccounts.value = currentList

        saveAccountsToPrefs(currentList, currentList.firstOrNull()?.id)

        if (currentList.isNotEmpty()) {
            switchAccount(currentList[0].id)
            return
        }

        onlineRepo.setAuthCookie(null)
        _isLoggedIn.value = false
        _currentAccount.value = null
        _userName.value = null
        _userHandle.value = null
        _userAvatar.value = null
        _likedSongs.value = emptyList()
        _userPlaylists.value = emptyList()

        // Clear Android WebView cookies & storage so next login doesn't auto-log in!
        coroutineScope.launch(Dispatchers.Main) {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
                Log.d(TAG, "Cleared WebView cookies and WebStorage successfully on logout")
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing WebView cookies", e)
            }
        }

        Log.d(TAG, "Logged out of YouTube Music")
        onSessionChanged?.invoke()
    }

    fun refreshLibrary() {
        if (!_isLoggedIn.value) return

        coroutineScope.launch(Dispatchers.IO) {
            _isLoadingLibrary.value = true
            try {
                val likedTracks = onlineRepo.innerTube.getLikedSongs().map { it.toTrackEntity() }
                _likedSongs.value = likedTracks
                Log.d(TAG, "Fetched ${likedTracks.size} Liked Songs from YouTube Music")

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

    fun addTrackToYouTubePlaylist(playlist: InnerTubePlaylist, track: TrackEntity, onResult: (Boolean) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onResult(false)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            var videoId = onlineRepo.extractVideoId(track)
            if (videoId.isBlank()) {
                // Fallback: search track title and artist
                val searchResults = onlineRepo.innerTube.search("${track.title} ${track.artist}", "Eg-KAQwIABAAGAEgASgB")
                videoId = searchResults.firstOrNull()?.videoId ?: ""
            }

            if (videoId.isBlank()) {
                withContext(Dispatchers.Main) { onResult(false) }
                return@launch
            }

            val success = onlineRepo.innerTube.addTrackToPlaylist(playlist.id, videoId)
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun createYouTubePlaylist(title: String, description: String = "", onResult: (String?) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onResult(null)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val playlistId = onlineRepo.innerTube.createPlaylist(title, description)
            if (playlistId != null) {
                refreshLibrary()
            }
            withContext(Dispatchers.Main) {
                onResult(playlistId)
            }
        }
    }

    fun editYouTubePlaylistDetails(playlistId: String, newName: String, newDescription: String = "", onResult: (Boolean) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onResult(false)
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            val success = onlineRepo.innerTube.editPlaylistDetails(playlistId, newName, newDescription)
            if (success) {
                val updated = onlineRepo.innerTube.getUserPlaylists()
                _userPlaylists.value = updated
            }
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun renameYouTubePlaylist(playlistId: String, newName: String, onResult: (Boolean) -> Unit = {}) =
        editYouTubePlaylistDetails(playlistId, newName, "", onResult)

    fun deleteYouTubePlaylist(playlistId: String, onResult: (Boolean) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onResult(false)
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            val success = onlineRepo.innerTube.deletePlaylist(playlistId)
            if (success) {
                val updated = onlineRepo.innerTube.getUserPlaylists()
                _userPlaylists.value = updated
            }
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun removeTrackFromYouTubePlaylist(playlistId: String, videoId: String, onResult: (Boolean) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onResult(false)
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            val success = onlineRepo.innerTube.removeTrackFromPlaylist(playlistId, videoId)
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }
}

