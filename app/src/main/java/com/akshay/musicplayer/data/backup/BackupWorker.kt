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
        val sharedPrefs = applicationContext.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
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
                    spotifyUrl = p.spotifyUrl,
                    tracks = tracks.map { t ->
                        val idKey = "bg_bias_${t.trackId}"
                        var bias = if (sharedPrefs.contains(idKey)) sharedPrefs.getFloat(idKey, 0f) else 0f
                        if (bias == 0f && t.filePath.isNotBlank()) {
                            val videoId = if (t.filePath.startsWith("online_") || t.filePath.startsWith("youtube_")) {
                                t.filePath.substringAfter("online_").substringAfter("youtube_")
                            } else null
                            if (!videoId.isNullOrBlank()) {
                                bias = sharedPrefs.getFloat("bg_bias_$videoId", 0f)
                            }
                        }
                        if (bias == 0f && t.title.isNotBlank() && t.artist.isNotBlank()) {
                            bias = sharedPrefs.getFloat("bg_bias_${t.title.trim().lowercase()}_${t.artist.trim().lowercase()}", 0f)
                        }

                        BackupTrack(
                            trackId = t.trackId,
                            title = t.title,
                            artist = t.artist,
                            artworkUrl = t.artworkUrl,
                            filePath = t.filePath,
                            duration = t.duration,
                            orderIndex = t.orderIndex,
                            backgroundBias = if (bias != 0f) bias else null
                        )
                    }
                )
            }

            // Gather Local Playlists
            val localPlaylistsEntities = db.playlistDao().getAllPlaylists().first()
            val localTrackMetadataMap = mutableMapOf<Long, Pair<String, String>>()
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST
                )
                applicationContext.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: ""
                        val artist = cursor.getString(artistCol) ?: ""
                        localTrackMetadataMap[id] = Pair(title, artist)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BackupWorker", "Failed to query local track metadata for backup: ${e.message}")
            }

            val backupLocalList = localPlaylistsEntities.map { p ->
                val trackRefs = db.playlistDao().getPlaylistTracksSync(p.id)
                BackupLocalPlaylist(
                    name = p.name,
                    dateCreated = p.dateCreated,
                    tracks = trackRefs.map { ref ->
                        val meta = localTrackMetadataMap[ref.trackId]
                        BackupLocalTrackInfo(
                            title = meta?.first ?: "",
                            artist = meta?.second ?: "",
                            orderIndex = ref.orderIndex
                        )
                    }
                )
            }

            val backupPlaylistsEnabled = sharedPrefs.getBoolean("backup_playlists", true)
            val backupLyricsEnabled = sharedPrefs.getBoolean("backup_lyrics", true)
            val backupSettingsEnabled = sharedPrefs.getBoolean("backup_settings", true)

            val customLyricsMap = mutableMapOf<String, String>()
            if (backupLyricsEnabled) {
                for ((key, value) in sharedPrefs.all) {
                    if ((key.startsWith("custom_lyrics_") || key.startsWith("lyrics_offset_")) && value != null) {
                        customLyricsMap[key] = value.toString()
                    }
                }
            }

            val backupSettings = if (backupSettingsEnabled) {
                val currentHeroId = sharedPrefs.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global"
                var heroName: String? = null
                if (currentHeroId.startsWith("custom_")) {
                    val numericId = currentHeroId.removePrefix("custom_").toLongOrNull()
                    if (numericId != null) {
                        val p = db.onlinePlaylistDao().getOnlinePlaylistById(numericId)
                        heroName = p?.name
                    }
                }
                BackupSettings(
                    heroPlaylistId = currentHeroId,
                    heroPlaylistName = heroName,
                    isDarkMode = sharedPrefs.getBoolean("is_dark_mode", true),
                    showOnLockscreen = sharedPrefs.getBoolean("show_on_lockscreen", true),
                    highRefreshRate = sharedPrefs.getBoolean("high_refresh_rate", false),
                    audioQuality = sharedPrefs.getString("audio_quality", "Medium (160 kbps)") ?: "Medium (160 kbps)",
                    thumbnailQuality = sharedPrefs.getString("thumbnail_quality", "Medium (480p)") ?: "Medium (480p)",
                    downloadQuality = sharedPrefs.getString("download_quality", "Medium (128 kbps)") ?: "Medium (128 kbps)",
                    playButtonPosition = sharedPrefs.getString("play_button_position", "Left") ?: "Left",
                    enableLyrics = sharedPrefs.getBoolean("enable_lyrics", true),
                    embedLyricsInDownload = sharedPrefs.getBoolean("embed_lyrics_in_download", true),
                    enableSponsorBlock = sharedPrefs.getBoolean("enable_sponsorblock", true),
                    skipSponsor = sharedPrefs.getBoolean("skip_sponsor", true),
                    skipSelfPromo = sharedPrefs.getBoolean("skip_self_promo", true),
                    skipInteraction = sharedPrefs.getBoolean("skip_interaction", true),
                    skipIntroOutro = sharedPrefs.getBoolean("skip_intro_outro", true),
                    skipNonMusicOffTopic = sharedPrefs.getBoolean("skip_non_music_off_topic", true),
                    themeMode = sharedPrefs.getString("theme_mode", "system") ?: "system",
                    usePureBlack = sharedPrefs.getBoolean("use_pure_black", false),
                    accentColorId = sharedPrefs.getString("accent_color_id", "sunset_orange") ?: "sunset_orange",
                    fontScaleOption = sharedPrefs.getString("font_scale_option", "standard") ?: "standard",
                    cornerRadiusOption = sharedPrefs.getString("corner_radius_option", "rounded") ?: "rounded",
                    lyricsFontSizeOption = sharedPrefs.getString("lyrics_font_size_option", "standard") ?: "standard"
                )
            } else null

            val backupData = MuesoBackupData(
                onlinePlaylists = if (backupPlaylistsEnabled) backupOnlineList else emptyList(),
                localPlaylists = if (backupPlaylistsEnabled) backupLocalList else emptyList(),
                settings = backupSettings,
                customLyrics = if (backupLyricsEnabled) customLyricsMap else null
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
