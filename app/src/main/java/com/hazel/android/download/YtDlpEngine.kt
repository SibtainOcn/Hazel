package com.hazel.android.download

import android.util.Log
import com.hazel.android.HazelApp
import com.hazel.android.update.YtDlpUpdater
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import android.system.Os
import android.system.OsConstants
import java.io.File
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
     * until the user thinks to reinstall. So is one too old to accept the options the engine
     * library always passes, which would otherwise refuse every run until an update landed.
     */
    fun execute(
        request: YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse = try {
        executeOnce(request, processId, callback)
    } catch (e: YoutubeDLException) {
        // A proxy that is down, or one ffmpeg cannot use, is said in the user's words as the
        // last error line, which is the one a failed download's card shows.
        val hint = NetworkProxy.hintFor(e.message) ?: throw e
        throw YoutubeDLException("${e.message}\nERROR: ${HazelApp.instance.getString(hint)}", e)
    }

    private fun executeOnce(
        request: YoutubeDLRequest,
        processId: String?,
        callback: ((Float, Long, String) -> Unit)?
    ): YoutubeDLResponse = try {
        shared { YoutubeDL.getInstance().execute(request, processId, callback) }
    } catch (e: YoutubeDL.CanceledException) {
        throw e
    } catch (e: Exception) {
        when {
            isDamagedBinary(e) -> {
                Log.w("Hazel", "yt-dlp binary is damaged, restoring the bundled copy: ${e.message}")
                YtDlpUpdater.ensureValidBinary(HazelApp.instance, confirmedBroken = true)
            }
            isOutdatedBinary(e) -> {
                Log.w("Hazel", "yt-dlp binary is too old for this app, restoring the bundled copy")
                if (!YtDlpUpdater.restoreBundledBinary(HazelApp.instance)) throw e
            }
            else -> throw e
        }
        shared { YoutubeDL.getInstance().execute(request, processId, callback) }
    }

    /**
     * Stops the run started under [processId], and the ffmpeg it started.
     *
     * The library stops a run by ending the Python process and asks `pstree` for its
     * children by the run's *name* rather than its pid, so the children are never found. A
     * cut is downloaded by ffmpeg, a child of Python that shares its output pipes: it kept
     * running after Python was gone, the pipes never closed, and the run that was stopped
     * never returned. The card stayed on "downloading", the next link never started, and
     * every stop left one more ffmpeg burning the processor and the network.
     */
    fun stop(processId: String): Boolean {
        val stopped = try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
            false
        }
        killStrayFfmpeg()
        return stopped
    }

    /**
     * Ends every ffmpeg this app has running. The engine is the only thing here that starts
     * one, and it runs one download at a time, so any that is alive when nothing is
     * downloading, or just after a stop, belongs to a run that is over.
     */
    fun killStrayFfmpeg() {
        try {
            val self = android.os.Process.myPid()
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == self) return@forEach
                val program = try {
                    File(dir, "cmdline").readText().substringBefore('\u0000')
                } catch (_: Exception) {
                    return@forEach
                }
                if (program.endsWith("libffmpeg.so")) {
                    try {
                        Os.kill(pid, OsConstants.SIGKILL)
                    } catch (_: Exception) { /* already gone */ }
                }
            }
        } catch (_: Exception) { /* best effort: the stop itself has already been done */ }
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
        Log.d("Hazel", "yt-dlp run started (${lock.readLockCount} running)")
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

    /** yt-dlp's refusal of an option it predates, such as the JS runtime the library adds. */
    private fun isOutdatedBinary(e: Exception): Boolean =
        e.message?.contains("no such option", ignoreCase = true) == true

    private val DAMAGED_BINARY_SIGNS = listOf("bad local file header", "zipimporterror", "bad zipfile")
}
