package com.hazel.android.download

import android.content.Context
import android.os.Build
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.hazel.android.util.StagedFiles
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Trim after download: cuts the finished file in the work folder with the bundled ffmpeg by
 * stream copy, replacing it in place. Sidecar subtitles are cut to match. Precise cuts are
 * left to yt-dlp, so this never re-encodes.
 */
object LocalTrim {

    class Result(val ok: Boolean, val stopped: Boolean, val message: String = "")

    private const val WATCH_MS = 15_000L
    private const val LEGACY_POLL_MS = 250L
    private const val TAIL_CHARS = 2000
    private const val STALL_MS = 5 * 60_000L
    private const val TRIM_PREFIX = "hazel.trim."

    /** Written once the file is cut, so a resumed run never cuts it twice. A dot file: the save purges it. */
    internal const val DONE_MARKER = ".hazel.trimmed"
    private val TEXT_SUBTITLES = setOf("srt", "vtt")
    private val FORMAT_FRAGMENT = Regex("""\.f\d+[\w-]*\.\w+$""")

    @Volatile private var stopped = false
    @Volatile private var process: Process? = null

    /** Called as each download starts, so a stop during it is never lost. */
    fun arm() {
        stopped = false
    }

    fun stop() {
        stopped = true
        process?.destroy()
    }

    fun trim(context: Context, dir: File, startSeconds: Double, endSeconds: Double): Result {
        if (stopped) return Result(false, true)
        val range = rangeKey(startSeconds, endSeconds)
        val marker = File(dir, DONE_MARKER)
        if (runCatching { marker.readText() == range }.getOrDefault(false)) return Result(true, false)

        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpeg.exists()) return Result(false, false, "ffmpeg is missing")

        dir.listFiles()?.filter { it.name.startsWith(TRIM_PREFIX) }?.forEach { it.delete() }
        val source = mainMedia(dir) ?: return Result(false, false, "the downloaded file was not found")
        if (source.extension.isBlank()) return Result(false, false, "the downloaded file has no extension")

