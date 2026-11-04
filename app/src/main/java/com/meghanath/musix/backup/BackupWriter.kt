package com.meghanath.musix.backup

import android.content.Context
import com.meghanath.musix.data.ArtistDao
import com.meghanath.musix.data.PlaylistDao
import com.meghanath.musix.data.SongDao
import com.meghanath.musix.data.SongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupProgress(
    val doneBytes: Long,
    val totalBytes: Long
)

data class BackupResult(
    val songsWritten: Int,
    val songsSkipped: Int,
    val bytesWritten: Long
)

object BackupWriter {

    /** Reads the whole library from the database in one go. */
    suspend fun loadSnapshot(
        artistDao: ArtistDao,
        songDao: SongDao,
        playlistDao: PlaylistDao
    ): LibrarySnapshot = LibrarySnapshot(
        artists = artistDao.getAllOnce(),
        songs = songDao.getAllOnce(),
        playlists = playlistDao.getAllPlaylistsOnce(),
        links = playlistDao.getAllLinksOnce()
    )

    /**
     * Writes the backup zip into [out]. The caller owns [out] and must close it.
     * Cancelling the coroutine stops the copy within a fraction of a second.
     */
    suspend fun write(
        context: Context,
        snapshot: LibrarySnapshot,
        out: OutputStream,
        onProgress: (BackupProgress) -> Unit
    ): BackupResult = withContext(Dispatchers.IO) {

        // Only back up songs whose file really exists, so the backup never lists a song it cannot restore.
        val songs = snapshot.songs.filter { SongFiles.fileFor(context, it).exists() }
        val songIds = songs.map { it.id }.toSet()
        val trimmed = snapshot.copy(
            songs = songs,
            links = snapshot.links.filter { it.songId in songIds }
        )
        val totalBytes = songs.sumOf { SongFiles.fileFor(context, it).length() }

        val zip = ZipOutputStream(out.buffered())

        // 1. The manifest goes first (small text, so normal compression is fine).
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(BackupManifest.toJson(trimmed).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 2. Song files. Audio and video are already compressed, so they are STORED as they are.
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        var lastReported = 0L

        for (song in songs) {
            ensureActive()
            val file = SongFiles.fileFor(context, song)
            val fileSize = file.length()

            // A stored entry needs its size and checksum before the data, so read the file once first.
            val crc = CRC32()
            file.inputStream().use { input ->
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    crc.update(buffer, 0, count)
                }
            }

            val entry = ZipEntry("songs/${song.artistId}/${song.fileName}")
            entry.method = ZipEntry.STORED
            entry.size = fileSize
            entry.compressedSize = fileSize
            entry.crc = crc.value
            zip.putNextEntry(entry)

            file.inputStream().use { input ->
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    zip.write(buffer, 0, count)
                    done += count
                    if (done - lastReported >= 256 * 1024) {
                        onProgress(BackupProgress(done, totalBytes))
                        lastReported = done
                    }
                }
            }
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
        onProgress(BackupProgress(done, totalBytes))

        BackupResult(
            songsWritten = songs.size,
            songsSkipped = snapshot.songs.size - songs.size,
            bytesWritten = done
        )
    }
}