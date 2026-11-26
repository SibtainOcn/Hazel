package com.hazel.android.ui.screens.gallery

import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryVideoRoutingTest {

    private fun video(index: Int, post: String) = GalleryItem(
        index = index, url = "ytdl:$post$index.mp4", extension = "mp4", title = "", author = "news", site = "instagram",
        width = 720, height = 1280, thumbnail = null, postUrl = post, playUrl = "https://cdn/$index.mp4"
    )

    @Test
    fun onlyAOneVideoPostIsReadByItsPage() {
        val reel = "https://www.instagram.com/reel/abc/"
        assertEquals(reel, ownPage(GalleryPost(reel, reel, listOf(video(1, reel))), video(1, reel)))

        val story = "https://www.instagram.com/stories/news/"
        assertNull(ownPage(GalleryPost(story, story, listOf(video(1, story))), video(1, story)))

        val carousel = "https://www.instagram.com/p/xyz/"
        val items = listOf(video(1, carousel), video(2, carousel))
        assertNull(ownPage(GalleryPost(carousel, carousel, items), items[1]))

        val info = fileInfo(items[1], "https://cdn/2.mp4", VideoLabels("Best", "Best", "Worst"))
        assertEquals("https://cdn/2.mp4", info.url)
        assertEquals(carousel, info.pageUrl)
        assertEquals("news 2", info.title)
    }
}
