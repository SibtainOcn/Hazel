package com.hazel.android.download

import android.util.Log
import com.hazel.android.HazelApp
import com.hazel.android.update.YtDlpUpdater
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * The one door every yt-dlp run goes through.
 *
 * yt-dlp ships as a zip that Python reads lazily: it reopens the file by path whenever a run
 * imports an extractor or post-processor it has not needed yet. Replacing that file while a
 * run is in flight breaks the run midway, usually at the post-processing step near the end,
 * with "bad local file header". Runs therefore share a read lock, and the binary is only
 * replaced under the write lock, which is taken only when nothing is running.
 */
object YtDlpEngine {

    private val lock = ReentrantReadWriteLock()

    /** Whether any yt-dlp run is in flight right now. */
    val isBusy: Boolean get() = lock.readLockCount > 0

    /**
     * Runs [request] through yt-dlp.
     *
     * A binary left damaged by an older build's interrupted update is restored from the copy
     * bundled with the app and the run is tried once more, rather than failing every link
     * until the user thinks to reinstall.
     */
    fun execute(
        request: YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse = try {
        shared { YoutubeDL.getInstance().execute(request, processId, callback) }
    } catch (e: YoutubeDL.CanceledException) {
        throw e
    } catch (e: Exception) {
        if (!isDamagedBinary(e)) throw e
        Log.w("Hazel", "yt-dlp binary is damaged, restoring the bundled copy: ${e.message}")
        YtDlpUpdater.ensureValidBinary(HazelApp.instance)
        shared { YoutubeDL.getInstance().execute(request, processId, callback) }
    }

    /**
     * Runs [block] only if no yt-dlp run is in flight, holding every new run back until it
     * returns. Returns null without waiting when the engine is busy.
     */
    fun <T> whenIdle(block: () -> T): T? {
        if (!lock.writeLock().tryLock()) return null
        return try {
            block()
        } finally {
            lock.writeLock().unlock()
        }
    }

    private fun <T> shared(block: () -> T): T {
        lock.readLock().lock()
        try {
            return block()
        } finally {
            lock.readLock().unlock()
            // A binary fetched while a download was running waits for the first moment the
            // engine is free, which can be between two items of a batch.
            if (lock.readLockCount == 0) {
                runCatching { YtDlpUpdater.applyStagedUpdate(HazelApp.instance) }
            }
        }
    }

    private fun isDamagedBinary(e: Exception): Boolean {
        val text = e.message?.lowercase() ?: return false
        return DAMAGED_BINARY_SIGNS.any { it in text }
    }

    private val DAMAGED_BINARY_SIGNS = listOf("bad local file header", "zipimporterror", "bad zipfile")
}
