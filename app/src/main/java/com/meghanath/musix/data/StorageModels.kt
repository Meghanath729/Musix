package com.meghanath.musix.data

data class ArtistStorage(
    val artistId: Long,
    val artistName: String,
    val songCount: Int,
    val totalBytes: Long
)