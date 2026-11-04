package com.meghanath.musix.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** The player itself, needed by the video surface on the Player screen (step 4d). */
    val player: Player? get() = controller

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshState(player)
        }
    }

    init {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected != null) {
                controller = connected
                connected.addListener(playerListener)
                refreshState(connected)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refreshState(player: Player) {
        val item = player.currentMediaItem
        val extras = item?.mediaMetadata?.extras
        _state.value = PlayerState(
            isConnected = true,
            currentSongId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artistName = item?.mediaMetadata?.artist?.toString().orEmpty(),
            versionLabel = extras?.getString(KEY_VERSION).orEmpty(),
            hasVideo = extras?.getBoolean(KEY_HAS_VIDEO) ?: false,
            isPlaying = player.isPlaying,
            durationMs = player.duration.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    // ---- Commands the screens can call ----

    /** Replace the queue with these songs and start playing from startIndex. */
    fun playQueue(items: List<QueueItem>, startIndex: Int) {
        val c = controller ?: return
        if (items.isEmpty()) return
        c.setMediaItems(items.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** Jump forward (positive) or backward (negative) by this many milliseconds. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        var target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = c.duration
        if (duration != C.TIME_UNSET && duration > 0L) target = target.coerceAtMost(duration)
        c.seekTo(target)
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    /** Stops playback and empties the queue (used before a restore replaces the song files). */
    fun stopAndClear() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** The seek bar asks for this several times a second (step 4d). */
    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    private fun QueueItem.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(KEY_VERSION, versionLabel)
            putBoolean(KEY_HAS_VIDEO, hasVideo)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistName)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(songId.toString())
            .setUri(Uri.fromFile(File(filePath)))
            .setMediaMetadata(metadata)
            .build()
    }

    private companion object {
        const val KEY_VERSION = "version"
        const val KEY_HAS_VIDEO = "has_video"
    }
}