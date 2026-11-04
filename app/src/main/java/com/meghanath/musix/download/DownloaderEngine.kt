package com.meghanath.musix.download

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

sealed interface EngineState {
    data object Starting : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

data class LinkInfo(
    val title: String,
    val uploader: String?,
    val durationSec: Int,
)

object DownloaderEngine {

    private const val PROCESS_ID = "musix-download"

    private val _state = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null
    private val initMutex = Mutex()

    /** Cheap: just remembers the application. Nothing heavy runs here. */
    fun attach(app: Application) {
        appContext = app
    }

    /**
     * Optional early warm-up in the background. Safe to call many times.
     * Not required: fetchInfo, download and updateDownloader start the engine themselves.
     */
    fun start(app: Application) {
        attach(app)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ensureReady(app)
            } catch (t: Throwable) {
                // ensureReady already logged this and set the Failed state
            }
        }
    }

    /** Starts the engine if it is not ready yet. Later calls return instantly. */
    private suspend fun ensureReady(context: Context? = null) {
        if (_state.value == EngineState.Ready) return
        val ctx = (context ?: appContext)?.applicationContext
            ?: throw IllegalStateException("DownloaderEngine was not attached to the app")
        initMutex.withLock {
            if (_state.value == EngineState.Ready) return
            _state.value = EngineState.Starting
            try {
                // NonCancellable: if the screen is closed mid-start, the start still finishes cleanly
                withContext(Dispatchers.IO + NonCancellable) {
                    YoutubeDL.getInstance().init(ctx)
                    FFmpeg.getInstance().init(ctx)
                }
                _state.value = EngineState.Ready
                Log.i("Musix", "Downloader ready")
            } catch (t: Throwable) {
                Log.e("Musix", "Downloader init failed", t)
                _state.value = EngineState.Failed(t.message ?: "Unknown error")
                throw t
            }
        }
    }

    fun tempDir(context: Context): File =
        File(context.filesDir, "tmp_downloads").apply { mkdirs() }

    suspend fun fetchInfo(url: String): LinkInfo {
        // Start-up happens before the timeout clock begins
        ensureReady()
        val job = CoroutineScope(Dispatchers.IO).async {
            YoutubeDL.getInstance().getInfo(url)
        }
        val info = withTimeoutOrNull(240_000L) { job.await() }
            ?: throw IOException("Timed out reading the link. Check the internet connection and try again.")
        return LinkInfo(
            title = info.title ?: "Untitled",
            uploader = info.uploader,
            durationSec = info.duration ?: 0,
        )
    }

    suspend fun download(
        context: Context,
        url: String,
        audioOnly: Boolean,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        ensureReady(context)

        val dir = tempDir(context)
        // clear leftovers from any earlier attempt
        dir.listFiles()?.forEach { it.deleteRecursively() }

        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--playlist-items", "1")
        request.addOption("--no-mtime")
        request.addOption("-o", dir.absolutePath + "/%(id)s.%(ext)s")

        if (audioOnly) {
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            request.addOption("-x")
            request.addOption("--audio-format", "m4a")
        } else {
            request.addOption(
                "-f",
                "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]"
            )
            request.addOption("--merge-output-format", "mp4")
        }

        YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, _, _ ->
            onProgress(progress)
        }

        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("Download finished but no file was found")
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }

    suspend fun updateDownloader(context: Context): String = withContext(Dispatchers.IO) {
        ensureReady(context)
        val status = YoutubeDL.getInstance().updateYoutubeDL(context)
        when (status?.toString()) {
            "DONE" -> "Downloader updated."
            "ALREADY_UP_TO_DATE" -> "Downloader is already up to date."
            else -> "The update did not complete. Try again later."
        }
    }
}