package com.meghanath.musix.data

import android.content.Context
import java.io.File

object SongFiles {

    fun artistFolder(context: Context, artistId: Long): File =
        File(context.filesDir, "songs/$artistId")

    fun fileFor(context: Context, song: Song): File =
        File(artistFolder(context, song.artistId), song.fileName)

    fun deleteSongFile(context: Context, song: Song) {
        fileFor(context, song).delete()
    }

    fun deleteArtistFolder(context: Context, artistId: Long) {
        artistFolder(context, artistId).deleteRecursively()
    }
}