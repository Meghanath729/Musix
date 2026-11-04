package com.meghanath.musix.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
    fun search(query: String): Flow<List<Artist>>
    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtist(id: Long): Flow<Artist?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: Artist): Long

    @Delete
    suspend fun delete(artist: Artist)

    @Query("UPDATE OR IGNORE artists SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String): Int

    @Query("SELECT * FROM artists")
    suspend fun getAllOnce(): List<Artist>

    @Insert
    suspend fun insertAll(artists: List<Artist>)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}