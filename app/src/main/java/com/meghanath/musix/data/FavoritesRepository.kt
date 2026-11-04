package com.meghanath.musix.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(context: Context) {
    private val songDao = MusixDatabase.get(context).songDao()

    fun observeFavorites(): Flow<List<SongWithArtist>> = songDao.observeFavoritesWithArtist()

    suspend fun setFavorite(songId: Long, favorite: Boolean) =
        songDao.setFavorite(songId, favorite)
}