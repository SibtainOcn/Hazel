package com.hazel.android.download

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Trim after download: cuts the finished file in the work folder with the bundled ffmpeg,
 * replacing it in place. Sidecar subtitles are cut to match.
 */
object LocalTrim {

    class Result(val ok: Boolean, val stopped: Boolean, val message: String = "")

    private const val WATCH_MS = 15_000L
    private const val STALL_MS = 5 * 60_000L
    private const val TRIM_PREFIX = "hazel.trim."
    private val TEXT_SUBTITLES = setOf("srt", "vtt")
    private val FORMAT_FRAGMENT = Regex("""\.f\d+[\w-]*\.\w+$""")

    @Volatile private var stopped = false
    @Volatile private var process: Process? = null
    @Volatile private var encoders: Set<String>? = null

    /** Called as each download starts, so a stop during it is never lost. */
    fun arm() {
        stopped = false
    }

    fun stop() {
        stopped = true
        process?.destroy()
    }

    fun trim(context: Context, dir: File, startSeconds: Double, endSeconds: Double, precise: Boolean): Result {
        if (stopped) return Result(false, true)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpeg.exists()) return Result(false, false, "ffmpeg is missing")

        dir.listFiles()?.filter { it.name.startsWith(TRIM_PREFIX) }?.forEach { it.delete() }
        val source = mainMedia(dir) ?: return Result(false, false, "the downloaded file was not found")
        if (source.extension.isBlank()) return Result(false, false, "the downloaded file has no extension")

        val env = libraryPath(context)
        val streams = streamsOf(ffmpeg, env, source)
        if (streams.isEmpty()) return Result(false, false, "the downloaded file could not be read")
        val output = File(dir, TRIM_PREFIX + source.extension)
        val command = trimCommand(
            ffmpeg.absolutePath, source.absolutePath, output.absolutePath, streams, startSeconds, endSeconds,
            if (precise) encodersFor(source.extension, ffmpeg, env) else null
        )
        val result = run(context, command, env, output)
        if (!result.ok || output.length() <= 0L) {
            output.delete()
            return if (result.ok) Result(false, false, "ffmpeg wrote nothing") else result
        }
        if (!output.renameTo(source)) {
            output.delete()
            return Result(false, false, "could not replace the downloaded file")
        }

