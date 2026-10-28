package com.hazel.android.download

/**
 * Formats that exist before a link has been read.
 *
 * A shared link opens its sheet at once, before its own formats are known, so the sheet
 * offers a ladder of engine expressions instead: best, a height or bitrate to stay under,
 * worst. Each is something any source can answer, so a choice made from the ladder can be
 * downloaded straight away, and when the read lands it is matched to what the link actually
 * offers.
 */
object GenericFormats {

    /**
     * Heights the video ladder offers, tallest first. The one list of them: the sheet a link
     * opens before it is read and the quality a set of links is given both offer these.
     */
    val VIDEO_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

    /**
     * The quality ceiling a ladder row stands for, in the terms a set of links keeps it:
     * 0 for best, [WORST_HEIGHT] for the smallest, the height otherwise.
     */
    fun ceilingOf(format: MediaFormat): Int = when (format.formatId) {
        MediaProbe.BEST_VIDEO.formatId -> 0
        WORST_VIDEO.formatId -> WORST_HEIGHT
        else -> format.height
    }

    /**
     * The video closest to [height] without going over, with sound. Expressed as a format
     * sort rather than a filter, so a source with nothing that low still gives its nearest
     * stream instead of its largest.
     */
    fun heightCeiling(height: Int) = MediaFormat(
        formatId = "bv_${height}p",
        selector = "bv*+ba/b",
        sort = "res:$height",
        label = "~${height}p",
        ext = "",
        vcodec = null,
        acodec = null,
        height = height,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = 0L,
        hasVideo = true,
        hasAudio = true,
        isGeneric = true
    )

    /** The video ladder, named with [bestLabel] and [worstLabel] in the user's language. */
    fun videoLadder(bestLabel: String, worstLabel: String): List<MediaFormat> =
        listOf(MediaProbe.BEST_VIDEO.copy(label = bestLabel)) +
            VIDEO_HEIGHTS.map(::heightCeiling) +
            WORST_VIDEO.copy(label = worstLabel)

    /** What the sheet shows for [url] until it has been read: both ladders and no details. */
    fun placeholder(url: String, bestVideo: String, bestAudio: String, worst: String) = MediaInfo(
        url = url,
        title = "",
        uploader = "",
        thumbnail = null,
        durationSeconds = 0,
        videoFormats = videoLadder(bestVideo, worst),
        audioFormats = BatchAudioFormats.ladder(bestAudio, worst)
    )

    /**
     * [info] as its sheet shows it before the read: the ladders in place of the bare best
     * rows, for each kind the link offers, so a quality can be chosen and downloaded while
     * the read runs. A read link is returned as it is, so the real list replaces the ladder.
     */
    fun untilRead(info: MediaInfo, bestVideo: String, bestAudio: String, worst: String): MediaInfo {
        if (info.hasResolvedFormats) return info
        return info.copy(
            videoFormats = if (info.videoFormats.isEmpty()) emptyList() else videoLadder(bestVideo, worst),
            audioFormats = if (info.audioFormats.isEmpty()) emptyList() else BatchAudioFormats.ladder(bestAudio, worst)
        )
    }

    /**
     * [choice], made before [info] was read, as it applies to what [info] turned out to
     * offer: a height becomes the tallest stream under it, best and worst their concrete
     * streams, and a bitrate step stays the expression it is. A concrete format is kept
     * where the link has it. The preferred codecs are taken where the link offers them,
     * as the sheet does for a link read before it opened.
     */
    fun applyTo(
        info: MediaInfo,
        choice: MediaFormat,
        language: String?,
        videoCodec: VideoCodec? = null,
        audioCodec: AudioCodec? = null
    ): MediaFormat? = when {
        !choice.isGeneric ->
            (info.videoFormats + info.audioFormats).firstOrNull { it.formatId == choice.formatId } ?: choice
        choice.hasVideo -> when (choice.formatId) {
            MediaProbe.BEST_VIDEO.formatId -> info.autoPick(true, 0, language, videoCodec = videoCodec)
            WORST_VIDEO.formatId -> info.autoPick(true, WORST_HEIGHT, language, videoCodec = videoCodec)
            else -> info.autoPick(true, choice.height, language, videoCodec = videoCodec)
        } ?: choice
        else -> BatchAudioFormats.pick(info, choice, language, audioCodec) ?: choice
    }
}
