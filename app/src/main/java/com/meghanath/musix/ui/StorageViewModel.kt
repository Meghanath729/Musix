package com.meghanath.musix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.data.ArtistStorage
import com.meghanath.musix.data.DeviceSpace
import com.meghanath.musix.data.DeviceStorage
import com.meghanath.musix.data.MusixDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongRepository
import com.meghanath.musix.data.SongWithArtist
import kotlinx.coroutines.launch
import com.meghanath.musix.data.SongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
data class StorageSummary(
    val musixBytes: Long,
    val videoBytes: Long,
    val device: DeviceSpace
) {
    val audioBytes: Long get() = musixBytes - videoBytes
}

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val songDao = MusixDatabase.get(application).songDao()

    // Re-reads phone free space every time the song totals change,
    // and once each time this screen is opened.
    val summary: StateFlow<StorageSummary> = combine(
        songDao.observeTotalBytes(),
        songDao.observeVideoBytes()
    ) { total, video ->
        StorageSummary(total, video, DeviceStorage.read(getApplication()))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StorageSummary(0L, 0L, DeviceStorage.read(application))
    )

    val artists: StateFlow<List<ArtistStorage>> = songDao.observeArtistStorage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val songRepo = SongRepository(songDao)
    val largestSongs: StateFlow<List<SongWithArtist>> = songDao.observeLargestSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            songRepo.deleteSong(song)
            withContext(Dispatchers.IO) {
                SongFiles.deleteSongFile(getApplication<Application>(), song)
            }
        }
    }
}