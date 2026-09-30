package com.hazel.android.download

/**
 * The audio formats a set of links can be given in one choice, and how that choice lands on
 * each link.
 *
 * Links in a set come from any number of sources and most have not had their formats read,
 * so the list a set is offered is a ladder of engine expressions that every source can
 * answer without being read first: best audio, a bitrate to stay under, worst audio. Each
 * link's download resolves the expression against whatever that link turns out to offer.
 *
 * Where every link in the set has already been read, the concrete formats they all share
 * are offered beneath the ladder as well, which is the usual case for a playlist from one
 * site after the formats have been updated.
 */
object BatchAudioFormats {

    /** The generic "best audio" row every source can satisfy. */
    val BEST: MediaFormat get() = MediaProbe.BEST_AUDIO

    /** Bitrates the ladder offers, highest first, in kbps. */
    val BITRATE_STEPS = listOf(192, 160, 128, 96, 64)

    /** The engine's own smallest audio. */
    val WORST = MediaFormat(
        formatId = "worstaudio",
        selector = "wa/w",
        label = "Worst quality",
        ext = "",
        vcodec = null,
        acodec = null,
        height = 0,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = 0L,
        hasVideo = false,
        hasAudio = true,
        isGeneric = true
    )

    /**
     * The audio closest to [kbps] without going over, by format sort, so a source with
     * nothing that low still gives its nearest stream. A source that labels no bitrate
     * downloads its best audio.
     */
    fun bitrateCeiling(kbps: Int) = MediaFormat(
        formatId = "ba_${kbps}k",
        selector = "ba/b",
        sort = "abr:$kbps",
        label = "~$kbps kbps",
        ext = "",
        vcodec = null,
        acodec = null,
        height = 0,
        fps = 0,
        bitrateKbps = kbps.toDouble(),
        fileSizeBytes = 0L,
        hasVideo = false,
        hasAudio = true,
        isGeneric = true
    )

    /** The ladder, named with [bestLabel] and [worstLabel] in the user's language. */
    fun ladder(bestLabel: String, worstLabel: String): List<MediaFormat> =
        listOf(BEST.copy(label = bestLabel)) +
            BITRATE_STEPS.map(::bitrateCeiling) +
            WORST.copy(label = worstLabel)

    /** What the format sheet offers for [targets]: the ladder, then any formats all share. */
    fun choices(targets: List<MediaInfo>, bestLabel: String, worstLabel: String): List<MediaFormat> =
        ladder(bestLabel, worstLabel) + sharedFormats(targets)

    /**
     * The concrete formats every one of [targets] offers, each sized for the whole set.
     * Empty until every link has been read, since a format only some links were checked for
     * might not exist on the rest.
     */
    fun sharedFormats(targets: List<MediaInfo>): List<MediaFormat> {
        if (targets.isEmpty() || targets.any { !it.hasResolvedFormats }) return emptyList()

        val formatsPerLink = targets.map { info ->
            info.audioFormats.filterNot { it.isGeneric }.associateBy { it.formatId }
        }
        if (formatsPerLink.any { it.isEmpty() }) return emptyList()

        val sharedIds = formatsPerLink.map { it.keys }.reduce { shared, ids -> shared intersect ids }
        return formatsPerLink.first().values
            .filter { it.formatId in sharedIds }
            .map { format ->
                val sameFormat = formatsPerLink.mapNotNull { it[format.formatId] }
                format.copy(
                    fileSizeBytes = sameFormat.sumOf { it.fileSizeBytes },
                    isEstimatedSize = sameFormat.any { it.isEstimatedSize }
                )
            }
    }

    /**
     * The format [info] downloads with when the set's audio choice is [choice], in
     * [language] where the source has one. Null [choice] is best audio.
     *
     * A ladder row is handed on as it is and resolved by the engine at download time. A
     * concrete format lands on the same id; a link that lacks it takes its best audio.
     */
    fun pick(info: MediaInfo, choice: MediaFormat?, language: String?): MediaFormat? = when {
        choice == null || choice.formatId == BEST.formatId -> info.bestAudioFor(language)
        choice.isGeneric -> choice
        else -> info.audioFormats.firstOrNull { !it.isGeneric && it.formatId == choice.formatId }
            ?: info.bestAudioFor(language)
    }
}
