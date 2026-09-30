package com.hazel.android.download

import androidx.annotation.StringRes
import com.hazel.android.R

/**
 * The stages a download goes through, in the order yt-dlp runs them (its
 * `get_postprocessors` order, 2026.08): the streams are fetched and merged, then the audio
 * is extracted, the file remuxed, subtitles embedded, SponsorBlock segments cut, tags
 * written, the cover embedded and the chapters split, and last the app saves the file.
 *
 * [marker] is the tag yt-dlp prints when it starts that stage, which is how the card knows
 * where a download has got to. A stage the engine decides it has nothing to do for prints
 * nothing, and the card simply moves past it when a later one starts.
 */
enum class ProcessingStep(@StringRes val label: Int, val marker: String?) {
    FETCH(R.string.processing_step_fetch, null),
    MERGE(R.string.processing_step_merge, "[merger]"),
    EXTRACT(R.string.processing_step_extract, "[extractaudio]"),
    REMUX(R.string.processing_step_remux, "[videoremuxer]"),
    SUBTITLES(R.string.processing_step_subtitles, "[embedsubtitle]"),
    CUT(R.string.processing_step_cut, "[modifychapters]"),
    TAGS(R.string.processing_step_tags, "[metadata]"),
    COVER(R.string.processing_step_cover, "[embedthumbnail]"),
    SPLIT(R.string.processing_step_split, "[splitchapters]"),
    SAVE(R.string.processing_step_save, null);

    companion object {
        /** The stage a line of yt-dlp output announces, or null for any other line. */
        fun announcedBy(line: String): ProcessingStep? {
            val lower = line.lowercase()
            return entries.firstOrNull { step -> step.marker?.let { it in lower } == true }
        }
    }
}
