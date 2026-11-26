package com.hazel.android.ui.screens.gallery

import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryPost
import org.junit.Assert.assertEquals
import org.junit.Test

class MergedPostsTest {

    private fun file(index: Int) = GalleryItem(index, "https://cdn/$index.jpg", "jpg", "", "u", "instagram", 0, 0, null)

    @Test
    fun picksGoBackToTheirOwnSectionByNumber() {
        val stories = GalleryPost("s", "https://www.instagram.com/stories/u/", listOf(file(1), file(2)))
        val reels = GalleryPost("r", "https://www.instagram.com/u/reels/", listOf(file(1), file(2), file(3)))
        val merged = MergedPosts(listOf(stories, reels))
        assertEquals(listOf(1, 2, 3, 4, 5), merged.items.map { it.index })

        // The whole list is always named by number, since a section read with a limit holds more.
        val all = merged.split(listOf(GalleryPick(null, GalleryOptions()))) { post, indexes, _ -> post.source to indexes }
        assertEquals(listOf(stories.source to listOf(1, 2), reels.source to listOf(1, 2, 3)), all)

        val some = merged.split(listOf(GalleryPick(listOf(2, 4), GalleryOptions()))) { post, indexes, _ -> post.source to indexes }
        assertEquals(listOf(stories.source to listOf(2), reels.source to listOf(2)), some)
    }
}
