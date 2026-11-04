package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.data.FavoritesRepository
import com.meghanath.musix.data.SongWithArtist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FavoritesRepository(app)

    val favorites: StateFlow<List<SongWithArtist>> = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Quick lookup for heart buttons: is this song id a favorite?
    val favoriteIds: StateFlow<Set<Long>> = favorites
        .map { list -> list.map { it.song.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setFavorite(songId: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(songId, favorite) }
    }
}