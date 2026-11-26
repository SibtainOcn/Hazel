package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileLinksTest {

    private fun video(url: String, thumb: String, play: String?) = GalleryItem(
        index = 1, url = url, extension = "mp4", title = "", author = "", site = "instagram",
        width = 0, height = 0, thumbnail = thumb, playUrl = play
    )

    @Test
    fun staleLinksAndFreshMatchByFileName() {
        val now = 0x6900_0000L
        assertTrue(FileLinks.isStale("https://cdn/v.mp4?a=1&oe=${(now - 1).toString(16)}&b=2", now))
        assertFalse(FileLinks.isStale("https://cdn/v.mp4?oe=${(now + 3600).toString(16)}", now))
        assertFalse(FileLinks.isStale("https://cdn/v.mp4", now))

        val old = listOf(
            video("ytdl:https://www.instagram.com/stories/u/1.mp4", "https://cdn/a.jpg?sig=1", null),
            video("ytdl:https://www.instagram.com/stories/u/2.mp4", "https://cdn/b.jpg?sig=1", "https://cdn/old.mp4")
        )
        // A new story landed first, so positions moved; file names still match.
        val listed = listOf(
            video("ytdl:https://www.instagram.com/stories/u/1.mp4", "https://cdn/new.jpg?sig=2", "https://cdn/n.mp4"),
            video("ytdl:https://www.instagram.com/stories/u/2.mp4", "https://cdn/a.jpg?sig=2", "https://cdn/a.mp4"),
            video("ytdl:https://www.instagram.com/stories/u/3.mp4", "https://cdn/b.jpg?sig=2", "https://cdn/b.mp4")
        )
        val fresh = FileLinks.freshLinks(old, listed)
        assertEquals("https://cdn/a.mp4", fresh[old[0].url])
        assertEquals("https://cdn/b.mp4", fresh[old[1].url])

        assertNull(old[0].ytdlPage)
    }
}
