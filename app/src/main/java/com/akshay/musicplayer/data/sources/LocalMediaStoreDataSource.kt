package com.akshay.musicplayer.data.sources

import android.content.ContentResolver
import android.provider.MediaStore
import com.akshay.musicplayer.data.models.Track
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class LocalMediaStoreDataSource(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineContext
) : MediaStoreDataSource {

    override suspend fun getLocalTracks(): Result<List<Track>> = withContext(ioDispatcher) {
        try {
            val tracks = mutableListOf<Track>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
            val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                    val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                    val dateAdded = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) } catch (_: Exception) { 0L }
                    var effectiveDate = maxOf(dateModified, dateAdded)
                    if (effectiveDate == 0L && data.isNotEmpty()) {
                        try {
                            val f = java.io.File(data)
                            if (f.exists()) {
                                effectiveDate = f.lastModified() / 1000L
                            }
                        } catch (_: Exception) {}
                    }

                    if (data.isNotEmpty()) {
                        tracks.add(
                            Track(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                albumId = albumId,
                                data = data,
                                dateModified = effectiveDate
                            )
                        )
                    }
                }
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrackById(id: Long): Result<Track?> = withContext(ioDispatcher) {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(id.toString())

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                    val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                    val dateAdded = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) } catch (_: Exception) { 0L }
                    var effectiveDate = maxOf(dateModified, dateAdded)
                    if (effectiveDate == 0L && data.isNotEmpty()) {
                        try {
                            val f = java.io.File(data)
                            if (f.exists()) {
                                effectiveDate = f.lastModified() / 1000L
                            }
                        } catch (_: Exception) {}
                    }

                    return@withContext Result.success(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            albumId = albumId,
                            data = data,
                            dateModified = effectiveDate
                        )
                    )
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
