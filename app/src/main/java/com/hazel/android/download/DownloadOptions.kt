package com.hazel.android.download

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

    val embedThumbnail: Boolean = false,
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
    val subLanguages: String = DEFAULT_SUB_LANGUAGES
) {
    /**
     * Count shown on the Chapters chip badge. Embedding only applies to a video download,
     * so it is left out of the count on the audio tab.
     */
    fun chapterBadge(isVideo: Boolean): Int =
        listOf(addChapters && isVideo, splitByChapters).count { it }

    /** Count shown on the Subtitles chip badge. */
    val subtitleBadge: Int
        get() = listOf(embedSubs, writeSubs, writeAutoSubs).count { it }

    companion object {
        const val DEFAULT_FILENAME_TEMPLATE = "%(title)s.%(ext)s"
        const val DEFAULT_SUB_LANGUAGES = "en.*,.*-orig"
    }
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
 * How hard yt-dlp tries when reading a link.
 *
 * Reading metadata is the slowest visible step, and almost all of that time is network
 * waiting. These settings control how long a stalled connection is given before it is
 * abandoned and how many times a failed attempt is repeated. They apply to every site
 * equally: nothing here is specific to one extractor.
 */
enum class FetchMode(
    val label: String,
    val description: String,
    val socketTimeoutSeconds: Int,
    val retries: Int
) {
    FAST(
        label = "Fast",
        description = "Give up quickly. Fastest on a good network, but more likely to " +
                "need a second attempt.",
        socketTimeoutSeconds = 5,
        retries = 1
    ),
    BALANCED(
        label = "Balanced",
        description = "A middle ground between speed and tolerating a poor connection.",
        socketTimeoutSeconds = 10,
        retries = 3
    ),
    THOROUGH(
        label = "Thorough",
        description = "Wait longer and retry more. Slowest, but survives a weak network.",
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
