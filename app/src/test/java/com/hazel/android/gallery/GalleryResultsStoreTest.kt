package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryResultsStoreTest {

    @Test
    fun `saved results come back with their posts, links and captions`() {
        val item = GalleryItem(
            index = 1, url = "https://cdn.example/1.jpg", extension = "jpg", title = "t", author = "a",
            site = "site", width = 10, height = 20, thumbnail = null, postKey = "k",
            postUrl = "https://example/p/1", caption = "line one\nline two"
        )
        val saved = GalleryResultsStore.Saved("cats", true, listOf(GalleryPost("k", "https://example/p/1", listOf(item), direct = true)))

        assertEquals(saved, GalleryResultsStore.decode(GalleryResultsStore.encode(saved)))
    }
}
