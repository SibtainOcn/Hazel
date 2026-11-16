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
        trimStopped = true
        trimProcess?.destroy()
        val stopped = try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
            false
        }
        killStrayFfmpeg()
        return stopped
    }

    @Volatile private var trimProcess: Process? = null
    @Volatile private var trimStopped = false

    class TrimOutcome(val ok: Boolean, val stopped: Boolean, val message: String)

    fun trim(
        context: android.content.Context,
        input: File,
        output: File,
        startSeconds: Double,
        endSeconds: Double,
        precise: Boolean
    ): TrimOutcome {
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpeg.exists()) return TrimOutcome(false, false, "ffmpeg is not installed")
        val libs = ffmpegLibraryPath(context)

        val start = "%.3f".format(java.util.Locale.ROOT, startSeconds)
        val length = "%.3f".format(java.util.Locale.ROOT, endSeconds - startSeconds)
        val quiet = listOf(ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "error")
        val streams = listOf("-map", "0:V?", "-map", "0:a?", "-map", "0:s?", "-map", "0:t?")
        val keep = listOf("-map_metadata", "0", "-map_chapters", "0")
        val ext = input.extension

        trimStopped = false
        val tuning = if (precise) preciseArgs(ext, encodersOf(ffmpeg, libs)) else null
        if (precise && tuning == null) {
            Log.w("Hazel", "No precise trim plan for .$ext here, copying the cut instead")
        }

        if (tuning == null) {
            return runFfmpeg(
                quiet + listOf("-ss", start, "-i", input.absolutePath, "-t", length) +
                    listOf("-map", "0:v?", "-map", "0:a?", "-map", "0:s?", "-map", "0:t?") + keep +
                    listOf("-c", "copy", "-avoid_negative_ts", "make_zero", output.absolutePath),
                libs,
                output
            )
        }

        val reencoded = File(output.parentFile, "hazel.pre.$ext")
        try {
            val first = runFfmpeg(
                quiet + listOf("-ss", start, "-i", input.absolutePath, "-t", length) +
                    streams + keep + tuning + reencoded.absolutePath,
                libs,
                reencoded
            )
            if (!first.ok) return first
            return runFfmpeg(
                quiet + listOf("-i", reencoded.absolutePath, "-i", input.absolutePath) +
                    streams + listOf("-map", "1:v?", "-map", "-1:V") + keep +
                    listOf("-c", "copy", output.absolutePath),
                libs,
                output
            )
        } finally {
            reencoded.delete()
        }
    }

    private fun ffmpegLibraryPath(context: android.content.Context): String {
        val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
        val dirs = listOf("ffmpeg", "python", "aria2c")
            .map { File(packages, "$it/usr/lib") }
            .filter { it.isDirectory }
            .map { it.absolutePath }
        return (dirs + context.applicationInfo.nativeLibraryDir).joinToString(":")
    }

    private const val TRIM_STALL_MS = 5 * 60_000L

    private const val TRIM_WATCH_MS = 15_000L

    private fun runFfmpeg(command: List<String>, libs: String, writes: File): TrimOutcome {
        if (trimStopped) return TrimOutcome(false, true, "")
        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["LD_LIBRARY_PATH"] = libs }
                .start()
        } catch (e: Exception) {
            return TrimOutcome(false, false, e.message ?: "ffmpeg did not start")
        }
        trimProcess = process
        if (trimStopped) process.destroy()

        val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = Thread {
            var lastSize = -1L
            var lastChange = System.currentTimeMillis()
            try {
                while (true) {
                    Thread.sleep(TRIM_WATCH_MS)
                    val size = writes.length()
                    val now = System.currentTimeMillis()
                    if (size != lastSize) {
                        lastSize = size
                        lastChange = now
                    } else if (now - lastChange > TRIM_STALL_MS) {
                        stalled.set(true)
                        process.destroy()
                        return@Thread
                    }
                }
            } catch (_: InterruptedException) { }
        }.apply { isDaemon = true; start() }

        return try {
            val tail = StringBuilder()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    tail.append(line).append('\n')
                    if (tail.length > 2000) tail.delete(0, tail.length - 2000)
                }
            }
            val code = process.waitFor()
            val said = if (stalled.get()) "ffmpeg stopped making progress"
            else tail.toString().trim().replace(Regex("(?:/[^/\\s:]+)+/"), "")
            TrimOutcome(code == 0 && !trimStopped && !stalled.get(), trimStopped, said)
        } catch (e: Exception) {
            TrimOutcome(false, trimStopped, e.message ?: "ffmpeg stopped")
        } finally {
            watchdog.interrupt()
            trimProcess = null
        }
    }

    private fun encodersOf(ffmpeg: File, libs: String): Set<String> = try {
        val process = ProcessBuilder(ffmpeg.absolutePath, "-hide_banner", "-encoders")
            .redirectErrorStream(true)
            .apply { environment()["LD_LIBRARY_PATH"] = libs }
            .start()
        val names = process.inputStream.bufferedReader().readLines()
            .mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1) }
            .toSet()
        process.waitFor()
        names
    } catch (_: Exception) {
        emptySet()
    }

    private fun preciseArgs(extension: String, have: Set<String>): List<String>? {
        fun pick(vararg names: String) = names.firstOrNull { it in have }
        val video: String?
        val audio: String?
        when (extension.lowercase()) {
            "mp4", "m4v", "mov", "mkv" -> {
                video = pick("libx264", "mpeg4") ?: return null
                audio = pick("aac")
            }
            "webm" -> {
                video = pick("libvpx-vp9", "libvpx") ?: return null
                audio = pick("libopus", "libvorbis")
            }
            "m4a", "aac" -> { video = null; audio = pick("aac") }
            "mp3" -> { video = null; audio = pick("libmp3lame") }
            "opus", "ogg" -> { video = null; audio = pick("libopus", "libvorbis") }
            "flac" -> { video = null; audio = pick("flac") }
            "wav" -> { video = null; audio = pick("pcm_s16le") }
            else -> return null
        }
        if (audio == null) return null

        val args = mutableListOf("-c", "copy")
        if (video != null) {
            args += listOf("-c:V", video)
            when {
                video == "libx264" ->
                    args += listOf("-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p")
                video.startsWith("libvpx") ->
                    args += listOf("-deadline", "realtime", "-cpu-used", "6", "-crf", "30", "-b:v", "0")
            }
        }
        args += listOf("-c:a", audio)
        when (audio) {
            "aac" -> args += listOf("-b:a", "192k")
            "libmp3lame" -> args += listOf("-q:a", "2")
        }
        return args
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