        val env = libraryPath(context)
        val streams = streamsOf(ffmpeg, env, source)
        if (streams.isEmpty()) return Result(false, false, "the downloaded file could not be read")
        val output = File(dir, TRIM_PREFIX + source.extension)
        val result = run(
            context,
            trimCommand(ffmpeg.absolutePath, source.absolutePath, output.absolutePath, streams, startSeconds, endSeconds),
            env,
            output
        )
        if (!result.ok || output.length() <= 0L) {
            output.delete()
            return if (result.ok) Result(false, false, "ffmpeg wrote nothing") else result
        }
        if (!output.renameTo(source)) {
            output.delete()
            return Result(false, false, "could not replace the downloaded file")
        }
        runCatching { marker.writeText(range) }
        trimSidecarSubtitles(dir, startSeconds, endSeconds)
        // The save takes the newest file as the download's name and size.
        source.setLastModified(System.currentTimeMillis())
        return Result(true, false)
    }

    private fun rangeKey(start: Double, end: Double) = "%.3f-%.3f".format(Locale.ROOT, start, end)

    internal class Stream(val index: Int, val type: String, val cover: Boolean)

    private val STREAM_LINE = Regex("""Stream #0:(\d+)\S*: (Video|Audio|Subtitle|Attachment|Data):""")

    /** Streams as `ffmpeg -i` lists them; an embedded cover is tagged `(attached pic)`. */
    internal fun parseStreams(listing: String): List<Stream> = listing.lines().mapNotNull { line ->
        val m = STREAM_LINE.find(line) ?: return@mapNotNull null
        Stream(m.groupValues[1].toInt(), m.groupValues[2], "(attached pic)" in line)
    }

    /**
     * Streams are mapped by index, covers last with their flag set again: a remux drops it,
     * turning the cover into a second video track. Data tracks (mp4 chapter text) are rebuilt
     * by the muxer.
     */
    internal fun trimCommand(
        ffmpeg: String,
        source: String,
        output: String,
        streams: List<Stream>,
        startSeconds: Double,
        endSeconds: Double
    ): List<String> {
        val main = streams.filter { !it.cover && it.type != "Data" }
        val covers = streams.filter { it.cover }
        val videos = main.count { it.type == "Video" }
        return buildList {
            addAll(listOf(ffmpeg, "-y", "-hide_banner", "-loglevel", "error"))
            addAll(listOf("-ss", "%.3f".format(Locale.ROOT, startSeconds)))
            addAll(listOf("-t", "%.3f".format(Locale.ROOT, endSeconds - startSeconds)))
            addAll(listOf("-i", source))
            (main + covers).forEach { addAll(listOf("-map", "0:${it.index}")) }
            addAll(listOf("-map_metadata", "0", "-map_chapters", "0", "-c", "copy"))
            covers.indices.forEach { addAll(listOf("-disposition:v:${videos + it}", "attached_pic")) }
            add(output)
        }
    }

    private fun streamsOf(ffmpeg: File, env: String, source: File): List<Stream> = try {
        val p = ProcessBuilder(ffmpeg.absolutePath, "-hide_banner", "-i", source.absolutePath)
            .redirectErrorStream(true)
            .apply { environment()["LD_LIBRARY_PATH"] = env }
            .start()
        val listing = p.inputStream.bufferedReader().use { it.readText() }
        if (!waitFor(p, 30_000L)) p.destroy()
        parseStreams(listing)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * The finished media file: the newest that is not a fragment, part or side file, as the
     * save picks it. With "keep video" and audio extraction, the extracted audio is newest.
     */
    fun mainMedia(dir: File): File? = try {
        // Includes files in folders from the user's output template.
        StagedFiles.list(dir).map { it.file }
            .filter { f ->
                f.name != DONE_MARKER && !f.name.startsWith(TRIM_PREFIX) &&
                    !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.endsWith(".temp") &&
                    f.extension.lowercase() !in CutProgress.SIDE_FILE_EXTENSIONS
            }
            .let { files -> files.filterNot { FORMAT_FRAGMENT.containsMatchIn(it.name) }.ifEmpty { files } }
            .maxWithOrNull(compareBy<File>({ it.lastModified() }, { it.length() }))
    } catch (_: Exception) {
        null
    }

    /** Best effort: a sidecar left uncut is only out of step, never a failed download. */
    private fun trimSidecarSubtitles(dir: File, start: Double, end: Double) {
        StagedFiles.list(dir).map { it.file }
            .filter { it.extension.lowercase() in TEXT_SUBTITLES }
            .forEach { sub ->
                try {
                    sub.writeText(cutSubtitles(sub.readText(), sub.extension.lowercase() == "srt", start, end))
                } catch (e: Exception) {
                    Log.w("Hazel", "Could not trim ${sub.name}: ${e.message}")
                }
            }
    }

    private val CUE_TIME = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})(.*)""")
    private val INLINE_TIME = Regex("""<((?:\d+:)?\d{1,2}:\d{2}\.\d{3})>""")

    /**
     * SRT or WebVTT [text] cut to [start]..[end] seconds: cues outside are dropped, the rest
     * clipped and shifted to begin at zero. Blocks without a timing line (headers, styles) stay.
     */
    internal fun cutSubtitles(text: String, srt: Boolean, start: Double, end: Double): String {
        val blocks = text.replace("\r\n", "\n").trim().split(Regex("\n{2,}"))
        val kept = mutableListOf<String>()
        var index = 0
        for (block in blocks) {
            val lines = block.lines()
            val timing = lines.indexOfFirst { CUE_TIME.containsMatchIn(it) }
            if (timing < 0) {
                if (!srt) kept += block
                continue
            }
            val match = CUE_TIME.find(lines[timing]) ?: continue
            val from = seconds(match.groupValues[1]) ?: continue
            val to = seconds(match.groupValues[2]) ?: continue
            if (to <= start || from >= end) continue
            val line = stamp(maxOf(from, start) - start, srt) + " --> " +
                stamp(minOf(to, end) - start, srt) + match.groupValues[3]
            val body = lines.drop(timing + 1).map { cue ->
                INLINE_TIME.replace(cue) { m ->
                    val at = seconds(m.groupValues[1]) ?: return@replace m.value
                    "<" + stamp((at - start).coerceIn(0.0, end - start), false) + ">"
                }
            }
            kept += if (srt) (listOf((++index).toString(), line) + body).joinToString("\n")
            else (lines.take(timing) + line + body).joinToString("\n")
        }
        return kept.joinToString("\n\n", postfix = "\n")
    }

    private fun seconds(stamp: String): Double? {
        val parts = stamp.replace(',', '.').split(':')
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble()
                2 -> parts[0].toLong() * 60 + parts[1].toDouble()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun stamp(value: Double, srt: Boolean): String {
        val millis = Math.round(value.coerceAtLeast(0.0) * 1000)
        val text = "%02d:%02d:%02d.%03d".format(
            Locale.ROOT, millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60, millis % 1000
        )
        return if (srt) text.replace('.', ',') else text
    }

    private fun run(context: Context, command: List<String>, env: String, writes: File): Result {
        if (stopped) return Result(false, true)
        val started = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["LD_LIBRARY_PATH"] = env }
                .start()
        } catch (e: Exception) {
            return Result(false, false, e.message ?: "ffmpeg did not start")
        }
        process = started
        if (stopped) started.destroy()
        val tail = StringBuilder()
        // Drained as it comes, or ffmpeg blocks once the pipe is full.
        val drain = thread(isDaemon = true, name = "hazel-trim-output") {
            try {
                started.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        synchronized(tail) {
                            tail.append(buffer, 0, read)
                            if (tail.length > TAIL_CHARS * 2) tail.delete(0, tail.length - TAIL_CHARS)
                        }
                    }
                }
            } catch (_: IOException) {
            }
        }

        return try {
            var lastSize = -1L
            var lastChange = System.currentTimeMillis()
            var stalled = false
            while (!waitFor(started, WATCH_MS)) {
                val size = writes.length()
                val now = System.currentTimeMillis()
                if (size != lastSize) {
                    lastSize = size
                    lastChange = now
                } else if (now - lastChange > STALL_MS) {
                    stalled = true
                    started.destroy()
                }
            }
            val code = started.exitValue()
            drain.join(1_000L)
            when {
                stopped -> Result(false, true)
                stalled -> Result(false, false, "ffmpeg stopped making progress")
                code != 0 -> Result(false, false, clean(synchronized(tail) { tail.toString() }))
                else -> Result(true, false)
            }
        } catch (e: Exception) {
            started.destroy()
            Result(false, stopped, e.message ?: "ffmpeg stopped")
        } finally {
            process = null
        }
    }

    /** The last lines ffmpeg wrote, without file paths. */
    private fun clean(output: String): String =
        output.takeLast(TAIL_CHARS).trim().replace(PATH_PREFIX, "")

    private val PATH_PREFIX = Regex("""(?:/[^/\s:]+)+/""")

    /** `Process.waitFor(timeout)` needs Android 8; below it the exit is polled. */
    internal fun waitFor(
        p: Process,
        timeoutMs: Long,
        legacy: Boolean = false
    ): Boolean {
        if (!legacy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            try {
                p.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
            }
            val leftMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (leftMs <= 0) return false
            Thread.sleep(minOf(leftMs, LEGACY_POLL_MS))
        }
    }

    private fun libraryPath(context: Context): String {
        val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
        return (listOf("python", "ffmpeg", "aria2c")
            .map { File(packages, "$it/usr/lib") }
            .filter { it.isDirectory }
            .map { it.absolutePath } + context.applicationInfo.nativeLibraryDir)
            .joinToString(":")
    }
}
