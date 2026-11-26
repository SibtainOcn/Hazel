package com.hazel.android.ui.screens.gallery

import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost

/**
 * Several posts listed as one Download all, such as a profile's chosen sections: files are
 * numbered 1..n across them, and each maps back to its own post and number, so every download
 * goes to the right link with exactly the files that were shown.
 */
internal class MergedPosts(val posts: List<GalleryPost>) {

    private val origins: List<Pair<GalleryPost, GalleryItem>> = posts.flatMap { post -> post.items.map { post to it } }

    val items: List<GalleryItem> = origins.mapIndexed { position, (_, item) -> item.copy(index = position + 1) }

    /** The post and the file as listed there, for a file of [items]. */
    fun originOf(item: GalleryItem): Pair<GalleryPost, GalleryItem>? = origins.getOrNull(item.index - 1)

    /**
     * [picks] split back into one download per post. Files are always named by number, never
     * as a whole link, since a section read with a limit holds more than was shown.
     */
    fun <R> split(picks: List<GalleryPick>, build: (post: GalleryPost, indexes: List<Int>, pick: GalleryPick) -> R): List<R> =
        picks.flatMap { pick ->
            val chosen = pick.indexes?.mapNotNull { origins.getOrNull(it - 1) } ?: origins
            chosen.groupBy({ it.first }, { it.second.index }).map { (post, indexes) -> build(post, indexes, pick) }
        }
}
