package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.content.SharedPreferences
import com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository
import com.akshay.musicplayer.data.db.OnlinePlaylistDao
import com.akshay.musicplayer.data.db.PlaylistDao
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupManager(
    private val sharedPreferences: SharedPreferences,
    private val playlistDao: PlaylistDao,
    private val onlinePlaylistDao: OnlinePlaylistDao,
    private val coroutineScope: CoroutineScope,
    private val settingsManager: SettingsManager? = null
) {
    private fun buildBackupSettings(): com.akshay.musicplayer.data.backup.BackupSettings {
        return com.akshay.musicplayer.data.backup.BackupSettings(
            heroPlaylistId = sharedPreferences.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global",
            isDarkMode = sharedPreferences.getBoolean("is_dark_mode", true),
            showOnLockscreen = sharedPreferences.getBoolean("show_on_lockscreen", true),
            highRefreshRate = sharedPreferences.getBoolean("high_refresh_rate", false),
            audioQuality = sharedPreferences.getString("audio_quality", "Medium (160 kbps)") ?: "Medium (160 kbps)",
            thumbnailQuality = sharedPreferences.getString("thumbnail_quality", "Medium (480p)") ?: "Medium (480p)",
            downloadQuality = sharedPreferences.getString("download_quality", "Standard (256 kbps)") ?: "Standard (256 kbps)",
            playButtonPosition = sharedPreferences.getString("play_button_position", "Left") ?: "Left",
            enableLyrics = sharedPreferences.getBoolean("enable_lyrics", true),
            enableSponsorBlock = sharedPreferences.getBoolean("enable_sponsorblock", true),
            skipSponsor = sharedPreferences.getBoolean("skip_sponsor", true),
            skipSelfPromo = sharedPreferences.getBoolean("skip_self_promo", true),
            skipInteraction = sharedPreferences.getBoolean("skip_interaction", true),
            skipIntroOutro = sharedPreferences.getBoolean("skip_intro_outro", true),
            skipNonMusicOffTopic = sharedPreferences.getBoolean("skip_non_music_off_topic", true)
        )
    }

    private fun buildCustomLyricsMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val allEntries = sharedPreferences.all
        for ((key, value) in allEntries) {
            if (key.startsWith("custom_lyrics_") && value is String) {
                map[key] = value
            }
        }
        return map
    }

    private fun restoreCustomLyrics(customLyricsMap: Map<String, String>?) {
        if (customLyricsMap.isNullOrEmpty()) return
        val editor = sharedPreferences.edit()
        for ((key, jsonStr) in customLyricsMap) {
            editor.putString(key, jsonStr)
        }
        editor.apply()
    }

    private fun restoreBackupSettings(settings: com.akshay.musicplayer.data.backup.BackupSettings?) {
        if (settings == null) return
        sharedPreferences.edit()
            .putString("hero_playlist_id", settings.heroPlaylistId)
            .putBoolean("is_dark_mode", settings.isDarkMode)
            .putBoolean("show_on_lockscreen", settings.showOnLockscreen)
            .putBoolean("high_refresh_rate", settings.highRefreshRate)
            .putString("audio_quality", settings.audioQuality)
            .putString("thumbnail_quality", settings.thumbnailQuality)
            .putString("download_quality", settings.downloadQuality)
            .putString("play_button_position", settings.playButtonPosition)
            .putBoolean("enable_lyrics", settings.enableLyrics)
            .putBoolean("enable_sponsorblock", settings.enableSponsorBlock)
            .putBoolean("skip_sponsor", settings.skipSponsor)
            .putBoolean("skip_self_promo", settings.skipSelfPromo)
            .putBoolean("skip_interaction", settings.skipInteraction)
            .putBoolean("skip_intro_outro", settings.skipIntroOutro)
            .putBoolean("skip_non_music_off_topic", settings.skipNonMusicOffTopic)
            .apply()
        settingsManager?.reloadFromPreferences()
    }

    private val _hasUnbackedUpChanges = MutableStateFlow(sharedPreferences.getBoolean("has_unbacked_up_changes", false))
    val hasUnbackedUpChanges: StateFlow<Boolean> = _hasUnbackedUpChanges.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow(sharedPreferences.getLong("last_backup_timestamp", 0L))
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _isRestoreInProgress = MutableStateFlow(false)
    val isRestoreInProgress: StateFlow<Boolean> = _isRestoreInProgress.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow<String?>(sharedPreferences.getString("google_account_email", null))
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

    fun markDirty() {
        val autoBackupEnabled = sharedPreferences.getBoolean("auto_cloud_backup", true)
        if (!autoBackupEnabled) return
        _hasUnbackedUpChanges.value = true
        sharedPreferences.edit().putBoolean("has_unbacked_up_changes", true).apply()
    }

    fun setGoogleAccountEmail(email: String?) {
        _googleAccountEmail.value = email
        if (email != null) {
            sharedPreferences.edit().putString("google_account_email", email).apply()
        } else {
            sharedPreferences.edit().remove("google_account_email").apply()
        }
    }

    fun initGoogleDriveAccount(context: Context) {
        val repo = GoogleDriveBackupRepository(context)
        val lastAcc = repo.getSignedInAccount()
        _googleAccount.value = lastAcc
        if (lastAcc?.email != null) {
            setGoogleAccountEmail(lastAcc.email)
        }
    }

    fun setGoogleAccount(account: GoogleSignInAccount?) {
        _googleAccount.value = account
        if (account?.email != null) {
            setGoogleAccountEmail(account.email)
        }
    }

    fun signOutGoogle(context: Context) {
        val client = GoogleDriveBackupRepository(context).getGoogleSignInClient(context)
        client.signOut().addOnCompleteListener {
            _googleAccount.value = null
            setGoogleAccountEmail(null)
        }
    }

    fun connectAndBackupGoogleAccount(context: Context, email: String, onResult: (Boolean, String) -> Unit) {
        if (_isBackupInProgress.value || _isRestoreInProgress.value) return
        _isBackupInProgress.value = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = GoogleDriveBackupRepository(context)
                android.util.Log.d("MUESO_BACKUP", "Getting access token for $email...")
                val token = driveRepo.getAccessToken(email)
                    ?: throw Exception("Failed to acquire Google Drive access token")

                android.util.Log.d("MUESO_BACKUP", "Token acquired. Checking for existing backup...")
                val existingBackupInfo = driveRepo.findBackupFile(token)
                android.util.Log.d("MUESO_BACKUP", "Existing backup: ${existingBackupInfo != null}")

                android.util.Log.d("MUESO_BACKUP", "Fetching local playlists...")
                val localOnlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first() ?: emptyList()
                val localPlaylistEntities = playlistDao.getAllPlaylists().first() ?: emptyList()
                android.util.Log.d("MUESO_BACKUP", "Local: ${localOnlineEntities.size} online, ${localPlaylistEntities.size} local playlists")

                if (existingBackupInfo != null) {
                    android.util.Log.d("MUESO_BACKUP", "Downloading existing backup from Drive...")
                    val downloadResult = driveRepo.downloadBackup(email)
                    if (downloadResult.isSuccess) {
                        val backupData = downloadResult.getOrNull()
                        if (backupData != null) {
                            android.util.Log.d("MUESO_BACKUP", "Backup parsed. onlinePlaylists=${backupData.onlinePlaylists?.size ?: "null"}, localPlaylists=${backupData.localPlaylists?.size ?: "null"}")
                            var restoredCount = 0
                            val existingNames = localOnlineEntities.map { it.name }.toSet()

                            val onlineList = backupData.onlinePlaylists ?: emptyList()
                            for (op in onlineList) {
                                val opName = op.name ?: "Restored Playlist"
                                if (existingNames.contains(opName)) continue

                                val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                                    com.akshay.musicplayer.data.db.OnlinePlaylistEntity(
                                        name = opName,
                                        description = op.description,
                                        artworkUrl = op.artworkUrl,
                                        dateCreated = op.dateCreated
                                    )
                                )
                                val trackList = op.tracks ?: emptyList()
                                for (t in trackList) {
                                    val restoredTrackId = if (t.trackId > 0L) t.trackId else (System.currentTimeMillis() + (0..10000).random())
                                    onlinePlaylistDao.insertOnlineTrack(
                                        com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
                                            onlinePlaylistId = playlistId,
                                            trackId = restoredTrackId,
                                            title = t.title ?: "Unknown Title",
                                            artist = t.artist ?: "Unknown Artist",
                                            artworkUrl = t.artworkUrl,
                                            filePath = t.filePath ?: "",
                                            duration = t.duration,
                                            orderIndex = t.orderIndex
                                        )
                                    )
                                }
                                restoredCount++
                            }

                            withContext(Dispatchers.Main) {
                                restoreBackupSettings(backupData.settings)
                                restoreCustomLyrics(backupData.customLyrics)
                                _isBackupInProgress.value = false
                                setGoogleAccountEmail(email)
                                _hasUnbackedUpChanges.value = false
                                val modTime = try {
                                    if (!existingBackupInfo.modifiedTime.isNullOrEmpty()) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            java.time.Instant.parse(existingBackupInfo.modifiedTime).toEpochMilli()
                                        } else {
                                            System.currentTimeMillis()
                                        }
                                    } else System.currentTimeMillis()
                                } catch (e: Exception) { System.currentTimeMillis() }
                                _lastBackupTimestamp.value = modTime
                                sharedPreferences.edit()
                                    .putBoolean("has_unbacked_up_changes", false)
                                    .putLong("last_backup_timestamp", modTime)
                                    .apply()
                                onResult(true, if (restoredCount > 0) "Found existing backup! Restored $restoredCount playlist(s) & settings." else "Connected! Restored playlists & settings from Google Drive.")
                            }
                            return@launch
                        }
                    }
                }

                // No existing backup found — perform initial backup
                android.util.Log.d("MUESO_BACKUP", "No existing backup. Creating initial backup...")
                val backupOnlineList = localOnlineEntities.map { p ->
                    val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id) ?: emptyList()
                    com.akshay.musicplayer.data.backup.BackupOnlinePlaylist(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        dateCreated = p.dateCreated,
                        tracks = tracks.map { t ->
                            com.akshay.musicplayer.data.backup.BackupTrack(
                                trackId = t.trackId,
                                title = t.title,
                                artist = t.artist,
                                artworkUrl = t.artworkUrl,
                                filePath = t.filePath,
                                duration = t.duration,
                                orderIndex = t.orderIndex
                            )
                        }
                    )
                }

                val backupLocalList = localPlaylistEntities.map { p ->
                    val refs = playlistDao.getPlaylistTracksSync(p.id) ?: emptyList()
                    com.akshay.musicplayer.data.backup.BackupLocalPlaylist(
                        name = p.name,
                        dateCreated = p.dateCreated,
                        tracks = refs.map { ref ->
                            com.akshay.musicplayer.data.backup.BackupLocalTrackInfo(
                                title = "",
                                artist = "",
                                orderIndex = ref.orderIndex
                            )
                        }
                    )
                }

                val backupData = com.akshay.musicplayer.data.backup.MuesoBackupData(
                    onlinePlaylists = backupOnlineList,
                    localPlaylists = backupLocalList,
                    settings = buildBackupSettings(),
                    customLyrics = buildCustomLyricsMap()
                )

                android.util.Log.d("MUESO_BACKUP", "Uploading backup: ${backupOnlineList.size} online, ${backupLocalList.size} local playlists")
                val result = driveRepo.uploadBackup(email, backupData)
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    if (result.isSuccess) {
                        setGoogleAccountEmail(email)
                        _hasUnbackedUpChanges.value = false
                        val now = System.currentTimeMillis()
                        _lastBackupTimestamp.value = now
                        sharedPreferences.edit()
                            .putBoolean("has_unbacked_up_changes", false)
                            .putLong("last_backup_timestamp", now)
                            .apply()
                        onResult(true, "Connected! Playlists backed up to Google Drive.")
                    } else {
                        onResult(false, result.exceptionOrNull()?.message ?: "Sign-in verification failed")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MUESO_BACKUP", "connectAndBackupGoogleAccount error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    onResult(false, e.message ?: "Sign-in verification failed")
                }
            }
        }
    }

    private var activeBackupJob: kotlinx.coroutines.Job? = null

    fun cancelBackup(context: Context) {
        activeBackupJob?.cancel()
        activeBackupJob = null
        _isBackupInProgress.value = false
        com.akshay.musicplayer.media.notification.NotificationHelper.dismissBackupNotification(context)
    }

    fun performDriveBackup(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val email = _googleAccountEmail.value ?: _googleAccount.value?.email
        if (email.isNullOrBlank()) return onResult(false, "Not signed in to Google")
        if (_isBackupInProgress.value) return
        _isBackupInProgress.value = true

        com.akshay.musicplayer.media.notification.NotificationHelper.showBackupProgress(context)
        com.akshay.musicplayer.media.notification.NotificationHelper.onCancelBackupRequested = {
            cancelBackup(context)
            onResult(false, "Backup cancelled")
        }

        activeBackupJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = GoogleDriveBackupRepository(context)
                
                // Collect online playlists
                val onlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first()
                val backupOnlineList = onlineEntities.map { p ->
                    val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupOnlinePlaylist(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        dateCreated = p.dateCreated,
                        tracks = tracks.map { t ->
                            com.akshay.musicplayer.data.backup.BackupTrack(
                                trackId = t.trackId,
                                title = t.title,
                                artist = t.artist,
                                artworkUrl = t.artworkUrl,
                                filePath = t.filePath,
                                duration = t.duration,
                                orderIndex = t.orderIndex
                            )
                        }
                    )
                }

                // Collect local playlists
                val localEntities = playlistDao.getAllPlaylists().first()
                val backupLocalList = localEntities.map { p ->
                    val refs = playlistDao.getPlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupLocalPlaylist(
                        name = p.name,
                        dateCreated = p.dateCreated,
                        tracks = refs.map { ref ->
                            com.akshay.musicplayer.data.backup.BackupLocalTrackInfo(
                                title = "",
                                artist = "",
                                orderIndex = ref.orderIndex
                            )
                        }
                    )
                }

                val backupData = com.akshay.musicplayer.data.backup.MuesoBackupData(
                    onlinePlaylists = backupOnlineList,
                    localPlaylists = backupLocalList,
                    settings = buildBackupSettings(),
                    customLyrics = buildCustomLyricsMap()
                )

                val result = driveRepo.uploadBackup(email, backupData)
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    if (result.isSuccess) {
                        _hasUnbackedUpChanges.value = false
                        val now = System.currentTimeMillis()
                        _lastBackupTimestamp.value = now
                        sharedPreferences.edit()
                            .putBoolean("has_unbacked_up_changes", false)
                            .putLong("last_backup_timestamp", now)
                            .apply()
                        com.akshay.musicplayer.media.notification.NotificationHelper.showBackupComplete(context, "Playlists, Lyrics & Settings backed up to Google Drive!")
                        onResult(true, "Playlists, Lyrics & Settings backed up to Google Drive!")
                    } else {
                        com.akshay.musicplayer.media.notification.NotificationHelper.dismissBackupNotification(context)
                        onResult(false, result.exceptionOrNull()?.message ?: "Backup failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    com.akshay.musicplayer.media.notification.NotificationHelper.dismissBackupNotification(context)
                    onResult(false, e.message ?: "Backup failed")
                }
            }
        }
    }

    fun performDriveRestore(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val email = _googleAccountEmail.value ?: _googleAccount.value?.email
        if (email.isNullOrBlank()) return onResult(false, "Not signed in to Google")
        if (_isRestoreInProgress.value) return
        _isRestoreInProgress.value = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val driveRepo = GoogleDriveBackupRepository(context)
                val result = driveRepo.downloadBackup(email)

                if (result.isSuccess) {
                    val backupData = result.getOrNull()
                    if (backupData != null) {
                        val localOnlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first() ?: emptyList()
                        val existingNames = localOnlineEntities.map { it.name }.toSet()
                        var restoredCount = 0

                        val onlineList = backupData.onlinePlaylists ?: emptyList()
                        onlineList.forEach { op ->
                            val opName = op.name ?: "Restored Playlist"
                            if (existingNames.contains(opName)) return@forEach

                            val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                                com.akshay.musicplayer.data.db.OnlinePlaylistEntity(
                                    name = opName,
                                    description = op.description,
                                    artworkUrl = op.artworkUrl,
                                    dateCreated = op.dateCreated
                                )
                            )
                            val trackList = op.tracks ?: emptyList()
                            trackList.forEach { t ->
                                val restoredTrackId = if (t.trackId > 0L) t.trackId else (System.currentTimeMillis() + (0..10000).random())
                                onlinePlaylistDao.insertOnlineTrack(
                                    com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
                                        onlinePlaylistId = playlistId,
                                        trackId = restoredTrackId,
                                        title = t.title ?: "Unknown Title",
                                        artist = t.artist ?: "Unknown Artist",
                                        artworkUrl = t.artworkUrl,
                                        filePath = t.filePath ?: "",
                                        duration = t.duration,
                                        orderIndex = t.orderIndex
                                    )
                                )
                            }
                            restoredCount++
                        }

                        withContext(Dispatchers.Main) {
                            restoreBackupSettings(backupData.settings)
                            restoreCustomLyrics(backupData.customLyrics)
                            _isRestoreInProgress.value = false
                            if (restoredCount > 0) {
                                onResult(true, "Restored $restoredCount new online playlist(s) & app settings successfully!")
                            } else {
                                onResult(true, "Playlists, lyrics & settings are already up to date!")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _isRestoreInProgress.value = false
                            onResult(false, "No backup data found")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isRestoreInProgress.value = false
                        onResult(false, result.exceptionOrNull()?.message ?: "Restore failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isRestoreInProgress.value = false
                    onResult(false, e.message ?: "Restore failed")
                }
            }
        }
    }
}
