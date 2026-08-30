package com.hazel.android.converter

/**
 * What a converted file can come out as.
 *
 * The three at the top are the three anybody actually wants, in the order the choice
 * usually goes: the one that sounds best for its size, the one that sounds nearly as good
 * and plays on more things, and the one that plays on everything ever made. The rest are
 * there because somebody always needs one of them, not because they are worth scrolling
 * past on the way to MP3.
 */
data class AudioFormat(
    /** Handed to yt-dlp as `--audio-format`. */
    val id: String,
    /** What the row is headed with. */
    val name: String,
    /** Shown beside the name. The engine has the final say on the actual extension. */
    val extension: String,
    /** One line saying what choosing this gets you. */
    val summary: String,
    val tags: List<AudioTag>
)

/**
 * A word on a format, so the list can be read without knowing what any of them mean.
 *
 * Quality and size are the two things being traded, so the tags name one or the other and
 * never both at once. A format is at most two of these.
 */
enum class AudioTag(val label: String) {
    BEST("Best"),
    RECOMMENDED("Recommended"),
    COMPATIBLE("Most compatible"),
    NORMAL("Normal"),
    SMALL("Small file"),
    LOSSLESS("Lossless"),
    BIG("Big file"),
    BIGGEST("Biggest file")
}

object AudioFormats {

    val OPUS = AudioFormat(
        id = "opus",
        name = "Opus",
        extension = "opus",
        summary = "Best quality for the size. Needs a recent player.",
        tags = listOf(AudioTag.BEST, AudioTag.SMALL)
    )

    val AAC = AudioFormat(
        id = "aac",
        name = "AAC",
        extension = "aac",
        summary = "Nearly as good as Opus, and far more players take it.",
        tags = listOf(AudioTag.RECOMMENDED)
    )

    val MP3 = AudioFormat(
        id = "mp3",
        name = "MP3",
        extension = "mp3",
        summary = "Plays on anything, at the cost of some quality.",
        tags = listOf(AudioTag.COMPATIBLE, AudioTag.NORMAL)
    )

    /**
     * The default, and deliberately not the one tagged Best.
     *
     * Opus in a `.opus` file is only playable out of the box from Android 10, so on the
     * older phones this app still supports it would convert perfectly and then not play.
     * MP3 plays on every one of them.
     */
    val DEFAULT = MP3

    val all: List<AudioFormat> = listOf(
        OPUS,
        AAC,
        MP3,
        AudioFormat(
            id = "m4a",
            name = "M4A",
            extension = "m4a",
            summary = "The same AAC audio, in the container Apple devices expect.",
            tags = listOf(AudioTag.NORMAL)
        ),
        AudioFormat(
            id = "vorbis",
            name = "Vorbis",
            extension = "ogg",
            summary = "Open format, quality between MP3 and Opus.",
            tags = listOf(AudioTag.NORMAL)
        ),
        AudioFormat(
            id = "flac",
            name = "FLAC",
            extension = "flac",
            summary = "Every bit of the original audio kept, compressed losslessly.",
            tags = listOf(AudioTag.LOSSLESS, AudioTag.BIG)
        ),
        AudioFormat(
            id = "alac",
            name = "ALAC",
            extension = "m4a",
            summary = "Apple's lossless format. Same idea as FLAC, different container.",
            tags = listOf(AudioTag.LOSSLESS, AudioTag.BIG)
        ),
        AudioFormat(
            id = "wav",
            name = "WAV",
            extension = "wav",
            summary = "No compression at all. Perfect, and enormous.",
            tags = listOf(AudioTag.LOSSLESS, AudioTag.BIGGEST)
        )
    )

    fun byId(id: String): AudioFormat = all.firstOrNull { it.id == id } ?: DEFAULT
}
