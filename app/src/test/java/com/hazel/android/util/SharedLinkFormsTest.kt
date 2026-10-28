package com.hazel.android.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Two link forms that made Instant download the wrong thing: a YouTube video shared from
 * inside a Mix, which was read as the endless Mix, and the videos of one post, which all
 * carried the post's address and each downloaded the whole post.
 */
class SharedLinkFormsTest {

    @Test
    fun `a video shared from a Mix is that video alone`() {
        assertEquals(
            "https://m.youtube.com/watch?v=EU1KWuGWy1o",
            UrlExtractor.youTubeVideoOnly(
                "https://m.youtube.com/watch?v=EU1KWuGWy1o&list=RDEU1KWuGWy1o&start_radio=1&pp=ygULRmxhdmlhIHRhc2igBwE%3D"
            )
        )
    }

    @Test
    fun `a video from a playlist keeps its other parameters`() {
        assertEquals(
            "https://www.youtube.com/watch?v=kUox2TPnpzo&t=42",
            UrlExtractor.youTubeVideoOnly("https://www.youtube.com/watch?v=kUox2TPnpzo&list=PL123&index=4&t=42")
        )
        assertEquals(
            "https://youtu.be/kUox2TPnpzo?si=abc",
            UrlExtractor.youTubeVideoOnly("https://youtu.be/kUox2TPnpzo?si=abc&list=PL123")
        )
    }

    @Test
    fun `playlists, plain videos and other sites are left alone`() {
        listOf(
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/watch?v=kUox2TPnpzo",
            "https://example.com/watch?v=kUox2TPnpzo&list=PL123",
            "https://x.com/user/status/1578353380363501568"
        ).forEach { assertEquals(it, UrlExtractor.youTubeVideoOnly(it)) }
    }

    @Test
    fun `an item of a post keeps its place and gives back the post's address`() {
        val post = "https://x.com/DavidToons_/status/1578353380363501568"
        val second = PostItem.of(post, 2)
        assertEquals(2, PostItem.indexOf(second))
        assertEquals(post, PostItem.address(second))
        assertEquals(PostItem.of(post, 3), PostItem.of(second, 3))
        assertNull(PostItem.indexOf(post))
        assertEquals(post, PostItem.address(post))
    }

    @Test
    fun `the items of one post are different media, and each is its own read`() {
        val post = "https://x.com/DavidToons_/status/1578353380363501568"
        val first = PostItem.of(post, 1)
        val second = PostItem.of(post, 2)
        assertNotEquals(LinkKey.canonical(first), LinkKey.canonical(second))
        assertNotEquals(LinkKey.readKey(first), LinkKey.readKey(second))
        assertNotEquals(LinkKey.digest(first), LinkKey.digest(second))
        assertNotEquals(LinkKey.canonical(post), LinkKey.canonical(first))
        assertEquals(
            LinkKey.canonical(PostItem.of("https://x.com/DavidToons_/status/1578353380363501568?s=20", 2)),
            LinkKey.canonical(second)
        )
    }
}
