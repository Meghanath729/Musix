package com.meghanath.musix.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meghanath.musix.backup.BackupRestorer
import com.meghanath.musix.backup.BackupWriter
import com.meghanath.musix.backup.RestoreException
import com.meghanath.musix.backup.RestoreStage
import com.meghanath.musix.backup.TransferService
import com.meghanath.musix.data.MusixDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.util.zip.ZipException

sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val doneBytes: Long, val totalBytes: Long) : BackupState
    data class Done(val songs: Int, val skipped: Int, val bytes: Long) : BackupState
    data class Failed(val message: String) : BackupState
}

sealed interface RestoreState {
    data object Idle : RestoreState
    data class Working(val stage: RestoreStage, val doneBytes: Long, val totalBytes: Long) : RestoreState
    data class Done(val artists: Int, val songs: Int, val playlists: Int) : RestoreState
    data class Failed(val message: String) : RestoreState
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MusixDatabase.get(application)

    val libraryBytes: StateFlow<Long> = db.songDao().observeTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    // Only one backup or restore can run at a time.
    private var job: Job? = null

    // ---------------- Backup ----------------

    /** [target] is the file the user picked in the "Save as" screen. */
    fun startBackup(target: Uri) {
        if (job?.isActive == true) return
        val resolver = getApplication<Application>().contentResolver
        _state.value = BackupState.Working(0L, 0L)
        TransferService.start(getApplication<Application>(), "Creating backup")

        job = viewModelScope.launch {
            try {
                val snapshot = BackupWriter.loadSnapshot(
                    db.artistDao(), db.songDao(), db.playlistDao()
                )
                val result = withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(target, "wt")
                        ?: throw IllegalStateException("Could not open the chosen file.")
                    stream.use { out ->
                        BackupWriter.write(getApplication(), snapshot, out) { p ->
                            _state.value = BackupState.Working(p.doneBytes, p.totalBytes)
                        }
                    }
                }
                _state.value = BackupState.Done(
                    result.songsWritten, result.songsSkipped, result.bytesWritten
                )
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { deletePartial(resolver, target) }
                _state.value = BackupState.Idle
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { deletePartial(resolver, target) }
                _state.value = BackupState.Failed(friendly(e))
            } finally {
                TransferService.stop(getApplication<Application>())
            }
        }
    }

    // ---------------- Restore ----------------

    /**
     * [source] is the backup file the user picked.
     * [stopPlayback] is called first, so the player lets go of the old song files.
     */
    fun startRestore(source: Uri, stopPlayback: () -> Unit) {
        if (job?.isActive == true) return
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        _restoreState.value = RestoreState.Working(RestoreStage.CHECKING, 0L, 0L)
        stopPlayback()
        TransferService.start(app, "Restoring backup")

        job = viewModelScope.launch {
            try {
                val result = BackupRestorer.restore(
                    context = app,
                    db = db,
                    openBackup = { resolver.openInputStream(source) },
                    onProgress = { p ->
                        _restoreState.value =
                            RestoreState.Working(p.stage, p.doneBytes, p.totalBytes)
                    }
                )
                _restoreState.value =
                    RestoreState.Done(result.artists, result.songs, result.playlists)
            } catch (e: CancellationException) {
                _restoreState.value = RestoreState.Idle
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MusixRestore", "Restore failed", e)
                _restoreState.value = RestoreState.Failed(friendlyRestore(e))
            } finally {
                TransferService.stop(app)
            }
        }
    }

    // ---------------- Shared ----------------

    fun cancel() {
        job?.cancel()
    }

    /** Called when the user dismisses a "done" or "failed" message. */
    fun dismiss() {
        if (_state.value !is BackupState.Working) _state.value = BackupState.Idle
        if (_restoreState.value !is RestoreState.Working) _restoreState.value = RestoreState.Idle
    }

    private fun deletePartial(resolver: ContentResolver, uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
            // Nothing more we can do; the user can delete the file by hand.
        }
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("ENOSPC") == true -> "Not enough free space at the chosen location."
        else -> e.message ?: "Backup failed."
    }

    private fun friendlyRestore(e: Exception): String = when {
        e is RestoreException -> e.message ?: "Restore failed."
        e is ZipException || e is EOFException ->
            "The backup file is damaged or incomplete (${e.javaClass.simpleName}: ${e.message})."
        e.message?.contains("ENOSPC") == true ->
            "Not enough free space on the phone to restore this backup."
        else -> "Restore failed: ${e.message ?: "unknown error"}"
    }
}