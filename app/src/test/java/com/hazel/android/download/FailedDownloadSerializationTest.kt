package com.hazel.android.download

import com.hazel.android.data.FailedDownload
import com.hazel.android.data.FailedDownloadRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedDownloadSerializationTest {

    @Test
    fun `failed download encodes and decodes accurately`() {
        val entry = FailedDownload(
            id = 123456789L,
            url = "https://example.com/watch?v=fail123",
            title = "Failing Stream",
            author = "Error Artist",
            thumbnail = "https://example.com/fail.jpg",
            isVideo = true,
            errorLog = "HTTP 403: Forbidden\nTraceback info here",
            failedAt = 1700000000000L,
            queuedPayload = "{\"url\":\"https://example.com/watch?v=fail123\"}"
        )

        val encoded = FailedDownloadRepository.encode(listOf(entry))
        val decodedList = FailedDownloadRepository.decode(encoded)

        assertEquals(1, decodedList.size)
        val decoded = decodedList.first()
        assertEquals(entry.id, decoded.id)
        assertEquals(entry.url, decoded.url)
        assertEquals(entry.title, decoded.title)
        assertEquals(entry.author, decoded.author)
        assertEquals(entry.thumbnail, decoded.thumbnail)
        assertEquals(entry.isVideo, decoded.isVideo)
        assertEquals(entry.errorLog, decoded.errorLog)
        assertEquals(entry.failedAt, decoded.failedAt)
        assertEquals(entry.queuedPayload, decoded.queuedPayload)
    }

    @Test
    fun `blank raw decodes to empty list`() {
        assertTrue(FailedDownloadRepository.decode(null).isEmpty())
        assertTrue(FailedDownloadRepository.decode("").isEmpty())
        assertTrue(FailedDownloadRepository.decode("   ").isEmpty())
    }
}
