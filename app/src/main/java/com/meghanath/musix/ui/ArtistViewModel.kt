package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meghanath.musix.data.Artist
import com.meghanath.musix.data.MusixDatabase
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongRepository
import com.meghanath.musix.data.RenameResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.meghanath.musix.data.SongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtistViewModel(
    application: Application,
    private val artistId: Long
) : AndroidViewModel(application) {

    private val db = MusixDatabase.get(application)
    private val songRepository = SongRepository(db.songDao())

    val artist: StateFlow<Artist?> = db.artistDao()
        .observeArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<Song>> = _query
        .flatMapLatest { text -> songRepository.observeSongs(artistId, text.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allSongs: StateFlow<List<Song>> = songRepository.observeSongs(artistId, "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun renameSong(song: Song, newTitle: String, onResult: (RenameResult) -> Unit) {
        viewModelScope.launch { onResult(songRepository.rename(song, newTitle)) }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
            withContext(Dispatchers.IO) {
                SongFiles.deleteSongFile(getApplication<Application>(), song)
            }
        }
    }

    companion object {
        fun factory(application: Application, artistId: Long) = viewModelFactory {
            initializer { ArtistViewModel(application, artistId) }
        }
    }
}