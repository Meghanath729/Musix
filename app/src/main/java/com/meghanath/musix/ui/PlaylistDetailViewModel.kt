package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.data.Playlist
import com.meghanath.musix.data.PlaylistRepository
import com.meghanath.musix.data.SongWithArtist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaylistRepository(app)
    private val playlistId = MutableStateFlow(-1L)

    // Screen calls this once with the playlist it opened
    fun setPlaylist(id: Long) {
        playlistId.value = id
    }

    val playlist: StateFlow<Playlist?> = playlistId
        .flatMapLatest { repo.observePlaylist(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: StateFlow<List<SongWithArtist>> = playlistId
        .flatMapLatest { repo.observeSongs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Whole library, for the "add songs" picker
    val allSongs: StateFlow<List<SongWithArtist>> = repo.observeAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSong(songId: Long) {
        viewModelScope.launch { repo.addSong(playlistId.value, songId) }
    }

    fun removeSong(songId: Long) {
        viewModelScope.launch { repo.removeSong(playlistId.value, songId) }
    }

    fun moveUp(songId: Long) = move(songId, -1)

    fun moveDown(songId: Long) = move(songId, 1)

    private fun move(songId: Long, delta: Int) {
        val list = songs.value
        val index = list.indexOfFirst { it.song.id == songId }
        val otherIndex = index + delta
        if (index < 0 || otherIndex !in list.indices) return
        val otherId = list[otherIndex].song.id
        viewModelScope.launch { repo.swap(playlistId.value, songId, otherId) }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.rename(playlistId.value, name) }
    }
}