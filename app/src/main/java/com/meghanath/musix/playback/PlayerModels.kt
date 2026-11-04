package com.meghanath.musix.playback

/** One song to put in the playback queue. Built from a Song in step 4g. */
data class QueueItem(
    val songId: Long,
    val title: String,
    val artistName: String,
    val filePath: String,
    val versionLabel: String,
    val hasVideo: Boolean
)

/** Everything a screen needs to know about the player right now. */
data class PlayerState(
    val isConnected: Boolean = false,
    val currentSongId: Long? = null,
    val title: String = "",
    val artistName: String = "",
    val versionLabel: String = "",
    val hasVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0
)