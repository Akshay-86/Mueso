package com.akshay.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlinePlaylistDao {

    @Query("SELECT * FROM online_playlists ORDER BY dateCreated DESC")
    fun getAllOnlinePlaylists(): Flow<List<OnlinePlaylistEntity>>

    @Query("SELECT * FROM online_playlists WHERE id = :playlistId")
    fun getOnlinePlaylistById(playlistId: Long): OnlinePlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOnlinePlaylist(playlist: OnlinePlaylistEntity): Long

    @Query("DELETE FROM online_playlists WHERE id = :playlistId")
    fun deleteOnlinePlaylist(playlistId: Long): Int

    @Query("UPDATE online_playlists SET name = :newName WHERE id = :playlistId")
    fun renameOnlinePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE online_playlists SET name = :newName, description = :newDescription WHERE id = :playlistId")
    fun updateOnlinePlaylistDetails(playlistId: Long, newName: String, newDescription: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOnlineTrack(track: OnlinePlaylistTrackEntity): Long

    @Query("DELETE FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId AND trackId = :trackId")
    fun removeOnlineTrack(playlistId: Long, trackId: Long): Int

    @Query("SELECT * FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId ORDER BY orderIndex ASC")
    fun getOnlinePlaylistTracks(playlistId: Long): Flow<List<OnlinePlaylistTrackEntity>>

    @Query("SELECT * FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId ORDER BY orderIndex ASC")
    fun getOnlinePlaylistTracksSync(playlistId: Long): List<OnlinePlaylistTrackEntity>

    @Query("DELETE FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId")
    fun clearOnlinePlaylistTracks(playlistId: Long): Int

    @Query("SELECT * FROM online_playlists ORDER BY dateCreated DESC")
    fun getAllOnlinePlaylistsSync(): List<OnlinePlaylistEntity>

    @Query("UPDATE online_playlists SET artworkUrl = :artworkUrl WHERE id = :playlistId")
    fun updateOnlinePlaylistArtwork(playlistId: Long, artworkUrl: String?): Int

    @androidx.room.Update
    fun updateOnlinePlaylistTracks(tracks: List<OnlinePlaylistTrackEntity>)
}
