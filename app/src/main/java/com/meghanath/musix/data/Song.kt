package com.meghanath.musix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("artistId")]
)
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistId: Long,
    val title: String,
    val isLive: Boolean,
    val hasVideo: Boolean,
    val durationMs: Long,
    val fileName: String,
    val sizeBytes: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)