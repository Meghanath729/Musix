package com.meghanath.musix.backup

import android.content.Context
import androidx.room.withTransaction
import com.meghanath.musix.data.MusixDatabase
import com.meghanath.musix.data.SongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

enum class RestoreStage { CHECKING, COPYING, FINISHING }

data class RestoreProgress(
    val stage: RestoreStage,
    val doneBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class RestoreResult(
    val artists: Int,
    val songs: Int,
    val playlists: Int
)

/** An error whose message is safe to show to the user as it is. */
class RestoreException(message: String) : IOException(message)

object BackupRestorer {

    private const val MAX_MANIFEST_BYTES = 20 * 1024 * 1024
    private const val SPACE_MARGIN = 100L * 1024 * 1024

    private class Inspection(
        val snapshot: LibrarySnapshot,
        val entrySizes: Map<String, Long>
    )

    /**
     * Restores a backup, replacing the whole library.
     * [openBackup] must return a NEW stream every time it is called (the file is read twice).
     * The caller must stop playback BEFORE calling this.
     */
    suspend fun restore(
        context: Context,
        db: MusixDatabase,
        openBackup: () -> InputStream?,
        onProgress: (RestoreProgress) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {

        val checkActive: () -> Unit = { ensureActive() }

        val filesDir = context.filesDir
        val staging = File(filesDir, "restore_staging")
        val songsDir = File(filesDir, "songs")
        val oldDir = File(filesDir, "songs_old")

        // Leftovers from an earlier restore that was interrupted.
        staging.deleteRecursively()
        oldDir.deleteRecursively()

        try {
            // Stage 1: check the backup.
            onProgress(RestoreProgress(RestoreStage.CHECKING))
            val inspection = inspect(openBackup, checkActive)
            val snapshot = inspection.snapshot
            checkLibrary(snapshot)

            val expected = expectedFiles(snapshot, staging)
            if (expected.keys.any { it !in inspection.entrySizes }) {
                throw RestoreException("The backup is missing some song files.")
            }
            val totalBytes = expected.keys.sumOf { inspection.entrySizes.getValue(it) }

            // Stage 2: is there room for the new songs next to the old ones?
            if (filesDir.usableSpace < totalBytes + SPACE_MARGIN) {
                throw RestoreException("Not enough free space on the phone to restore this backup.")
            }

            // Stage 3: copy song files into a temporary folder.
            extract(openBackup, expected, totalBytes, checkActive, onProgress)
            checkActive()

            // Stage 4: swap. Not cancellable, so it is never left half done.
            onProgress(RestoreProgress(RestoreStage.FINISHING, totalBytes, totalBytes))
            withContext(NonCancellable) {
                swap(context, db, snapshot, staging, songsDir, oldDir)
            }

            RestoreResult(
                artists = snapshot.artists.size,
                songs = snapshot.songs.size,
                playlists = snapshot.playlists.size
            )
        } catch (e: Throwable) {
            withContext(NonCancellable) { staging.deleteRecursively() }
            throw e
        }
    }

    /** Reads the zip once: confirms the manifest, and records the size of every file inside. */
    private fun inspect(openBackup: () -> InputStream?, checkActive: () -> Unit): Inspection {
        val stream = openBackup() ?: throw RestoreException("Could not open the backup file.")
        ZipInputStream(stream.buffered()).use { zin ->
            val first = zin.nextEntry
            if (first == null || first.name != "manifest.json") {
                throw RestoreException("This is not a Musix backup.")
            }
            val text = readLimited(zin, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
            val snapshot = try {
                BackupManifest.fromJson(text)
            } catch (e: Exception) {
                throw RestoreException("The backup's song list is damaged or from a newer version.")
            }

            val sizes = HashMap<String, Long>()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val entry = zin.nextEntry ?: break
                var count = 0L
                while (true) {
                    checkActive()
                    val n = zin.read(buffer)
                    if (n < 0) break
                    count += n
                }
                if (!entry.isDirectory) sizes[entry.name] = count
            }
            return Inspection(snapshot, sizes)
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw RestoreException("This is not a Musix backup.")
        }
        return out.toByteArray()
    }

    /** Makes sure the backup's lists agree with each other before anything is changed. */
    private fun checkLibrary(s: LibrarySnapshot) {
        val artistIds = s.artists.map { it.id }.toSet()
        val songIds = s.songs.map { it.id }.toSet()
        val playlistIds = s.playlists.map { it.id }.toSet()
        val linkKeys = s.links.map { it.playlistId to it.songId }.toSet()

        val ok = artistIds.size == s.artists.size &&
                songIds.size == s.songs.size &&
                playlistIds.size == s.playlists.size &&
                linkKeys.size == s.links.size &&
                s.songs.all { it.artistId in artistIds && isSafeName(it.fileName) } &&
                s.links.all { it.playlistId in playlistIds && it.songId in songIds }

        if (!ok) throw RestoreException("The backup is inconsistent and cannot be restored.")
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name

    /** Zip entry name -> where that file goes. Paths come from the checked list, never from the zip. */
    private fun expectedFiles(s: LibrarySnapshot, root: File): Map<String, File> =
        s.songs.associate { song ->
            "songs/${song.artistId}/${song.fileName}" to File(root, "${song.artistId}/${song.fileName}")
        }

    private fun extract(
        openBackup: () -> InputStream?,
        expected: Map<String, File>,
        totalBytes: Long,
        checkActive: () -> Unit,
        onProgress: (RestoreProgress) -> Unit
    ) {
        val stream = openBackup() ?: throw RestoreException("Could not open the backup file.")
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        var lastReported = 0L

        ZipInputStream(stream.buffered()).use { zin ->
            while (true) {
                checkActive()
                val entry = zin.nextEntry ?: break
                val target = expected[entry.name] ?: continue
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        checkActive()
                        val n = zin.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - lastReported >= 256 * 1024) {
                            onProgress(RestoreProgress(RestoreStage.COPYING, done, totalBytes))
                            lastReported = done
                        }
                    }
                }
            }
        }
        onProgress(RestoreProgress(RestoreStage.COPYING, done, totalBytes))
    }

    private suspend fun swap(
        context: Context,
        db: MusixDatabase,
        snapshot: LibrarySnapshot,
        staging: File,
        songsDir: File,
        oldDir: File
    ) {
        val hadOld = songsDir.exists()
        if (hadOld && !songsDir.renameTo(oldDir)) {
            throw RestoreException("Could not move the current songs aside.")
        }
        staging.mkdirs() // a backup with no songs has no staging folder yet
        if (!staging.renameTo(songsDir)) {
            if (hadOld) oldDir.renameTo(songsDir)
            throw RestoreException("Could not put the restored songs in place.")
        }

        try {
            // Confirms the app finds every restored file where it looks for songs.
            if (snapshot.songs.any { !SongFiles.fileFor(context, it).exists() }) {
                throw RestoreException("Restored song files were not found where the app expects them.")
            }

            db.withTransaction {
                val artistDao = db.artistDao()
                val songDao = db.songDao()
                val playlistDao = db.playlistDao()

                playlistDao.deleteAllLinks()
                playlistDao.deleteAllPlaylists()
                songDao.deleteAll()
                artistDao.deleteAll()

                artistDao.insertAll(snapshot.artists)
                songDao.insertAll(snapshot.songs)
                playlistDao.insertAllPlaylists(snapshot.playlists)
                playlistDao.insertAllLinks(snapshot.links)
            }
        } catch (e: Throwable) {
            songsDir.deleteRecursively()
            if (hadOld) oldDir.renameTo(songsDir)
            throw e
        }

        oldDir.deleteRecursively()
    }
}