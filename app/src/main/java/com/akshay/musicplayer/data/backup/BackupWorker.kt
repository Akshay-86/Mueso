package com.akshay.musicplayer.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akshay.musicplayer.data.db.AppDatabase
import kotlinx.coroutines.flow.first

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sharedPrefs = applicationContext.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("auto_cloud_backup", true)) {
            return Result.success()
        }

        val driveRepo = GoogleDriveBackupRepository(applicationContext)
        val account = driveRepo.getSignedInAccount() ?: return Result.success()

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Gather Online Playlists
            val onlinePlaylistsEntities = db.onlinePlaylistDao().getAllOnlinePlaylists().first()
            val backupOnlineList = onlinePlaylistsEntities.map { p ->
                val tracks = db.onlinePlaylistDao().getOnlinePlaylistTracksSync(p.id)
                BackupOnlinePlaylist(
                    name = p.name,
                    description = p.description,
                    artworkUrl = p.artworkUrl,
                    dateCreated = p.dateCreated,
                    tracks = tracks.map { t ->
                        BackupTrack(
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

            // Gather Local Playlists
            val localPlaylistsEntities = db.playlistDao().getAllPlaylists().first()
            val backupLocalList = localPlaylistsEntities.map { p ->
                val trackRefs = db.playlistDao().getPlaylistTracksSync(p.id)
                BackupLocalPlaylist(
                    name = p.name,
                    dateCreated = p.dateCreated,
                    tracks = trackRefs.map { ref ->
                        BackupLocalTrackInfo(
                            title = "",
                            artist = "",
                            orderIndex = ref.orderIndex
                        )
                    }
                )
            }

            val customLyricsMap = mutableMapOf<String, String>()
            for ((key, value) in sharedPrefs.all) {
                if (key.startsWith("custom_lyrics_") && value is String) {
                    customLyricsMap[key] = value
                }
            }

            val backupData = MuesoBackupData(
                onlinePlaylists = backupOnlineList,
                localPlaylists = backupLocalList,
                customLyrics = customLyricsMap
            )

            val uploadResult = driveRepo.uploadBackup(account, backupData)
            if (uploadResult.isSuccess) {
                val prefs = applicationContext.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("has_unbacked_up_changes", false)
                    .putLong("last_backup_timestamp", System.currentTimeMillis())
                    .apply()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
