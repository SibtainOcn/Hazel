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
                  "username": "spacebrief", "display_url": "https://cdn.example/b.jpg", "title": "", "seo_alt_text": "a cat"}]
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
        assertEquals("a cat", video.title)
    }



    @Test
    fun `a video handed to yt-dlp plays from the site's own file`() {
        val output = """
            [[3, "ytdl:https://www.instagram.com/stories/user/1.mp4",
              {"extension": "mp4", "video_url": "https://cdn.example/story.mp4"}]]
        """
        assertEquals("https://cdn.example/story.mp4", GalleryListing.parse(output).items.single().playUrl)
    }



    @Test(expected = GalleryDlException::class)
    fun `unreadable output fails as a gallery error`() {
        GalleryListing.parse("not json")
    }


}
