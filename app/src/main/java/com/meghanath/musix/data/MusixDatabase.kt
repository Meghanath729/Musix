package com.meghanath.musix.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                artistId INTEGER NOT NULL,
                title TEXT NOT NULL,
                isLive INTEGER NOT NULL,
                hasVideo INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                FOREIGN KEY(artistId) REFERENCES artists(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_artistId ON songs(artistId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId INTEGER NOT NULL,
                songId INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(playlistId, songId),
                FOREIGN KEY(playlistId) REFERENCES playlists(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(songId) REFERENCES songs(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_songs ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        // Give every existing entry a position that matches its current order
        // (oldest first, ties broken by song id), so playlists look unchanged.
        db.execSQL(
            """
            UPDATE playlist_songs SET position = (
                SELECT COUNT(*) FROM playlist_songs AS p2
                WHERE p2.playlistId = playlist_songs.playlistId
                AND (p2.addedAt < playlist_songs.addedAt
                     OR (p2.addedAt = playlist_songs.addedAt AND p2.songId < playlist_songs.songId))
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [Artist::class, Song::class, Playlist::class, PlaylistSong::class],
    version = 4,
    exportSchema = false
)
abstract class MusixDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var instance: MusixDatabase? = null

        fun get(context: Context): MusixDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusixDatabase::class.java,
                    "musix.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}