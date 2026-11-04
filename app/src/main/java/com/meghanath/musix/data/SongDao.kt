package com.meghanath.musix.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query(
        "SELECT * FROM songs WHERE artistId = :artistId " +
                "AND title LIKE '%' || :query || '%' " +
                "ORDER BY title COLLATE NOCASE"
    )
    fun observeSongs(artistId: Long, query: String): Flow<List<Song>>

    @Query(
        "SELECT COUNT(*) FROM songs WHERE artistId = :artistId " +
                "AND title = :title COLLATE NOCASE AND isLive = :isLive AND hasVideo = :hasVideo"
    )
    suspend fun countMatching(
        artistId: Long,
        title: String,
        isLive: Boolean,
        hasVideo: Boolean
    ): Int

    @Query(
        "SELECT COUNT(*) FROM songs WHERE artistId = :artistId " +
                "AND title = :title COLLATE NOCASE AND isLive = :isLive AND hasVideo = :hasVideo " +
                "AND id != :excludeId"
    )
    suspend fun countMatchingExcluding(
        artistId: Long,
        title: String,
        isLive: Boolean,
        hasVideo: Boolean,
        excludeId: Long
    ): Int

    @Query("UPDATE songs SET title = :newTitle WHERE id = :songId")
    suspend fun rename(songId: Long, newTitle: String)

    @Query("SELECT * FROM songs WHERE artistId = :artistId")
    suspend fun getSongsForArtist(artistId: Long): List<Song>

    @Insert
    suspend fun insert(song: Song): Long

    @Delete
    suspend fun delete(song: Song)

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<Song>>

    @Query(
        """
        SELECT songs.*, artists.name AS artistName FROM songs
        INNER JOIN artists ON songs.artistId = artists.id
        WHERE songs.isFavorite = 1
        ORDER BY songs.addedAt DESC
        """
    )
    fun observeFavoritesWithArtist(): Flow<List<SongWithArtist>>

    @Query(
        """
        SELECT songs.*, artists.name AS artistName FROM songs
        INNER JOIN artists ON songs.artistId = artists.id
        ORDER BY artists.name COLLATE NOCASE ASC, songs.title COLLATE NOCASE ASC
        """
    )
    fun observeAllWithArtist(): Flow<List<SongWithArtist>>

    @Query(
        "SELECT artists.id AS artistId, artists.name AS artistName, " +
                "COUNT(songs.id) AS songCount, COALESCE(SUM(songs.sizeBytes), 0) AS totalBytes " +
                "FROM artists LEFT JOIN songs ON songs.artistId = artists.id " +
                "GROUP BY artists.id ORDER BY totalBytes DESC"
    )
    fun observeArtistStorage(): Flow<List<ArtistStorage>>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM songs")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM songs WHERE hasVideo = 1")
    fun observeVideoBytes(): Flow<Long>

    @Query(
        "SELECT songs.*, artists.name AS artistName FROM songs " +
                "INNER JOIN artists ON artists.id = songs.artistId " +
                "ORDER BY songs.sizeBytes DESC LIMIT 20"
    )
    fun observeLargestSongs(): Flow<List<SongWithArtist>>

    @Query("SELECT * FROM songs")
    suspend fun getAllOnce(): List<Song>

    @Insert
    suspend fun insertAll(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}