package com.meghanath.musix.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meghanath.musix.data.MusixDatabase
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongRepository
import com.meghanath.musix.data.SongFiles
import com.meghanath.musix.download.DownloaderEngine
import com.meghanath.musix.download.EngineState
import com.meghanath.musix.download.LinkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import com.meghanath.musix.download.DownloadService

data class PickedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val fileHasVideo: Boolean
)

data class AddSongUiState(
    val picked: PickedFile? = null,
    val title: String = "",
    val isLive: Boolean = false,
    val wantsVideo: Boolean = false,
    val reading: Boolean = false,
    val saving: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
    val linkMode: Boolean = false,
    val link: String = "",
    val fetching: Boolean = false,
    val linkInfo: LinkInfo? = null,
    val downloading: Boolean = false,
    val statusText: String = "Copying",
    val updating: Boolean = false,
    val notice: String? = null
)

class AddSongViewModel(
    application: Application,
    val artistId: Long
) : AndroidViewModel(application) {

    private val songRepository = SongRepository(MusixDatabase.get(application).songDao())

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state

    private var cancelRequested = false

    fun onFilePicked(uri: Uri) {
        _state.update { it.copy(reading = true, error = null) }
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { readFileInfo(uri) }
            if (info == null) {
                _state.update {
                    it.copy(
                        picked = null,
                        reading = false,
                        error = "This file could not be read as audio or video."
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        picked = info,
                        title = info.displayName.substringBeforeLast('.'),
                        wantsVideo = info.fileHasVideo,
                        reading = false,
                        error = null
                    )
                }
            }
        }
    }

    fun onTitleChange(text: String) = _state.update { it.copy(title = text, error = null) }
    fun onLiveChange(live: Boolean) = _state.update { it.copy(isLive = live, error = null) }
    fun onVideoChange(video: Boolean) = _state.update { it.copy(wantsVideo = video, error = null) }

    fun onModeChange(linkMode: Boolean) {
        if (_state.value.saving) return
        _state.update { s ->
            s.copy(
                linkMode = linkMode,
                error = null,
                title = if (linkMode) (s.linkInfo?.title ?: "")
                else (s.picked?.displayName?.substringBeforeLast('.') ?: ""),
                wantsVideo = if (linkMode) false else (s.picked?.fileHasVideo ?: false)
            )
        }
    }

    fun onLinkChange(text: String) =
        _state.update { it.copy(link = cleanLink(text), linkInfo = null, error = null) }

    fun onLinkPasted(text: String) = onLinkChange(text.trim())

    fun fetchLinkInfo() {
        val link = cleanLink(_state.value.link)
        if (link.isEmpty() || _state.value.fetching) return

        when (val engine = DownloaderEngine.state.value) {
            EngineState.Starting -> {
                _state.update {
                    it.copy(error = "The downloader is still starting. Try again in a few seconds.")
                }
                return
            }
            is EngineState.Failed -> {
                _state.update {
                    it.copy(error = "The downloader could not start: ${engine.message}")
                }
                return
            }
            EngineState.Ready -> Unit
        }

        _state.update { it.copy(fetching = true, error = null, linkInfo = null, notice = null) }
        viewModelScope.launch {
            try {
                val info = DownloaderEngine.fetchInfo(link)
                _state.update { it.copy(fetching = false, linkInfo = info, title = info.title) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(fetching = false, error = "Could not read this link. ${friendlyMessage(e)}")
                }
            }
        }
    }

    fun cancelDownload() {
        cancelRequested = true
        DownloaderEngine.cancel()
    }

    override fun onCleared() {
        if (_state.value.downloading) {
            cancelRequested = true
            DownloaderEngine.cancel()
            DownloadService.stop(getApplication<Application>())
        }
    }

    fun downloadAndSave(onDone: () -> Unit) {
        val current = _state.value
        if (current.linkInfo == null) return
        val title = current.title.trim()
        if (title.isEmpty() || current.saving) return

        cancelRequested = false
        _state.update {
            it.copy(
                saving = true,
                downloading = true,
                progress = null,
                statusText = "Downloading",
                error = null
            )
        }

        viewModelScope.launch {
            val duplicate = songRepository.exists(
                artistId, title, current.isLive, current.wantsVideo
            )
            if (duplicate) {
                _state.update {
                    it.copy(
                        saving = false,
                        downloading = false,
                        statusText = "Copying",
                        error = "This version is already in this artist's library."
                    )
                }
                return@launch
            }

            val app = getApplication<Application>()
            val folder = SongFiles.artistFolder(app, artistId)
            var target: File? = null
            var success = false
            runCatching { DownloadService.start(app) }
            try {
                val downloaded = DownloaderEngine.download(
                    app,
                    cleanLink(current.link),
                    audioOnly = !current.wantsVideo
                ) { percent ->
                    if (percent >= 0f) {
                        _state.update { it.copy(progress = (percent / 100f).coerceIn(0f, 1f)) }
                    }
                }

                _state.update { it.copy(downloading = false, progress = null, statusText = "Saving") }

                val picked = withContext(Dispatchers.IO) {
                    readFileInfo(Uri.fromFile(downloaded))
                        ?.copy(displayName = downloaded.name, sizeBytes = downloaded.length())
                } ?: throw IOException("The downloaded file could not be read")

                val fileName = "${UUID.randomUUID()}.${extensionOf(picked.displayName)}"
                val destination = File(folder, fileName)
                target = destination

                val savedBytes = withContext(Dispatchers.IO) {
                    destination.parentFile?.mkdirs()
                    if (downloaded.renameTo(destination)) destination.length()
                    else copyIntoApp(picked, destination)
                }

                songRepository.addSong(
                    Song(
                        artistId = artistId,
                        title = title,
                        isLive = current.isLive,
                        hasVideo = picked.fileHasVideo,
                        durationMs = picked.durationMs,
                        fileName = fileName,
                        sizeBytes = savedBytes
                    )
                )
                success = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        downloading = false,
                        progress = null,
                        statusText = "Copying",
                        error = if (cancelRequested) null
                        else "Download failed. ${friendlyMessage(e)}"
                    )
                }
            } finally {
                DownloadService.stop(app)
                if (!success) target?.delete()
                DownloaderEngine.tempDir(app).listFiles()?.forEach { it.deleteRecursively() }
            }

            if (success) onDone()
        }
    }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        val picked = current.picked ?: return
        val title = current.title.trim()
        if (title.isEmpty() || current.saving) return

        _state.update {
            it.copy(
                saving = true,
                statusText = "Copying",
                progress = if (picked.sizeBytes > 0) 0f else null,
                error = null
            )
        }

        viewModelScope.launch {
            val duplicate = songRepository.exists(
                artistId, title, current.isLive, current.wantsVideo
            )
            if (duplicate) {
                _state.update {
                    it.copy(
                        saving = false,
                        error = "This version is already in this artist's library."
                    )
                }
                return@launch
            }

            val folder = SongFiles.artistFolder(getApplication<Application>(), artistId)
            val fileName = "${UUID.randomUUID()}.${extensionOf(picked.displayName)}"
            val target = File(folder, fileName)

            var success = false
            try {
                val copiedBytes = withContext(Dispatchers.IO) { copyIntoApp(picked, target) }
                songRepository.addSong(
                    Song(
                        artistId = artistId,
                        title = title,
                        isLive = current.isLive,
                        hasVideo = current.wantsVideo,
                        durationMs = picked.durationMs,
                        fileName = fileName,
                        sizeBytes = copiedBytes
                    )
                )
                success = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        error = "Could not save the song. Check free space and try again."
                    )
                }
            } finally {
                if (!success) target.delete()
            }

            if (success) onDone()
        }
    }

    private suspend fun copyIntoApp(picked: PickedFile, target: File): Long {
        val resolver = getApplication<Application>().contentResolver
        target.parentFile?.mkdirs()

        var total = 0L
        var lastPercent = -1
        resolver.openInputStream(picked.uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                    if (picked.sizeBytes > 0) {
                        val percent = ((total * 100) / picked.sizeBytes).toInt().coerceAtMost(100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.update { it.copy(progress = percent / 100f) }
                        }
                    }
                }
            }
        } ?: throw IOException("Could not open the selected file")
        return total
    }
    private fun cleanLink(raw: String): String =
        raw.filterNot {
            it.isISOControl() ||
                    it in '\u200B'..'\u200F' ||
                    it in '\u202A'..'\u202E' ||
                    it == '\uFEFF'
        }.trim()
    fun updateDownloader() {
        if (_state.value.updating || _state.value.saving) return
        _state.update { it.copy(updating = true, notice = null, error = null) }
        viewModelScope.launch {
            try {
                val message = DownloaderEngine.updateDownloader(getApplication<Application>())
                _state.update { it.copy(updating = false, notice = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(updating = false, error = "Update failed. ${friendlyMessage(e)}")
                }
            }
        }
    }

    private fun friendlyMessage(e: Exception): String {
        val full = e.message.orEmpty()
        return when {
            full.contains("Timed out", ignoreCase = true) -> full.lines().first()
            full.contains("Private video", ignoreCase = true) ||
                    full.contains("Video unavailable", ignoreCase = true) ->
                "This video is private or unavailable."
            full.contains("not a bot", ignoreCase = true) ||
                    full.contains("Sign in to confirm", ignoreCase = true) ->
                "YouTube is asking to confirm you are not a bot. Wait a while or try another link."
            full.contains("name resolution", ignoreCase = true) ||
                    full.contains("Network is unreachable", ignoreCase = true) ||
                    full.contains("Unable to connect", ignoreCase = true) ->
                "No internet connection."
            full.contains("Requested format is not available", ignoreCase = true) ->
                "No suitable format for this link. Try Audio only."
            full.contains("HTTP Error 403", ignoreCase = true) ||
                    full.contains("JavaScript runtime", ignoreCase = true) ->
                "YouTube changed something. Tap Update downloader and try again."
            else -> shortMessage(e)
        }
    }

    private fun shortMessage(e: Exception): String =
        e.message?.lines()?.lastOrNull { it.isNotBlank() }?.take(200) ?: "unknown error"

    private fun extensionOf(displayName: String): String {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it.isLetterOrDigit() }) ext else "bin"
    }

    private fun readFileInfo(uri: Uri): PickedFile? {
        val context = getApplication<Application>()

        var name = "Untitled"
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val hasVideo = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (duration == null) null else PickedFile(uri, name, size, duration, hasVideo)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        fun factory(application: Application, artistId: Long) = viewModelFactory {
            initializer { AddSongViewModel(application, artistId) }
        }
    }
}