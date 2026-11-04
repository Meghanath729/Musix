package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.data.AddResult
import com.meghanath.musix.data.Artist
import com.meghanath.musix.data.ArtistRepository
import com.meghanath.musix.data.MusixDatabase
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository =
        ArtistRepository(MusixDatabase.get(application).artistDao())

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    @OptIn(ExperimentalCoroutinesApi::class)
    val artists: StateFlow<List<Artist>> = _searchText
        .flatMapLatest { text -> repository.search(text.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchChange(text: String) {
        _searchText.value = text
    }

    fun addArtist(name: String, onResult: (AddResult) -> Unit) {
        viewModelScope.launch { onResult(repository.add(name)) }
    }

    fun renameArtist(artist: Artist, newName: String, onResult: (RenameResult) -> Unit) {
        viewModelScope.launch { onResult(repository.rename(artist, newName)) }
    }

    fun deleteArtist(artist: Artist) {
        viewModelScope.launch {
            repository.delete(artist)
            withContext(Dispatchers.IO) {
                SongFiles.deleteArtistFolder(getApplication<Application>(), artist.id)
            }
        }
    }
}