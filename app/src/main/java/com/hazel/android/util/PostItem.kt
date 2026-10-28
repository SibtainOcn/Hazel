package com.hazel.android.util

/**
 * One item of a post that holds several, such as a tweet with two videos.
 *
 * The engine gives these items no address of their own: each carries the post's. Downloading
 * that address takes the whole post, so the item's place in it is written onto the address
 * as a fragment. Everything that keys on links then tells the items apart, and the fragment
 * is taken off again, as `--playlist-items`, before the engine is asked.
 */
object PostItem {

    private const val MARK = "#hazel-item="

    /** [url] narrowed to its [index]th item, counted from 1. */
    fun of(url: String, index: Int): String = address(url) + MARK + index

    /** The item [url] names, or null for a link that names none. */
    fun indexOf(url: String): Int? =
        url.substringAfter(MARK, "").toIntOrNull()?.takeIf { it > 0 }

    /** [url] without its item, which is the address the engine is given. */
    fun address(url: String): String = url.substringBefore(MARK)
}
