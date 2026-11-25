package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.hazel.android.gallery.queue.GalleryDownloadRequest
import com.hazel.android.gallery.queue.request
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
        assertEquals("https://x.com/i/status/42", items[1].postUrl)
        assertEquals("tumblr:id:7:c", items[2].postKey)
        assertNull(items[2].postUrl)
    }

    @Test
    fun `an error gallery-dl wrote into the listing is kept`() {
        val listing = GalleryListing.parse("""[[-1, {"error": "AuthRequired", "message": "cookies needed"}]]""")
        assertTrue(listing.isEmpty)
        assertEquals("AuthRequired" to "cookies needed", listing.error)
        assertEquals(GalleryDlException.Kind.LOGIN_REQUIRED, GalleryDlException.classifyError("AuthRequired", "cookies needed"))
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
        // Without a link of its own the post's files are fetched by their own addresses.
        assertTrue(posts[2].direct)
        assertEquals(listOf(1), posts[2].items.map { it.index })
    }

    @Test
    fun `a search result without a link never downloads from the search page`() {
        val post = GalleryPost.group("https://site/search?q=x", listOf(file(34, "p1"), file(35, "p1")), feed = true).single()
        val request = post.request(null, null)
        assertNull(request.indexes)
        assertEquals(listOf("https://cdn.example/34.jpg", "https://cdn.example/35.jpg"), request.files)
        assertEquals(request.files, GalleryDownloadRequest.fromPayload(request.toPayload())?.files)
        assertTrue(post.items.all { request.covers(post, it) })
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
