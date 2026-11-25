package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPostTest {

    @Test
    fun `post key comes from the post link, then ids, then the file id with its author`() {
        val output = """
            [
              [3, "https://cdn.example/1.jpg", {"category": "instagram", "extension": "jpg", "username": "a",
                  "post_url": "https://www.instagram.com/p/AAA/", "post_shortcode": "AAA"}],
              [3, "https://cdn.example/2.jpg", {"category": "twitter", "extension": "jpg", "username": "b",
                  "tweet_id": 42}],
              [3, "https://cdn.example/3.jpg", {"category": "tumblr", "extension": "jpg", "username": "c", "id": 7}]
            ]
        """.trimIndent()

        val items = GalleryListing.parse(output).items

        assertEquals("instagram:post_url:https://www.instagram.com/p/AAA/", items[0].postKey)
        assertEquals("https://www.instagram.com/p/AAA/", items[0].postUrl)
        assertEquals("twitter:tweet_id:42", items[1].postKey)
        assertNull(items[1].postUrl)
        assertEquals("tumblr:id:7:c", items[2].postKey)
    }

    @Test
    fun `one post keeps the source link and its indexes`() {
        val items = listOf(file(1, "p1"), file(2, "p1"))
        val posts = GalleryPost.group("https://site/p1", items)
        assertEquals(1, posts.size)
        assertEquals("https://site/p1", posts[0].source)
        assertEquals(listOf(1, 2), posts[0].items.map { it.index })
    }

    @Test
    fun `several posts split in order, each counted from 1 on its own link`() {
        val items = listOf(
            file(1, "p1", "https://site/p/1"),
            file(2, "p1", "https://site/p/1"),
            file(3, "p2", "https://site/p/2"),
            file(4, "p3")
        )
        val posts = GalleryPost.group("https://site/search?q=x", items)

        assertEquals(listOf("p1", "p2", "p3"), posts.map { it.key })
        assertEquals("https://site/p/1", posts[0].source)
        assertEquals(listOf(1, 2), posts[0].items.map { it.index })
        assertEquals("https://site/p/2", posts[1].source)
        assertEquals(listOf(1), posts[1].items.map { it.index })
        // Without a link of its own the post is saved from the search, at its place there.
        assertEquals("https://site/search?q=x", posts[2].source)
        assertEquals(listOf(4), posts[2].items.map { it.index })
    }

    @Test
    fun `nothing read gives no posts`() {
        assertTrue(GalleryPost.group("https://site/x", emptyList()).isEmpty())
    }

    private fun file(index: Int, post: String, link: String? = null) = GalleryItem(
        index = index, url = "https://cdn.example/$index.jpg", extension = "jpg", title = "", author = "a",
        site = "site", width = 0, height = 0, thumbnail = null, postKey = post, postUrl = link
    )
}
