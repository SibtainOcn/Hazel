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
    val audioFormats: List<MediaFormat>,
    /**
     * True when this media would not open without the saved sign-in.
     *
     * Set by the read that found out, and carried to the download so it asks the same way.
     * False is the ordinary case, and it is what keeps a public link away from a signed-in
     * request on the sites that answer those with less.
     */
    val requiresSignIn: Boolean = false
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

    /**
     * The soundtracks this media carries, in the order the source listed them, which puts
     * the original first.
     *
     * Empty for almost everything: a source only names a language when it published more
     * than one, so an empty list means there is nothing to choose between.
     */
    val audioLanguages: List<String>
        get() = audioFormats.mapNotNull { it.language }.distinct()

    /** The best audio in [language], falling back to the best of any when it has none. */
    fun bestAudioFor(language: String?): MediaFormat? {
        if (language.isNullOrBlank()) return bestAudio
        return audioFormats.firstOrNull { !it.isGeneric && it.language == language } ?: bestAudio
    }

    /** The track a muxed video download takes its sound from, in [language] where there is one. */
    fun mergeAudioFor(language: String?): MediaFormat? {
        if (language.isNullOrBlank()) return mergeAudio
        return audioFormats.firstOrNull { !it.isGeneric && it.language == language } ?: mergeAudio
    }

    /** True once the source has reported at least one concrete format. */
    val hasResolvedFormats: Boolean
        get() = videoFormats.any { !it.isGeneric } || audioFormats.any { !it.isGeneric }

    /**
     * The format a download with no one watching should take.
     *
     * Used by the direct share, where there is no sheet to choose in. [maxHeight] is a
     * ceiling rather than a target, because sources do not all offer the same ladder and a
     * request for a height this one does not have would resolve to nothing. When nothing
     * clears the ceiling the smallest available is taken, since a download slightly over
     * budget is a better answer than no download at all.
     */
    fun autoPick(isVideo: Boolean, maxHeight: Int, audioLanguage: String? = null): MediaFormat? {
        if (!isVideo) return bestAudioFor(audioLanguage)

        val concrete = videoFormats.filter { !it.isGeneric }
        if (concrete.isEmpty() || maxHeight <= 0) return bestVideo

        return concrete.firstOrNull { it.height in 1..maxHeight }
            ?: concrete.minByOrNull { it.height }
            ?: bestVideo
    }
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
    /**
     * The dubbed track this stream carries, as the source named it, or null where it named
     * nothing. Most media has one soundtrack and reports no language at all; the field only
     * has anything to say about the sources that publish several.
     */
    val language: String? = null,
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
    val isGeneric: Boolean = false,
    /** True when the size came from the bitrate rather than from the source itself. */
    val isEstimatedSize: Boolean = false
) {
    /**
     * The headline without the measured resolution after it.
     *
     * The full label belongs in the format list, where a row is wide and the resolution is
     * what separates two entries with the same note. A card in a set has room for one
     * badge and a size beside it, and there the resolution pushes the size off the end of
     * the row to say something "2160P" already said.
     */
    val shortLabel: String
        get() = label.substringBefore(" (").trim().ifBlank { label }

    /** Codec badge text, e.g. "AVC1" for video or "OPUS" for audio. */
    val codecLabel: String
        get() {
            val codec = if (hasVideo) vcodec else acodec
            return codec?.substringBefore('.')?.takeIf { it.isNotBlank() && it != "none" }
                ?.uppercase() ?: ""
        }

    /**
     * Size badge. Sources report either an exact size or an estimate derived from the
     * bitrate; an estimate is marked so a figure that turns out larger than the file is
     * not read as the app getting it wrong.
     */
    val sizeLabel: String
        get() = formatFileSize(fileSizeBytes).let {
            if (it.isNotBlank() && isEstimatedSize) "~ $it" else it
        }

    val bitrateLabel: String get() = formatBitrate(bitrateKbps)
}

/**
 * What to call a language code on screen.
 *
 * Sources write these as tags rather than words, and "hi-IN" says nothing to the person
 * choosing between soundtracks. The device already knows the names, so the tag is only
 * shown when it turns out not to be one Android recognises.
 */
fun languageLabel(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code.replace('_', '-'))
    return locale.getDisplayName(java.util.Locale.getDefault())
        .takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { it.uppercase() }
        ?: code
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
