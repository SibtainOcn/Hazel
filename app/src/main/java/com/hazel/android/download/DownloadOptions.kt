package com.hazel.android.download

import androidx.annotation.StringRes
import com.hazel.android.R

/**
 * Everything the download sheet can adjust before a download starts.
 *
 * These are the knobs yt-dlp is given on top of the chosen format. They persist between
 * downloads, so the sheet always reopens on the settings that were last used.
 */
data class DownloadOptions(
    /** Output container. Blank means "Default": whatever the source already provides. */
    val videoContainer: String = "",
    val audioContainer: String = "",
    /** Bitrate of an audio conversion, such as "192k". Blank leaves it to the encoder. */
    val audioQuality: String = "",

    /** `--embed-thumbnail`: the source's artwork as the file's cover. On unless turned off. */
    val embedThumbnail: Boolean = true,
    /** Crops the embedded cover to a square, the shape music players show. */
    val cropThumbnail: Boolean = false,
    val filenameTemplate: String = DEFAULT_FILENAME_TEMPLATE,

    /** SponsorBlock category ids to cut out, e.g. `sponsor`, `intro`. Empty disables removal. */
    val sponsorBlockFilters: Set<String> = emptySet(),

    /** `--embed-chapters`, plus `--sponsorblock-mark all` so the marks land as chapters. */
    val addChapters: Boolean = true,
    /** `--split-chapters`: one output file per chapter. */
    val splitByChapters: Boolean = false,

    val embedSubs: Boolean = true,
    val writeSubs: Boolean = false,
    val writeAutoSubs: Boolean = false,
    val subLanguages: String = DEFAULT_SUB_LANGUAGES,
    /**
     * With subtitles embedded, the subtitle files are removed once they are in the video.
     * Off, they are kept beside it, which is yt-dlp's `--write-subs` next to `--embed-subs`.
     */
    val deleteSubsAfterEmbed: Boolean = true,

    /** SponsorBlock at all. Off, nothing is cut or marked whatever the categories say. */
    val useSponsorBlock: Boolean = true,
    /** The SponsorBlock server to ask. Blank is the public one, [SponsorBlock.API_URL]. */
    val sponsorBlockApiUrl: String = "",

    // ── Preferences the sheets start from. None of them is passed to yt-dlp as such:
    // they decide which format is picked before the download is asked for. ──

    /** Soundtrack to prefer, as a language tag ("hi", "en"). Blank takes the source's own. */
    val preferredAudioLanguage: String = "",
    /** [AudioCodec] name to prefer. Blank prefers none. */
    val preferredAudioCodec: String = "",
    /** [VideoCodec] name to prefer. Blank prefers none. */
    val preferredVideoCodec: String = "",
    /** Video quality to start from: 0 is best, a height is a ceiling, [WORST_HEIGHT] worst. */
    val videoQuality: Int = 0,
    /** The download sheet opens on its Audio tab rather than Video, where the link has audio. */
    val sheetOpensOnAudio: Boolean = false,

    // ── For one download only. Set from the sheet as the download starts, carried with it
    // in the queue, and never saved as a setting, so the next download does not inherit a
    // cut or a live option meant for this one. ──

    /** Start of the part to download, in seconds; below zero downloads all of it. */
    val sectionStart: Double = -1.0,
    /** End of the part to download, in seconds. */
    val sectionEnd: Double = -1.0,
    /** `--force-keyframes-at-cuts`: exact cut points, at the cost of re-encoding around them. */
    val preciseCuts: Boolean = false,
    /** `--live-from-start`: a stream already live is taken from its beginning, not from now. */
    val liveFromStart: Boolean = false,
    /** `--wait-for-video`: a stream not started yet is waited for, then downloaded. */
    val waitForVideo: Boolean = false
) {
    /** True when only part of the media is to be downloaded. */
    val hasSection: Boolean
        get() = sectionStart >= 0.0 && sectionEnd > sectionStart

    val audioCodecPreference: AudioCodec?
        get() = AudioCodec.entries.firstOrNull { it.name == preferredAudioCodec }

    val videoCodecPreference: VideoCodec?
        get() = VideoCodec.entries.firstOrNull { it.name == preferredVideoCodec }

    /** The SponsorBlock server a request goes to. */
    val sponsorBlockServer: String
        get() = sponsorBlockApiUrl.trim().trimEnd('/').ifBlank { SponsorBlock.API_URL }

    /**
     * Count shown on the Chapters chip badge. Embedding only applies to a video download,
     * so it is left out of the count on the audio tab.
     */
    fun chapterBadge(isVideo: Boolean): Int =
        listOf(addChapters && isVideo, splitByChapters).count { it }

    /** Count shown on the Thumbnail chip badge: cover art on, and cropped. */
    val thumbnailBadge: Int
        get() = listOf(embedThumbnail, embedThumbnail && cropThumbnail).count { it }

    /** Count shown on the SponsorBlock chip badge: the categories cut, while it is on. */
    val sponsorBlockBadge: Int
        get() = if (useSponsorBlock) sponsorBlockFilters.size else 0

    /** Count shown on the Subtitles chip badge. */
    val subtitleBadge: Int
        get() = listOf(embedSubs, writeSubs, writeAutoSubs).count { it }

    /** These options with a download's own choices laid over them. */
    fun with(oneOff: OneOffOptions): DownloadOptions = copy(
        sectionStart = if (oneOff.hasSection) oneOff.sectionStart else -1.0,
        sectionEnd = if (oneOff.hasSection) oneOff.sectionEnd else -1.0,
        preciseCuts = oneOff.hasSection && oneOff.preciseCuts,
        liveFromStart = oneOff.liveFromStart,
        waitForVideo = oneOff.waitForVideo
    )

    companion object {
        const val DEFAULT_FILENAME_TEMPLATE = "%(title)s.%(ext)s"
        /**
         * English as published, and the video's own language as captioned. Not "en.*": that
         * also takes every machine translation into or out of English, each one a request,
         * and a single one refused (YouTube answers a burst with 429) fails the whole
         * download, since yt-dlp treats a subtitle it could not fetch as an error.
         */
        const val DEFAULT_SUB_LANGUAGES = "en,en-US,en-GB,en-IN,en-CA,en-AU,.*-orig"

        /** The default before it was narrowed, still stored for anyone who never changed it. */
        const val LEGACY_SUB_LANGUAGES = "en.*,.*-orig"
    }
}

