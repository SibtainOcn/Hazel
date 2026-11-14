package com.hazel.android.download

import java.io.File

/**
 * What a cut download is measured against.
 *
 * A cut is passed to yt-dlp as `--download-sections`, which hands the transfer to ffmpeg.
 * ffmpeg reports its progress on stderr, which the engine does not pass on, and yt-dlp prints
 * no `[download]  42.5%` line for it, so the percentage the card and the shade read never
 * arrives. What can still be measured is the file ffmpeg is writing, against what the kept
 * part should come to: the size the sheet showed for the whole link, scaled by the share of
 * its running time the cut keeps.
 *
 * That is an estimate, because a bitrate is not constant, so it only sets the scale of the
 * bar. The caller keeps the bar short of whole and the finished download sets it to whole.
 */
object CutProgress {

    /**
     * The size the kept part should come to, or 0 when it cannot be worked out: the size
     * or the running time of the link is not known, or the cut is not a part of it.
     *
     * A cut that runs past the end is measured to the end, as yt-dlp keeps it.
     */
    fun expectedBytes(
        sourceBytes: Long,
        durationSeconds: Int,
        startSeconds: Double,
        endSeconds: Double
    ): Long {
        if (sourceBytes <= 0L || durationSeconds <= 0) return 0L
        if (!startSeconds.isFinite() || !endSeconds.isFinite()) return 0L
        if (startSeconds < 0.0 || endSeconds <= startSeconds) return 0L

        val duration = durationSeconds.toDouble()
        val kept = minOf(endSeconds, duration) - minOf(startSeconds, duration)
        if (kept <= 0.0) return 0L

        return (sourceBytes * (kept / duration)).toLong().coerceAtLeast(1L)
    }

    /**
     * Files yt-dlp writes beside the media: the thumbnail, the subtitles and the like. They are
     * written before the transfer starts and are sized for the whole link, not for the cut, so
     * counting them against the size of the kept part made a short cut start high, and a very
     * short one start at the cap.
     */
    private val SIDE_FILE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif",
        "vtt", "srt", "ass", "ssa", "ttml", "srv1", "srv2", "srv3", "json3", "lrc", "sbv",
        "json", "description", "txt"
    )

    /**
     * What the media of a cut weighs in [dir], the engine's bookkeeping and the files beside
     * the media aside. Zero for a folder that is missing or cannot be read.
     *
     * With [includePartFiles] false the part files are left out as well. That is the reading
     * taken as a run starts: ffmpeg writes a cut from the beginning and overwrites a part file
     * a paused run left, so those bytes are not a head start, and a cut that began from zero
     * must not open at the figure the stopped one reached.
     */
    fun mediaBytesIn(dir: File, includePartFiles: Boolean = true): Long = try {
        dir.listFiles()
            ?.filter { f ->
                f.isFile && !f.name.endsWith(".ytdl") &&
                    f.extension.lowercase() !in SIDE_FILE_EXTENSIONS &&
                    (includePartFiles || !f.name.endsWith(".part"))
            }
            ?.sumOf { it.length() }
            ?: 0L
    } catch (_: Exception) {
        0L
    }
}
