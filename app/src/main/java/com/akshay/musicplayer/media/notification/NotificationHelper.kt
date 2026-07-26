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

    fun showDownloadProgress(
        context: Context,
        trackTitle: String,
        completedCount: Int,
        totalCount: Int,
        progress: Float
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = if (totalCount > 1) {
            "Downloading track $completedCount of $totalCount: \"$trackTitle\""
        } else {
            "Downloading \"$trackTitle\"..."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Music")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .setContentIntent(getLaunchIntent(context))
            .build()

        manager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun showDownloadComplete(context: Context, totalDownloaded: Int, lastTitle: String) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = if (totalDownloaded > 1) {
            "Successfully downloaded $totalDownloaded tracks"
        } else {
            "Saved \"$lastTitle\" to device"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(contentText)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))
            .build()

        manager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
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
