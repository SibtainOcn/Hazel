package com.hazel.android.util

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/** How much room a volume has, read fresh each time it is asked for. Call off the main thread. */
data class StorageSpace(val freeBytes: Long, val totalBytes: Long) {
    val usedFraction: Float
        get() = (1f - freeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    companion object {
        /**
         * The phone's own storage, which also holds the shared Download and Music folders.
         * From Android 8 this uses the same numbers as the system Settings screen; before
         * that, or if those are unavailable, it measures the data partition directly.
         */
        fun internal(context: Context): StorageSpace? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val stats = context.getSystemService(StorageStatsManager::class.java)
                    val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (total > 0L) return StorageSpace(free.coerceIn(0L, total), total)
                }
            }
            return of(Environment.getDataDirectory())
        }

        /** The volume [dir] lives on, or null when it is gone or cannot be measured. */
        fun of(dir: File?): StorageSpace? {
            if (dir == null) return null
            val stat = runCatching { StatFs(dir.path) }.getOrNull() ?: return null
            val total = stat.totalBytes
            if (total <= 0L) return null
            return StorageSpace(stat.availableBytes.coerceIn(0L, total), total)
        }
    }
}
