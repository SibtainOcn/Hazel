package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryListingTest {

    @Test
    fun `reads files in range order with type, author and title`() {
        val output = """
            [
              [2, {"category": "instagram"}],
              [3, "https://cdn.example/a.jpg", {"category": "instagram", "extension": "jpg",
                  "username": "spacebrief", "description": "First line\nsecond", "width": 1080, "height": 1350}],
              [3, "https://cdn.example/b.mp4", {"category": "instagram", "extension": "mp4",
                  "username": "spacebrief", "display_url": "https://cdn.example/b.jpg"}]
            ]
        """.trimIndent()

        val listing = GalleryListing.parse(output)

        assertEquals(2, listing.items.size)
        val image = listing.items[0]
        assertEquals(1, image.index)
        assertEquals("spacebrief", image.author)
        assertEquals("First line", image.title)
        assertEquals("https://cdn.example/a.jpg", image.thumbnail)
        assertFalse(image.isVideo)
        val video = listing.items[1]
        assertEquals(2, video.index)
        assertTrue(video.isVideo)
        assertEquals("https://cdn.example/b.jpg", video.thumbnail)
    }

    @Test
    fun `nested author object and queued posts are read`() {
        val output = """
            [
              [3, "https://pbs.example/x.png", {"extension": "png", "author": {"name": "nasa"}, "content": "Launch"}],
              [6, "https://x.com/nasa/status/1", {}],
              [6, "not a link", {}]
            ]
        """.trimIndent()

        val listing = GalleryListing.parse(output)

        assertEquals("nasa", listing.items.single().author)
        assertEquals(listOf("https://x.com/nasa/status/1"), listing.posts)
    }

    @Test
    fun `empty listing is empty`() {
        assertTrue(GalleryListing.parse("[]").isEmpty)
    }

    @Test(expected = GalleryDlException::class)
    fun `unreadable output fails as a gallery error`() {
        GalleryListing.parse("not json")
    }

    @Test
    fun `error lines sort into kinds`() {
        assertEquals(GalleryDlException.Kind.LOGIN_REQUIRED, GalleryDlException.classify("AuthorizationError: Login required"))
        assertEquals(GalleryDlException.Kind.UNSUPPORTED, GalleryDlException.classify("Unsupported URL 'https://example.com'"))
        assertEquals(GalleryDlException.Kind.NOT_FOUND, GalleryDlException.classify("HttpError: '404 Not Found'"))
        assertEquals(GalleryDlException.Kind.NETWORK, GalleryDlException.classify("ConnectionError: Max retries exceeded"))
        assertEquals(GalleryDlException.Kind.OTHER, GalleryDlException.classify("Something odd"))
    }
}
