package com.hazel.android.download.extractor

import com.hazel.android.download.MediaInfo

/**
 * One item found behind a link, before anything is known about its formats.
 *
 * This is what a listing pass can report cheaply: enough to draw a card, and the address to
 * resolve properly later. Formats are deliberately absent, because reading them costs a
 * request per item and most items in a long playlist are never opened.
 */
data class LinkEntry(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSeconds: Int
)

/**
 * What a pasted link turned out to be.
 *
 * The distinction is made by asking the extractor rather than by matching the address
 * against a list of known shapes. A playlist, a channel, an album, a watch-later page and a
 * multi-part post all differ in the URL and are all the same question: does this address
 * hold one thing or several? Only the extractor can answer that, and answering it this way
 * means a source nobody thought about still works.
 */
sealed interface LinkContents {

    /**
     * One playable item, already resolved.
     *
     * The listing pass returns the full metadata for a single item rather than just its
     * address, because that is what the pass already had in hand: asking a source whether a
     * link is a collection reads the whole item when it is not one. Handing it back saves
     * reading the same link twice.
     */
    data class Single(val info: MediaInfo) : LinkContents

    /** Several items. [title] names the collection where the source reported one. */
    data class Many(val title: String, val entries: List<LinkEntry>) : LinkContents
}
