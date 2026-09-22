package com.hazel.android.download

import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.data.QueuedDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSerializationTest {

    @Test
    fun `queued download encodes and decodes accurately`() {
        val queued = QueuedDownload(
            url = "https://example.com/watch?v=123",
            title = "Test Song",
            author = "Test Artist",
            thumbnail = "https://example.com/thumb.jpg",
            durationSeconds = 240,
            formatId = "320",
            selector = "320",
            formatLabel = "320 KBPS AUDIO",
            ext = "m4a",
            hasVideo = false,
            hasAudio = true,
            isGeneric = false,
            fileSizeBytes = 1234567L,
            mergeAudioSelector = null,
            mergeAudioSizeBytes = 0L,
            treeUri = "",
            requiresSignIn = false,
            audioLanguage = null,
            options = DownloadOptions(audioContainer = "m4a"),
            paused = false
        )

        val encoded = DownloadQueueRepository.encodeItem(queued)
        val decoded = DownloadQueueRepository.decodeItem(encoded)

        assertNotNull(decoded)
        assertEquals(queued.url, decoded?.url)
        assertEquals(queued.title, decoded?.title)
        assertEquals(queued.author, decoded?.author)
        assertEquals(queued.formatId, decoded?.formatId)
        assertEquals(queued.formatLabel, decoded?.formatLabel)
        assertEquals(queued.ext, decoded?.ext)
        assertEquals(queued.hasAudio, decoded?.hasAudio)
        assertEquals(queued.hasVideo, decoded?.hasVideo)
        assertEquals(queued.fileSizeBytes, decoded?.fileSizeBytes)
    }
}
