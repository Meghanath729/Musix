package com.meghanath.musix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<Playlist>>

    // Returns -1 instead of crashing if the song is already in this playlist
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(entry: PlaylistSong): Long

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Long?

    @Query("SELECT position FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun getPosition(playlistId: Long, songId: Long): Long?

    @Query(
        """
        UPDATE playlist_songs
        SET position = CASE songId WHEN :songA THEN :positionB WHEN :songB THEN :positionA END
        WHERE playlistId = :playlistId AND songId IN (:songA, :songB)
        """
    )
    suspend fun swapPositions(
        playlistId: Long,
        songA: Long,
        songB: Long,
        positionA: Long,
        positionB: Long
    )

    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
        WHERE playlist_songs.playlistId = :playlistId
                ORDER BY playlist_songs.position ASC, playlist_songs.addedAt ASC
        """
    )
    fun observeSongs(playlistId: Long): Flow<List<Song>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    @Query(
        """
        SELECT playlists.*,
            (SELECT COUNT(*) FROM playlist_songs
             WHERE playlist_songs.playlistId = playlists.id) AS songCount
        FROM playlists
        ORDER BY playlists.createdAt DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT songs.*, artists.name AS artistName FROM songs
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
        INNER JOIN artists ON songs.artistId = artists.id
        WHERE playlist_songs.playlistId = :playlistId
                ORDER BY playlist_songs.position ASC, playlist_songs.addedAt ASC
        """
    )
    fun observeSongsWithArtist(playlistId: Long): Flow<List<SongWithArtist>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<Playlist>

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllLinksOnce(): List<PlaylistSong>

    @Insert
    suspend fun insertAllPlaylists(playlists: List<Playlist>)

    @Insert
    suspend fun insertAllLinks(links: List<PlaylistSong>)

    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllLinks()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}