package com.hazel.android.download

import java.io.File

/**
 * Progress of a cut (`--download-sections`) download. yt-dlp prints no percentage for a cut,
 * so ffmpeg's own `-progress` report is read instead: written time against kept time.
 * Until the first report arrives, the file on disk is followed by size.
 */
object CutProgress {

    /** For `--downloader-args`: output options of the cut's ffmpeg only. `-nostats` keeps stderr quiet. */
    const val FFMPEG_PROGRESS_ARGS = "ffmpeg_o:-progress pipe:1 -nostats"

    private val PROGRESS_LINE = Regex(
        """^(frame|fps|stream_\d+_\d+_q|bitrate|total_size|out_time_us|out_time_ms|out_time|""" +
            """dup_frames|drop_frames|speed|progress)="""
    )

    fun isProgressLine(line: String): Boolean = PROGRESS_LINE.containsMatchIn(line.trim())

    /** Seconds written, from `out_time_us`/`out_time_ms` (both microseconds) or `out_time`. */
    fun outTimeSeconds(line: String): Double? {
        val text = line.trim()
        val micros = when {
            text.startsWith("out_time_us=") -> text.substringAfter('=').toLongOrNull()
            text.startsWith("out_time_ms=") -> text.substringAfter('=').toLongOrNull()
            text.startsWith("out_time=") -> return clockSeconds(text.substringAfter('='))
            else -> return null
        } ?: return null
        return if (micros >= 0L) micros / 1_000_000.0 else null
    }

    private fun clockSeconds(clock: String): Double? {
        val parts = clock.trim().split(':')
        if (parts.size != 3 || clock.trim().startsWith("-")) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return (hours * 3600 + minutes * 60 + seconds).takeIf { it.isFinite() && it >= 0.0 }
    }

    fun speedFactor(line: String): Double? {
        val text = line.trim()
        if (!text.startsWith("speed=")) return null
        return text.substringAfter('=').trim().removeSuffix("x").trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Length of the kept part in seconds, clamped to the source; 0 when unknown. */
    fun keptSeconds(durationSeconds: Int, startSeconds: Double, endSeconds: Double): Double {
        if (!startSeconds.isFinite() || !endSeconds.isFinite()) return 0.0
        if (startSeconds < 0.0 || endSeconds <= startSeconds) return 0.0
        if (durationSeconds <= 0) return endSeconds - startSeconds
        val duration = durationSeconds.toDouble()
        return (minOf(endSeconds, duration) - minOf(startSeconds, duration)).coerceAtLeast(0.0)
    }

    /** Share written, capped at 0.99: only the finished download sets it whole. */
    fun fraction(outSeconds: Double, keptSeconds: Double): Float {
        if (keptSeconds <= 0.0 || !outSeconds.isFinite()) return 0f
        return (outSeconds / keptSeconds).toFloat().coerceIn(0f, 0.99f)
    }

    fun etaSeconds(outSeconds: Double, keptSeconds: Double, speed: Double): Long? {
        if (keptSeconds <= 0.0 || speed <= 0.0 || !outSeconds.isFinite()) return null
        val left = ((keptSeconds - outSeconds) / speed).coerceAtLeast(0.0)
        return if (left.isFinite()) kotlin.math.ceil(left).toLong() else null
    }

    /** `MM:SS` or `H:MM:SS`, as yt-dlp writes an ETA. */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec)
        else "%02d:%02d".format(java.util.Locale.ROOT, m, sec)
    }

    /** The link's size scaled to the kept share of its length; 0 when it cannot be worked out. */
    fun expectedBytes(
        sourceBytes: Long,
        durationSeconds: Int,
        startSeconds: Double,
        endSeconds: Double
    ): Long {
        if (sourceBytes <= 0L || durationSeconds <= 0) return 0L
        val kept = keptSeconds(durationSeconds, startSeconds, endSeconds)
        if (kept <= 0.0) return 0L
        return (sourceBytes * (kept / durationSeconds)).toLong().coerceAtLeast(1L)
    }

    /** Thumbnails, subtitles and metadata: sized for the whole link, not the cut. */
    private val SIDE_FILE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif",
        "vtt", "srt", "ass", "ssa", "ttml", "srv1", "srv2", "srv3", "json3", "lrc", "sbv",
        "json", "description", "txt"
    )

    /**
     * The largest media file in [dir]; 0 when missing or unreadable. Largest, not the sum:
     * post-processing (cover/tag embedding, audio conversion) writes a second copy beside the first.
     */
    fun mediaBytesIn(dir: File): Long = try {
        dir.listFiles()
            ?.filter { f ->
                f.isFile && !f.name.endsWith(".ytdl") &&
                    f.extension.lowercase() !in SIDE_FILE_EXTENSIONS
            }
            ?.maxOfOrNull { it.length() }
            ?: 0L
    } catch (_: Exception) {
        0L
    }
}
