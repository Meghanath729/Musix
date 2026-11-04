package com.meghanath.musix.playback

import android.content.Context
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongFiles

/** Turns a saved Song into the item the player understands. */
fun Song.toQueueItem(context: Context, artistName: String): QueueItem {
    val kind = if (isLive) "Live" else "Original"
    val media = if (hasVideo) "Video" else "Audio"
    return QueueItem(
        songId = id,
        title = title,
        artistName = artistName,
        filePath = SongFiles.fileFor(context, this).absolutePath,
        versionLabel = "$kind - $media",
        hasVideo = hasVideo
    )
}