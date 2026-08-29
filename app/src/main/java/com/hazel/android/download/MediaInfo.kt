package com.hazel.android.download

/**
 * Metadata for a single media URL, as reported by `yt-dlp --dump-json`.
 */
data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSeconds: Int,
    val videoFormats: List<MediaFormat>,
    val audioFormats: List<MediaFormat>
) {
    /**
     * The entry the sheet opens on for each tab.
     *
     * A real format is preferred over the generic "best" row, because it can show what will
     * actually be downloaded: resolution, codec, size and format id. The generic row is only
     * used when the source reported no usable formats at all, which is the one case where
     * there is nothing concrete to show.
     */
    val bestVideo: MediaFormat?
        get() = videoFormats.firstOrNull { !it.isGeneric } ?: videoFormats.firstOrNull()

    val bestAudio: MediaFormat?
        get() = audioFormats.firstOrNull { !it.isGeneric } ?: audioFormats.firstOrNull()

    /**
     * Audio track paired with a video-only stream when the file is muxed. Naming the track
     * lets the sheet show its format id instead of leaving the merge unexplained.
     */
    val mergeAudio: MediaFormat?
        get() = audioFormats.firstOrNull { !it.isGeneric }

    /** True once the source has reported at least one concrete format. */
    val hasResolvedFormats: Boolean
        get() = videoFormats.any { !it.isGeneric } || audioFormats.any { !it.isGeneric }
}

/**
 * One selectable entry in the format sheet.
 *
 * [selector] is what gets passed to yt-dlp as `-f`, so the download always uses exactly what
 * the row advertised. For a concrete format that is its id; for the generic "best" rows it is
 * a yt-dlp format expression, which is what keeps a download possible on sources that report
 * no usable format list at all.
 */
data class MediaFormat(
    val formatId: String,
    val selector: String,
    val label: String,
    val ext: String,
    val vcodec: String?,
    val acodec: String?,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Double,
    val fileSizeBytes: Long,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** True for the synthesised "best available" rows, which have no real format id. */
    val isGeneric: Boolean = false
) {
    /** Codec badge text, e.g. "AVC1" for video or "OPUS" for audio. */
    val codecLabel: String
        get() {
            val codec = if (hasVideo) vcodec else acodec
            return codec?.substringBefore('.')?.takeIf { it.isNotBlank() && it != "none" }
                ?.uppercase() ?: ""
        }

    val sizeLabel: String get() = formatFileSize(fileSizeBytes)

    val bitrateLabel: String get() = formatBitrate(bitrateKbps)
}

/** "1.2 GB" / "40.2 MB" / "812 KB", or blank when the size is unknown. */
fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}

/** "105.781k" style bitrate badge, matching how yt-dlp reports tbr/abr in kbps. */
fun formatBitrate(kbps: Double): String = when {
    kbps <= 0 -> ""
    kbps >= 1000 -> "%.2f Mbps".format(kbps / 1000.0)
    else -> "%.0f kbps".format(kbps)
}

/** "3:23" or "1:04:17". */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
