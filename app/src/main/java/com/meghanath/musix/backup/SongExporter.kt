package com.meghanath.musix.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** An error whose message is safe to show to the user as it is. */
class ExportException(message: String) : IOException(message)

object SongExporter {

    private val badFileNameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val extraSpaces = Regex("\\s+")

    /** The name offered in the "Save as" screen, e.g. "Hotel California (Live).m4a". */
    fun suggestedName(song: Song): String {
        val extension = song.fileName.substringAfterLast('.', "")
        val title = song.title
            .replace(badFileNameChars, " ")
            .replace(extraSpaces, " ")
            .trim()
            .take(100)
            .ifBlank { "song" }
        val version = if (song.isLive) " (Live)" else ""
        return if (extension.isEmpty()) "$title$version" else "$title$version.$extension"
    }

    /**
     * Copies the song file into [target], the file the user picked.
     * If it fails or is cancelled, the half-written file is deleted again.
     * [onProgress] receives (bytes copied, total bytes).
     */
    suspend fun export(
        context: Context,
        song: Song,
        target: Uri,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val source = SongFiles.fileFor(context, song)
        if (!source.exists()) throw ExportException("The song file is missing.")

        val total = source.length()
        val resolver = context.contentResolver

        try {
            val out = resolver.openOutputStream(target, "wt")
                ?: throw ExportException("Could not open the chosen location.")
            out.use { output ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        if (done - lastReported >= 256 * 1024) {
                            onProgress(done, total)
                            lastReported = done
                        }
                    }
                    output.flush()
                }
            }
            onProgress(total, total)
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                try {
                    DocumentsContract.deleteDocument(resolver, target)
                } catch (_: Exception) {
                    // Nothing more we can do; the user can delete the file by hand.
                }
            }
            throw e
        }
    }
}