package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.data.PlaylistRepository
import com.meghanath.musix.data.PlaylistWithCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaylistRepository(app)

    val playlists: StateFlow<List<PlaylistWithCount>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.create(name) }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    // onResult: true = added, false = song already in playlist
    fun addSong(playlistId: Long, songId: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repo.addSong(playlistId, songId)) }
    }
}