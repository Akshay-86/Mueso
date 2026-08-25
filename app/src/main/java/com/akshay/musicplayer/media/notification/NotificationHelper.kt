package com.akshay.musicplayer.media.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.akshay.musicplayer.MainActivity
import com.akshay.musicplayer.R

object NotificationHelper {

    private const val CHANNEL_ID = "mueso_background_tasks"
    private const val CHANNEL_NAME = "Downloads & Imports"
    private const val DOWNLOAD_NOTIFICATION_ID = 2001
    private const val IMPORT_NOTIFICATION_ID = 2002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for music downloads and playlist imports"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getLaunchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_CANCEL_DOWNLOAD = "com.akshay.musicplayer.ACTION_CANCEL_DOWNLOAD"
    const val ACTION_PLAY_DOWNLOADED = "com.akshay.musicplayer.ACTION_PLAY_DOWNLOADED"
    const val ACTION_OPEN_OFFLINE_LIBRARY = "com.akshay.musicplayer.ACTION_OPEN_OFFLINE_LIBRARY"
    const val EXTRA_TRACK_ID = "extra_track_id"
    const val EXTRA_FILE_PATH = "extra_file_path"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_ARTIST = "extra_artist"

    var onCancelDownloadRequested: ((Long) -> Unit)? = null
    var onPlayDownloadedTrackRequested: ((String) -> Unit)? = null

    fun showDownloadProgress(
        context: Context,
        trackId: Long,
        trackTitle: String,
        artist: String = "",
        completedCount: Int = 1,
        totalCount: Int = 1,
        progress: Float = 0f
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val percent = (progress * 100).toInt().coerceIn(0, 100)

        // Cancel Quick Action Intent
        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_TRACK_ID, trackId)
        }
        val pendingCancel = PendingIntent.getBroadcast(
            context,
            trackId.toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open App Intent
        val pendingOpen = getLaunchIntent(context)

        val title = if (totalCount > 1) {
            "Downloading ($completedCount/$totalCount): $trackTitle"
        } else {
            "Downloading \"$trackTitle\""
        }

        val artistSuffix = if (artist.isNotBlank() && !artist.equals("Unknown", ignoreCase = true)) " • $artist" else ""
        val contentText = "$percent% • $trackTitle$artistSuffix"

        val bigText = buildString {
            append("Track: ").append(trackTitle)
            if (artist.isNotBlank() && !artist.equals("Unknown", ignoreCase = true)) {
                append("\nArtist: ").append(artist)
            }
            if (totalCount > 1) {
                append("\nBatch: ").append(completedCount).append(" of ").append(totalCount).append(" tracks")
            }
            append("\nProgress: ").append(percent).append("%")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("$percent%")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Download", pendingCancel)
            .build()

        manager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun showDownloadComplete(
        context: Context,
        totalDownloaded: Int,
        lastTitle: String,
        lastArtist: String = "",
        lastFilePath: String? = null
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Open Library Intent
        val libraryIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_OFFLINE_LIBRARY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingLibrary = PendingIntent.getActivity(
            context,
            1001,
            libraryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalDownloaded > 1) {
            "Downloads Complete ($totalDownloaded songs)"
        } else {
            "Download Complete"
        }

        val artistSuffix = if (lastArtist.isNotBlank() && !lastArtist.equals("Unknown", ignoreCase = true)) " • $lastArtist" else ""
        val contentText = if (totalDownloaded > 1) {
            "Successfully downloaded $totalDownloaded tracks to device"
        } else {
            "Saved \"$lastTitle\"$artistSuffix to device"
        }

        val bigText = if (totalDownloaded > 1) {
            "Saved $totalDownloaded track(s) to your offline library.\nTap below to open your Offline Library."
        } else {
            "\"$lastTitle\"$artistSuffix has been downloaded and added to your offline library."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("Offline Library")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingLibrary)
            .addAction(android.R.drawable.ic_menu_agenda, "Open Library", pendingLibrary)

        // Quick action: Play Now
        if (!lastFilePath.isNullOrBlank()) {
            val playIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = ACTION_PLAY_DOWNLOADED
                putExtra(EXTRA_FILE_PATH, lastFilePath)
                putExtra(EXTRA_TITLE, lastTitle)
                putExtra(EXTRA_ARTIST, lastArtist)
            }
            val pendingPlay = PendingIntent.getBroadcast(
                context,
                2002,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Play Now", pendingPlay)
        }

        manager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    fun dismissDownloadNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    fun showImportProgress(
        context: Context,
        playlistName: String,
        matchedCount: Int,
        totalCount: Int
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val currentNum = (matchedCount + 1).coerceAtMost(totalCount)
        val percent = if (totalCount > 0) (currentNum * 100 / totalCount) else 0

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Importing Spotify Playlist ($percent%)")
            .setContentText("Matching track $currentNum of $totalCount • \"$playlistName\"")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalCount.coerceAtLeast(1), currentNum, false)
            .setContentIntent(getLaunchIntent(context))
            .build()

        manager.notify(IMPORT_NOTIFICATION_ID, notification)
    }

    fun showImportComplete(
        context: Context,
        playlistName: String,
        matchedCount: Int,
        totalCount: Int
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Spotify Import Ready")
            .setContentText("Matched $matchedCount of $totalCount tracks for \"$playlistName\". Tap to review.")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))
            .build()

        manager.notify(IMPORT_NOTIFICATION_ID, notification)
    }

    fun dismissImportNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(IMPORT_NOTIFICATION_ID)
    }

    private const val BACKUP_NOTIFICATION_ID = 2003
    var onCancelBackupRequested: (() -> Unit)? = null

    fun showBackupProgress(context: Context) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cancelIntent = Intent(context, BackupCancelReceiver::class.java)
        val pendingCancel = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Google Drive Backup")
            .setContentText("Backing up playlist changes to Google Drive...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(getLaunchIntent(context))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Backup", pendingCancel)
            .build()

        manager.notify(BACKUP_NOTIFICATION_ID, notification)
    }

    fun showBackupComplete(context: Context, message: String) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Google Drive Backup")
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))
            .build()

        manager.notify(BACKUP_NOTIFICATION_ID, notification)
    }

    fun dismissBackupNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(BACKUP_NOTIFICATION_ID)
    }
}

class BackupCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.onCancelBackupRequested?.invoke()
        NotificationHelper.dismissBackupNotification(context)
    }
}

class DownloadActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationHelper.ACTION_CANCEL_DOWNLOAD -> {
                val trackId = intent.getLongExtra(NotificationHelper.EXTRA_TRACK_ID, -1L)
                if (trackId != -1L) {
                    NotificationHelper.onCancelDownloadRequested?.invoke(trackId)
                }
            }
            NotificationHelper.ACTION_PLAY_DOWNLOADED -> {
                val filePath = intent.getStringExtra(NotificationHelper.EXTRA_FILE_PATH)
                if (!filePath.isNullOrBlank()) {
                    NotificationHelper.onPlayDownloadedTrackRequested?.invoke(filePath)

                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
