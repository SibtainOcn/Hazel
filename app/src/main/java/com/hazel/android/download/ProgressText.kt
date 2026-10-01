package com.hazel.android.download

/**
 * Figures read out of a yt-dlp progress line, which looks like
 * `[download]  42.5% of ~  40.20MiB at 2.35MiB/s ETA 00:10`.
 *
 * Shared by the notification and the screens, so the shade and the card never disagree about
 * how fast a download is going or how long it has left.
 */
object ProgressText {

    /** `at 2.35MiB/s` in a progress line. */
    private val RATE_PATTERN = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""", RegexOption.IGNORE_CASE)

    /** `ETA 00:10` in a progress line. */
    private val ETA_PATTERN = Regex("""ETA\s+([\d:]+)""", RegexOption.IGNORE_CASE)

    /** `[download]  42.5%`, the start of a line reporting bytes on the move. */
    private val TRANSFER_PATTERN = Regex("""^\s*\[download]\s+[\d.]+%""", RegexOption.IGNORE_CASE)

    /** The transfer rate the line reports, or null when it names none. */
    fun rate(line: String): String? = RATE_PATTERN.find(line)?.groupValues?.get(1)

    /** The time remaining the line reports, or null when it names none. */
    fun eta(line: String): String? = ETA_PATTERN.find(line)?.groupValues?.get(1)

    /** Whether the line is yt-dlp reporting a transfer in progress. */
    fun isTransferLine(line: String): Boolean = TRANSFER_PATTERN.containsMatchIn(line)
}
