package com.meghanath.musix.data

import android.content.Context
import android.os.StatFs

data class DeviceSpace(val freeBytes: Long, val totalBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
}

object DeviceStorage {
    const val LOW_SPACE_BYTES = 1L * 1024 * 1024 * 1024        // warn below 1 GB free
    const val MIN_FREE_AFTER_SAVE = 200L * 1024 * 1024         // keep at least 200 MB free

    // True if the phone is running low on space.
    fun isLow(context: Context): Boolean = read(context).freeBytes < LOW_SPACE_BYTES

    // True if a file of this size can be saved and still leave a safe amount free.
    fun canFit(context: Context, fileBytes: Long): Boolean =
        read(context).freeBytes - fileBytes >= MIN_FREE_AFTER_SAVE

    // Free space where Musix keeps its files (the phone's internal storage).
    fun read(context: Context): DeviceSpace {
        val stat = StatFs(context.filesDir.path)
        return DeviceSpace(
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes
        )
    }

    // Turns bytes into text like "1.4 GB" or "320 MB".
    fun format(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.0f MB", bytes / mb)
            bytes >= kb -> String.format("%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}