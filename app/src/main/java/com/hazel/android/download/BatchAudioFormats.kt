package com.hazel.android.download

/**
 * The audio formats a set of links can be given in one choice, and how that choice lands on
 * each link.
 *
 * Links in a set come from any number of sources and each reports its own format list, so a
 * choice made once for all of them has to mean something for every one. Three kinds do:
 *
 * - Best audio, which every source has.
 * - A codec, such as Opus or AAC, offered when at least one link carries it. A link without
 *   it takes its best of any codec rather than failing.
 * - A concrete format id, offered only when every link read so far carries it, which is the
 *   usual case for a playlist from one site. Its size is the whole set's.
 */
object BatchAudioFormats {

    /** The generic "best audio" row every source can satisfy. */
    val BEST: MediaFormat get() = MediaProbe.BEST_AUDIO

    /** What the format sheet offers for [targets], best first. */
    fun choices(targets: List<MediaInfo>): List<MediaFormat> {
        val formatsPerLink = targets
            .filter { it.hasResolvedFormats }
            .map { info -> info.audioFormats.filterNot { it.isGeneric }.associateBy { it.formatId } }
            .filter { it.isNotEmpty() }

        if (formatsPerLink.isEmpty()) return listOf(BEST)

        val codecs = AudioCodec.entries.filter { codec ->
            formatsPerLink.any { formats -> formats.values.any(codec::matches) }
        }

        val sharedIds = formatsPerLink
            .map { it.keys }
            .reduce { shared, ids -> shared intersect ids }
        val everyLinkRead = formatsPerLink.size == targets.size

        val shared = formatsPerLink.first().values
            .filter { it.formatId in sharedIds }
            .map { format ->
                val sameFormat = formatsPerLink.mapNotNull { it[format.formatId] }
                format.copy(
                    fileSizeBytes = sameFormat.sumOf { it.fileSizeBytes },
                    // A figure covering only the links read so far is a floor, not a total.
                    isEstimatedSize = !everyLinkRead || sameFormat.any { it.isEstimatedSize }
                )
            }

        return listOf(BEST) + codecs.map { it.genericFormat() } + shared
    }

    /**
     * The format [info] downloads with when the set's audio choice is [choice], in
     * [language] where the source has one. Null [choice] is best audio.
     */
    fun pick(info: MediaInfo, choice: MediaFormat?, language: String?): MediaFormat? {
        if (choice == null || choice.formatId == BEST.formatId) return info.bestAudioFor(language)
        if (!choice.isGeneric) {
            info.audioFormats
                .firstOrNull { !it.isGeneric && it.formatId == choice.formatId }
                ?.let { return it }
        }
        return info.bestAudioFor(language, AudioCodec.of(choice))
    }
}