/**
 * What the sheet sets for one download and nothing after it: a part of the media to cut out
 * and keep, and how to take a live stream.
 */
data class OneOffOptions(
    val sectionStart: Double = -1.0,
    val sectionEnd: Double = -1.0,
    val preciseCuts: Boolean = false,
    val liveFromStart: Boolean = false,
    val waitForVideo: Boolean = false
) {
    val hasSection: Boolean
        get() = sectionStart >= 0.0 && sectionEnd > sectionStart
}

/**
 * A time as the cut fields show it: "1:02:03.250" or "2:03.250", the milliseconds only
 * when there are any.
 */
fun formatTimestamp(seconds: Double): String {
    val totalMillis = Math.round(seconds.coerceAtLeast(0.0) * 1000)
    val h = totalMillis / 3_600_000
    val m = (totalMillis % 3_600_000) / 60_000
    val s = (totalMillis % 60_000) / 1000
    val ms = totalMillis % 1000
    val clock = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return if (ms > 0) "%s.%03d".format(clock, ms) else clock
}

/**
 * Reads a time typed into a cut field: seconds ("75.5"), or minutes and seconds ("1:15.5"),
 * or hours as well ("1:02:03"). Null for anything else.
 */
fun parseTimestamp(text: String): Double? {
    val parts = text.trim().split(':')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isBlank() }) return null
    val seconds = parts.last().toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    val whole = parts.dropLast(1).map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return null }
    if (whole.isNotEmpty() && seconds >= 60) return null
    if (whole.isEmpty()) return seconds
    var total = 0.0
    for (part in whole) total = total * 60 + part
    return total * 60 + seconds
}

/**
 * The SponsorBlock category list.
 *
 * Nothing here talks to the SponsorBlock service directly: the ids are passed to yt-dlp as
 * `--sponsorblock-remove` / `--sponsorblock-mark`, and yt-dlp is what queries the API and
 * keeps up with it. That means the feature follows whatever yt-dlp build the app is running,
 * so keeping yt-dlp current, which the in-app updater already does, is the whole of the
 * maintenance story. [API_URL] exists only so a self-hosted mirror can be pointed at.
 */
object SponsorBlock {

    const val API_URL = "https://sponsor.ajay.app"

    /** id, as yt-dlp names it, paired with the label shown in the picker. */
    val CATEGORIES: List<Pair<String, String>> = listOf(
        "music_offtopic" to "Non-music and off-topic portions",
        "sponsor" to "Sponsors",
        "intro" to "Intro",
        "outro" to "Outro",
        "selfpromo" to "Self promos",
        "preview" to "Previews",
        "filler" to "Fillers",
        "interaction" to "Subscription reminders",
        "hook" to "Hook/Greetings"
    )
}

/** Containers offered for a video download. The first entry means "leave it alone". */
val VIDEO_CONTAINERS = listOf("Default", "mp4", "webm", "mkv", "mov", "avi", "flv")

/** Containers offered for an audio download. */
val AUDIO_CONTAINERS =
    listOf("Default", "mp3", "m4a", "aac", "alac", "flac", "opus", "wav", "vorbis")

/**
 * Bitrates offered for yt-dlp's `--audio-quality`, which sets the bitrate of a conversion.
 * Blank leaves it to the encoder. A download kept in the format it arrived in is not
 * re-encoded, so the setting only matters alongside a different output format.
 */
val AUDIO_QUALITY_STEPS: List<Pair<String, Int>> = listOf(
    "" to R.string.audio_quality_best,
    "320k" to R.string.audio_quality_320kbps,
    "256k" to R.string.audio_quality_256kbps,
    "192k" to R.string.audio_quality_192kbps,
    "128k" to R.string.audio_quality_128kbps,
    "64k" to R.string.audio_quality_64kbps
)

/**
 * How hard yt-dlp tries when reading a link.
 *
 * Reading metadata is the slowest visible step, and almost all of that time is network
 * waiting. These settings control how long a stalled connection is given before it is
 * abandoned and how many times a failed attempt is repeated. They apply to every site
 * equally: nothing here is specific to one extractor.
 */
enum class FetchMode(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val socketTimeoutSeconds: Int,
    val retries: Int
) {
    FAST(
        labelRes = R.string.fetch_mode_fast_label,
        descriptionRes = R.string.fetch_mode_fast_description,
        socketTimeoutSeconds = 5,
        retries = 1
    ),
    BALANCED(
        labelRes = R.string.fetch_mode_balanced_label,
        descriptionRes = R.string.fetch_mode_balanced_description,
        socketTimeoutSeconds = 10,
        retries = 3
    ),
    THOROUGH(
        labelRes = R.string.fetch_mode_thorough_label,
        descriptionRes = R.string.fetch_mode_thorough_description,
        socketTimeoutSeconds = 20,
        retries = 10
    );

    companion object {
        /**
         * Balanced is the default. Fast gives a cold start too little room: the first read
         * of a link has to fetch player data before anything else, and cutting that off
         * after five seconds makes a link that works perfectly well look unreadable.
         */
        val DEFAULT = BALANCED

        fun fromName(name: String?): FetchMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
