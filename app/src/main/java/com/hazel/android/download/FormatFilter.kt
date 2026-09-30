package com.hazel.android.download

import androidx.annotation.StringRes
import com.hazel.android.R

/**
 * Which of a link's formats the format list shows.
 *
 * Sources differ wildly in how much they report: one site gives a single file, another a
 * ladder of forty streams in near-duplicate pairs. The filters narrow that to what a person
 * choosing is likely to want, and work on whatever a source reported rather than on any
 * one site's format ids.
 *
 * Every filter keeps the list's order, which is best first.
 */
enum class FormatFilter(@StringRes val labelRes: Int) {

    /** Everything the source reported. */
    ALL(R.string.format_filter_all),

    /**
     * The best stream of each kind: one per resolution and frame rate for video, one per
     * codec for audio, and one per soundtrack where a source has several. Loudness
     * normalised duplicates are left out, since they are the same stream re-mastered.
     */
    SUGGESTED(R.string.format_filter_suggested),

    /**
     * The smallest file of each resolution, or of each quality level for audio. Streams of
     * unknown size cannot be compared and are left out, as are loudness duplicates.
     */
    SMALLEST(R.string.format_filter_smallest),

    /** Only the "best available" rows, which let the engine decide at download time. */
    GENERIC(R.string.format_filter_generic);

    /** [formats] narrowed by this filter. [audio] says which kind of list they are. */
    fun apply(formats: List<MediaFormat>, audio: Boolean): List<MediaFormat> {
        val (generic, concrete) = formats.partition { it.isGeneric }
        return when (this) {
            ALL -> formats
            GENERIC -> generic.ifEmpty {
                listOf(if (audio) MediaProbe.BEST_AUDIO else MediaProbe.BEST_VIDEO)
            }
            SUGGESTED -> generic + concrete
                .filterNot { it.isLoudnessDuplicate }
                .distinctBy { it.kindKey(audio) }
            SMALLEST -> generic + concrete
                .filter { it.fileSizeBytes > 0 && !it.isLoudnessDuplicate }
                .groupBy { it.levelKey(audio) }
                .values
                .map { group -> group.minBy { it.fileSizeBytes } }
        }
    }

    private companion object {
        /** YouTube's dynamic range compressed copies, marked in the id and the note. */
        val MediaFormat.isLoudnessDuplicate: Boolean
            get() = formatId.endsWith("-drc", ignoreCase = true) ||
                label.contains("drc", ignoreCase = true)

        /** What makes two streams different choices rather than two sizes of one. */
        fun MediaFormat.kindKey(audio: Boolean): String =
            if (audio) "${AudioCodec.of(this)?.name ?: codecLabel.ifBlank { ext }}|$language"
            else "$height|${if (fps > 30) "high" else "standard"}|$language"

        /** The quality level a stream sits at: its resolution, or its note for audio. */
        fun MediaFormat.levelKey(audio: Boolean): String =
            if (audio) "${shortLabel.lowercase()}|$language" else "$height|$language"
    }
}
