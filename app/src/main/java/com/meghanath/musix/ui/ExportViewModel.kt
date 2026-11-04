package com.meghanath.musix.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.backup.ExportException
import com.meghanath.musix.backup.SongExporter
import com.meghanath.musix.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.meghanath.musix.backup.TransferService

sealed interface ExportState {
    data object Idle : ExportState
    data class Working(val title: String, val doneBytes: Long, val totalBytes: Long) : ExportState
    data class Done(val title: String) : ExportState
    data class Failed(val message: String) : ExportState
}

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var job: Job? = null

    /** [target] is the file the user picked in the "Save as" screen. */
    fun start(song: Song, target: Uri) {
        if (job?.isActive == true) return
        _state.value = ExportState.Working(song.title, 0L, song.sizeBytes)
        TransferService.start(getApplication<Application>(), "Exporting song")

        job = viewModelScope.launch {
            try {
                SongExporter.export(getApplication(), song, target) { done, total ->
                    _state.value = ExportState.Working(song.title, done, total)
                }
                _state.value = ExportState.Done(song.title)
            } catch (e: CancellationException) {
                _state.value = ExportState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = ExportState.Failed(friendly(e))
            } finally {
                TransferService.stop(getApplication<Application>())
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Called when the user dismisses the "done" or "failed" message. */
    fun dismiss() {
        if (_state.value !is ExportState.Working) _state.value = ExportState.Idle
    }

    private fun friendly(e: Exception): String = when {
        e is ExportException -> e.message ?: "Export failed."
        e.message?.contains("ENOSPC") == true -> "Not enough free space at the chosen location."
        else -> "Export failed: ${e.message ?: "unknown error"}"
    }
}