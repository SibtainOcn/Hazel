package com.hazel.android.gallery

import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.gallery.queue.GalleryDownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryQueueRecordsTest {

    @Test
    fun `a failed gallery job retries as it was asked for`() {
        val request = GalleryDownloadRequest(
            url = "https://x.com/a/status/1",
            indexes = listOf(2, 3),
            options = GalleryOptions(imageSize = GalleryImageSize.LARGE, packZip = true),
            title = "Post",
            retryOf = "https://x.com/a/status/1?s=20"
        )
        assertEquals(request, GalleryDownloadRequest.fromPayload(request.toPayload()))
        assertNull(GalleryDownloadRequest.fromPayload("{\"url\":\"https://x.com\"}"))
    }

    @Test
    fun `files of one post are not duplicates of each other`() {
        fun file(id: Long, name: String) = HistoryEntry(
            id, "https://x.com/a/status/1", "Post", "a", null, 0, name, "content://$id", "", false, 1, id,
            isImage = true, isGallery = true
        )
        val entries = listOf(file(1, "1.jpg"), file(2, "2.jpg"), file(3, "1.jpg"))
        assertEquals(setOf(1L), DownloadHistoryRepository.duplicatesIn(entries))
        assertTrue(DownloadHistoryRepository.duplicatesIn(entries.take(2)).isEmpty())
    }
}
