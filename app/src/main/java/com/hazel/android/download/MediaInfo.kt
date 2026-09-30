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
    val requiresSignIn: Boolean = false,
    /**
     * yt-dlp's `live_status`: "is_live", "is_upcoming", "post_live", "was_live", "not_live",
     * or blank when the source does not say. Decides whether the live stream options apply.
     */
    val liveStatus: String = ""
) {
    /** Streaming now. */
    val isLive: Boolean get() = liveStatus == "is_live"

    /** Scheduled and not started yet: a premiere, or a stream announced ahead. */
    val isUpcoming: Boolean get() = liveStatus == "is_upcoming"

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

    /**
     * The soundtrack of this media that answers to a preferred language, or null when it has
     * none. Sources write the same language in more than one way ("hi", "hi-IN"), so an exact
     * match is tried first and then one on the language alone.
     */
    fun languageMatching(preferred: String): String? {
        val wanted = preferred.trim()
        if (wanted.isBlank()) return null
        val base = wanted.substringBefore('-').lowercase()
        return audioLanguages.firstOrNull { it.equals(wanted, ignoreCase = true) }
            ?: audioLanguages.firstOrNull { it.substringBefore('-').lowercase() == base }
    }

    /**
     * The best audio in [language], falling back to the best of any when it has none.
     *
     * With a [codec] asked for, the best stream of that codec is preferred. A source that
     * does not offer it gives its best of any codec instead, since a link that downloads in
     * another codec is a better answer than one that does not download. Before the formats
     * are known, the preference is handed to yt-dlp as a format filter with the same
     * fallback built in.
     */
    fun bestAudioFor(language: String?, codec: AudioCodec? = null): MediaFormat? {
        if (codec != null) {
            val concrete = audioFormats.filter { !it.isGeneric }
            if (concrete.isEmpty()) return codec.genericFormat()
            val inLanguage = if (language.isNullOrBlank()) concrete
            else concrete.filter { it.language == language }
            inLanguage.firstOrNull { codec.matches(it) }?.let { return it }
        }
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
    fun autoPick(
        isVideo: Boolean,
        maxHeight: Int,
        audioLanguage: String? = null,
        audioCodec: AudioCodec? = null,
        videoCodec: VideoCodec? = null
    ): MediaFormat? {
        if (!isVideo) return bestAudioFor(audioLanguage, audioCodec)
        if (videoFormats.isEmpty()) return null

        val concrete = videoFormats.filter { !it.isGeneric }
        // A preferred codec is taken at the height the ceiling lands on, never by giving
        // up resolution for it: a sharper picture in another codec is the better answer.
        val chosen = pickVideo(concrete, maxHeight) ?: return null
        if (videoCodec == null || chosen.isGeneric) return chosen
        return concrete.firstOrNull { it.height == chosen.height && videoCodec.matches(it) } ?: chosen
    }

    private fun pickVideo(concrete: List<MediaFormat>, maxHeight: Int): MediaFormat? {
        if (maxHeight == WORST_HEIGHT) {
            return concrete.minByOrNull { it.height } ?: WORST_VIDEO
        }
        if (maxHeight <= 0) return bestVideo
        if (concrete.isEmpty()) {
            return MediaFormat(
                formatId = "bv*[height<=$maxHeight]",
                selector = "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b",
                label = "${maxHeight}p",
                ext = "",
                vcodec = null,
                acodec = null,
                height = maxHeight,
                fps = 0,
                bitrateKbps = 0.0,
                fileSizeBytes = 0L,
                hasVideo = true,
                hasAudio = true,
                isGeneric = true
            )
        }

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
    val isEstimatedSize: Boolean = false,
    /**
     * A yt-dlp format sort (`-S`) that goes with [selector], for a generic row that names a
     * target rather than a stream: "res:720" prefers the resolution closest to 720 without
     * going over, and the closest above only where nothing is at or under it.
     */
    val sort: String? = null
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
     * Container / extension label shown in the UI.
     * Audio streams are often encapsulated in WebM (with Opus codec) or M4A (with AAC codec).
     * For audio, showing raw "WEBM" confuses users who expect audio formats (like OPUS).
     */
    val displayContainer: String
        get() = ext.ifBlank { codecLabel.ifBlank { "DEFAULT" } }.uppercase()

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

/** The quality ceiling that asks for the smallest video a source has rather than a height. */
const val WORST_HEIGHT = -1

/** The engine's own smallest video with sound, for a link whose formats are not known yet. */
val WORST_VIDEO = MediaFormat(
    formatId = "worst",
    selector = "wv*+wa/w",
    label = "Worst quality",
    ext = "",
    vcodec = null,
    acodec = null,
    height = 0,
    fps = 0,
    bitrateKbps = 0.0,
    fileSizeBytes = 0L,
    hasVideo = true,
    hasAudio = true,
    isGeneric = true
)

/**
 * An audio codec a download can prefer to receive the stream in.
 *
 * This is what the stream arrives as, before any conversion: a source offering Opus and AAC
 * of the same track sends whichever is asked for, and the file keeps it unless a different
 * output format is chosen as well.
 */
/**
 * A video codec a download can prefer.
 *
 * Matched on the `vcodec` a source reports, by the names it goes by there (H.264 is written
 * "avc1" by YouTube and "h264" elsewhere). [sortKey] is the name yt-dlp's format sorting
 * uses, for the generic rows that are resolved by yt-dlp rather than picked from a list.
 */
enum class VideoCodec(val label: String, private val prefixes: List<String>, val sortKey: String) {
    H264("H.264 (AVC)", listOf("avc", "h264"), "h264"),
    H265("H.265 (HEVC)", listOf("hvc", "hev", "h265"), "h265"),
    VP9("VP9", listOf("vp9", "vp09"), "vp9"),
    AV1("AV1", listOf("av01", "av1"), "av01");

    fun matches(format: MediaFormat): Boolean {
        val codec = format.vcodec?.lowercase()?.takeIf { it.isNotBlank() && it != "none" } ?: return false
        return prefixes.any { codec.startsWith(it) }
    }
}

enum class AudioCodec(
    /** Badge text, the way codecs are written everywhere else in the app. */
    val label: String,
    /** Leading part of the `acodec` a source reports for this codec. */
    private val codecPrefix: String,
    /** Extensions that carry only this codec, for sources that name no codec. */
    private val extensions: Set<String>
) {
    OPUS("OPUS", "opus", setOf("opus")),
    AAC("AAC", "mp4a", setOf("m4a", "aac")),
    VORBIS("VORBIS", "vorbis", setOf("ogg", "oga")),
    MP3("MP3", "mp3", setOf("mp3")),
    FLAC("FLAC", "flac", setOf("flac"));

    /** The name yt-dlp's format sorting uses for this codec. */
    val sortKey: String
        get() = when (this) {
            OPUS -> "opus"
            AAC -> "aac"
            VORBIS -> "vorbis"
            MP3 -> "mp3"
            FLAC -> "flac"
        }

    /**
     * Whether [format] is in this codec. Many sites name the codec; the rest are read from
     * an extension that only ever holds this one, and anything else is left unmatched
     * rather than guessed at.
     */
    fun matches(format: MediaFormat): Boolean {
        val codec = format.acodec?.lowercase()?.takeIf { it.isNotBlank() && it != "none" }
        return if (codec != null) codec.startsWith(codecPrefix)
        else format.ext.lowercase() in extensions
    }

    /** The yt-dlp expression for the best stream in this codec, or the best of any. */
    fun genericFormat() = MediaFormat(
        formatId = "ba[acodec^=$codecPrefix]",
        selector = "ba[acodec^=$codecPrefix]/ba/b",
        label = label,
        ext = "",
        vcodec = null,
        acodec = codecPrefix,
        height = 0,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = 0L,
        hasVideo = false,
        hasAudio = true,
        isGeneric = true
    )

    companion object {
        /** The codec [format] carries, or null when it is not one of these. */
        fun of(format: MediaFormat): AudioCodec? = entries.firstOrNull { it.matches(format) }
    }
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

/**
 * A title fit to name a file and fill the sheet's title field.
 *
 * Sites without titles (TikTok, X, Facebook and the like) hand over the whole post as one,
 * line breaks, hashtags and all. This keeps what reads as the title: the text on one line,
 * without the run of hashtags and mentions at its end, and cut at a word near
 * [MAX_TITLE_CHARS] when it is longer. A title that is nothing but tags keeps them, so there
 * is always something left.
 */
fun readableTitle(raw: String): String {
    val flat = raw.replace(Regex("\\s+"), " ").trim()
    val untagged = flat.replace(TRAILING_TAGS, "").trim().ifBlank { flat }
    if (untagged.length <= MAX_TITLE_CHARS) return untagged
    // Never between the two halves of an emoji or other character outside the basic plane.
    val cut = untagged.take(MAX_TITLE_CHARS).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
    val lastSpace = cut.lastIndexOf(' ')
    return (if (lastSpace >= MAX_TITLE_CHARS * 2 / 3) cut.take(lastSpace) else cut)
        .trimEnd(' ', ',', ';', ':', '-', '|')
}

private const val MAX_TITLE_CHARS = 100

/** Hashtags and mentions at the very end of a caption, with anything between them. */
private val TRAILING_TAGS = Regex("""(?:[\s,.|·]*[#@][\p{L}\p{N}_.]+)+[\s,.|·]*$""")