        trimSidecarSubtitles(dir, startSeconds, endSeconds)
        return Result(true, false)
    }

    internal class Stream(val index: Int, val type: String, val cover: Boolean)
    internal class Encoders(val video: String, val audio: String)

    private val STREAM_LINE = Regex("""Stream #0:(\d+)\S*: (Video|Audio|Subtitle|Attachment|Data):""")

    /** Streams as `ffmpeg -i` lists them; an embedded cover is tagged `(attached pic)`. */
    internal fun parseStreams(listing: String): List<Stream> = listing.lines().mapNotNull { line ->
        val m = STREAM_LINE.find(line) ?: return@mapNotNull null
        Stream(m.groupValues[1].toInt(), m.groupValues[2], "(attached pic)" in line)
    }

    /**
     * Streams are mapped by index, covers last with their flag set again: a remux drops it,
     * turning the cover into a second video track. Precise re-encodes video and audio (copied
     * audio would start at the keyframe before the cut) and takes chapters from a second,
     * copied read of the source, since ffmpeg mangles them while encoding.
     */
    internal fun trimCommand(
        ffmpeg: String,
        source: String,
        output: String,
        streams: List<Stream>,
        startSeconds: Double,
        endSeconds: Double,
        encoders: Encoders?
    ): List<String> {
        val cut = listOf(
            "-ss", "%.3f".format(Locale.ROOT, startSeconds),
            "-t", "%.3f".format(Locale.ROOT, endSeconds - startSeconds),
            "-i", source
        )
        val main = streams.filter { !it.cover && it.type != "Data" }
        val covers = streams.filter { it.cover }
        val videos = main.count { it.type == "Video" }
        val meta = if (encoders != null) "1" else "0"
        return buildList {
            addAll(listOf(ffmpeg, "-y", "-hide_banner", "-loglevel", "error"))
            addAll(cut)
            if (encoders != null) addAll(cut)
            (main + covers).forEach { addAll(listOf("-map", "0:${it.index}")) }
            addAll(listOf("-map_metadata", meta, "-map_chapters", meta, "-c", "copy"))
            if (encoders != null) {
                repeat(videos) { addAll(listOf("-c:v:$it", encoders.video)) }
                if (videos > 0) addAll(videoTuning(encoders.video))
                addAll(listOf("-c:a", encoders.audio) + audioTuning(encoders.audio))
            }
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
        p.waitFor(30, TimeUnit.SECONDS)
        parseStreams(listing)
    } catch (_: Exception) {
        emptyList()
    }

    /** The finished media file: the largest that is not a fragment, part or side file. */
    fun mainMedia(dir: File): File? = try {
        dir.listFiles()
            ?.filter { f ->
                f.isFile && !f.name.startsWith(".") && !f.name.startsWith(TRIM_PREFIX) &&
                    !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.endsWith(".temp") &&
                    f.extension.lowercase() !in CutProgress.SIDE_FILE_EXTENSIONS
            }
            ?.let { files -> files.filterNot { FORMAT_FRAGMENT.containsMatchIn(it.name) }.ifEmpty { files } }
            ?.maxByOrNull { it.length() }
    } catch (_: Exception) {
        null
    }

    /** Best effort: a sidecar left uncut is only out of step, never a failed download. */
    private fun trimSidecarSubtitles(dir: File, start: Double, end: Double) {
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in TEXT_SUBTITLES }
            ?.forEach { sub ->
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
        val log = File(context.cacheDir, "hazel-trim.log")
        val started = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .apply { environment()["LD_LIBRARY_PATH"] = env }
                .start()
        } catch (e: Exception) {
            return Result(false, false, e.message ?: "ffmpeg did not start")
        }
        process = started
        if (stopped) started.destroy()

        return try {
            var lastSize = -1L
            var lastChange = System.currentTimeMillis()
            var stalled = false
            while (!started.waitFor(WATCH_MS, TimeUnit.MILLISECONDS)) {
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
            when {
                stopped -> Result(false, true)
                stalled -> Result(false, false, "ffmpeg stopped making progress")
                code != 0 -> Result(false, false, tailOf(log))
                else -> Result(true, false)
            }
        } catch (e: Exception) {
            Result(false, stopped, e.message ?: "ffmpeg stopped")
        } finally {
            process = null
            log.delete()
        }
    }

    /** The last lines ffmpeg wrote, without file paths. */
    private fun tailOf(log: File): String = try {
        log.readText().takeLast(2000).trim().replace(Regex("""(?:/[^/\s:]+)+/"""), "")
    } catch (_: Exception) {
        ""
    }

    private fun libraryPath(context: Context): String {
        val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
        return (listOf("python", "ffmpeg", "aria2c")
            .map { File(packages, "$it/usr/lib") }
            .filter { it.isDirectory }
            .map { it.absolutePath } + context.applicationInfo.nativeLibraryDir)
            .joinToString(":")
    }

    /** Encoders for a precise trim of a video container; null copies instead (audio cuts are exact anyway). */
    private fun encodersFor(extension: String, ffmpeg: File, env: String): Encoders? {
        val (videos, audios) = when (extension.lowercase()) {
            "mp4", "m4v", "mov", "mkv" -> listOf("libx264", "mpeg4") to listOf("aac")
            "webm" -> listOf("libvpx-vp9", "libvpx") to listOf("libopus", "libvorbis")
            else -> return null
        }
        val have = encoders ?: listEncoders(ffmpeg, env).also { if (it.isNotEmpty()) encoders = it }
        val video = videos.firstOrNull { it in have }
        val audio = audios.firstOrNull { it in have }
        if (video == null || audio == null) {
            Log.w("Hazel", "No encoder for a precise .$extension trim, copying")
            return null
        }
        return Encoders(video, audio)
    }

    private fun videoTuning(encoder: String): List<String> = when {
        encoder == "libx264" -> listOf("-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p")
        encoder == "mpeg4" -> listOf("-q:v", "2")
        encoder.startsWith("libvpx") -> listOf("-deadline", "realtime", "-cpu-used", "6", "-crf", "30", "-b:v", "0")
        else -> emptyList()
    }

    private fun audioTuning(encoder: String): List<String> = when (encoder) {
        "aac" -> listOf("-b:a", "192k")
        "libopus" -> listOf("-b:a", "160k")
        "libvorbis" -> listOf("-q:a", "6")
        else -> emptyList()
    }

    private fun listEncoders(ffmpeg: File, env: String): Set<String> = try {
        val p = ProcessBuilder(ffmpeg.absolutePath, "-hide_banner", "-encoders")
            .redirectErrorStream(true)
            .apply { environment()["LD_LIBRARY_PATH"] = env }
            .start()
        val names = p.inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1) }.toSet()
        }
        p.waitFor()
        names
    } catch (_: Exception) {
        emptySet()
    }
}
