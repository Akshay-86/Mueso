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
    private val coroutineScope: CoroutineScope
) {
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
                val token = driveRepo.getAccessToken(email)
                    ?: throw Exception("Failed to acquire Google Drive access token")

                val existingBackupInfo = driveRepo.findBackupFile(token)

                val localOnlineEntities = onlinePlaylistDao.getAllOnlinePlaylists().first()
                val localPlaylistEntities = playlistDao.getAllPlaylists().first()

                if (existingBackupInfo != null) {
                    val downloadResult = driveRepo.downloadBackup(email)
                    if (downloadResult.isSuccess) {
                        val backupData = downloadResult.getOrNull()
                        if (backupData != null) {
                            var restoredCount = 0
                            val existingNames = localOnlineEntities.map { it.name }.toSet()

                            backupData.onlinePlaylists.forEach { op ->
                                if (!existingNames.contains(op.name)) {
                                    val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                                        com.akshay.musicplayer.data.db.OnlinePlaylistEntity(
                                            name = op.name,
                                            description = op.description,
                                            artworkUrl = op.artworkUrl,
                                            dateCreated = op.dateCreated
                                        )
                                    )
                                    op.tracks.forEach { t ->
                                        onlinePlaylistDao.insertOnlineTrack(
                                            com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
                                                onlinePlaylistId = playlistId,
                                                trackId = System.currentTimeMillis() + (0..10000).random(),
                                                title = t.title,
                                                artist = t.artist,
                                                artworkUrl = t.artworkUrl,
                                                filePath = t.filePath,
                                                duration = t.duration,
                                                orderIndex = t.orderIndex
                                            )
                                        )
                                    }
                                    restoredCount++
                                }
                            }

                            withContext(Dispatchers.Main) {
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
                                onResult(true, if (restoredCount > 0) "Found existing backup! Restored $restoredCount playlist(s)." else "Connected! Restored playlists from Google Drive.")
                            }
                            return@launch
                        }
                    }
                }

                // Initial backup
                val backupOnlineList = localOnlineEntities.map { p ->
                    val tracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(p.id)
                    com.akshay.musicplayer.data.backup.BackupOnlinePlaylist(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        dateCreated = p.dateCreated,
                        tracks = tracks.map { t ->
                            com.akshay.musicplayer.data.backup.BackupTrack(
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
                    localPlaylists = backupLocalList
                )

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
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
                    onResult(false, e.message ?: "Sign-in verification failed")
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun performDriveBackup(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val email = _googleAccountEmail.value ?: _googleAccount.value?.email
        if (email.isNullOrBlank()) return onResult(false, "Not signed in to Google")
        if (_isBackupInProgress.value) return
        _isBackupInProgress.value = true

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
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
                    localPlaylists = backupLocalList
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
                        onResult(true, "Playlists backed up to Google Drive!")
                    } else {
                        onResult(false, result.exceptionOrNull()?.message ?: "Backup failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBackupInProgress.value = false
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

                withContext(Dispatchers.Main) {
                    _isRestoreInProgress.value = false
                    if (result.isSuccess) {
                        val backupData = result.getOrNull()
                        if (backupData != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                backupData.onlinePlaylists.forEach { op ->
                                    val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                                        com.akshay.musicplayer.data.db.OnlinePlaylistEntity(
                                            name = op.name,
                                            description = op.description,
                                            artworkUrl = op.artworkUrl,
                                            dateCreated = op.dateCreated
                                        )
                                    )
                                    op.tracks.forEach { t ->
                                        onlinePlaylistDao.insertOnlineTrack(
                                            com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity(
                                                onlinePlaylistId = playlistId,
                                                trackId = System.currentTimeMillis() + (0..10000).random(),
                                                title = t.title,
                                                artist = t.artist,
                                                artworkUrl = t.artworkUrl,
                                                filePath = t.filePath,
                                                duration = t.duration,
                                                orderIndex = t.orderIndex
                                            )
                                        )
                                    }
                                }
                            }
                            onResult(true, "Restored ${backupData.onlinePlaylists.size} online playlists successfully!")
                        } else {
                            onResult(false, "No backup data found")
                        }
                    } else {
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
