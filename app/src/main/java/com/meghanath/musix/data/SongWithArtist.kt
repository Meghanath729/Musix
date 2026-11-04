package com.meghanath.musix.data

import androidx.room.Embedded

data class SongWithArtist(
    @Embedded val song: Song,
    val artistName: String
)

data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val songCount: Int
)