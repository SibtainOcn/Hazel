package com.hazel.android.data

import com.hazel.android.util.MediaDeleter.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDeleteTest {

    private fun entry(id: Long) = HistoryEntry(
        id = id, url = "https://example.com/$id", title = "t$id", author = "", thumbnail = null,
        durationSeconds = 0, fileName = "f$id.mp4", fileUri = "", savedPath = "", isVideo = true,
        sizeBytes = 0, completedAt = id
    )

    @Test
    fun `only deleted or already gone files lose their record`() {
        val outcome = DeleteOutcome.of(
            mapOf(1L to Result.DELETED, 2L to Result.GONE, 3L to Result.FAILED, 4L to Result.NEEDS_CONSENT)
        )
        assertEquals(setOf(1L, 2L), outcome.removedIds)
        assertEquals(setOf(4L), outcome.consentIds)
        assertEquals(1, outcome.deleted)
        assertEquals(1, outcome.failed)
    }

    @Test
    fun `a failed delete keeps every record`() {
        val outcome = DeleteOutcome.of(mapOf(1L to Result.FAILED, 2L to Result.FAILED))
        assertTrue(outcome.removedIds.isEmpty())
        assertEquals(2, outcome.failed)
    }

    @Test
    fun `a new record finishing in the same millisecond gets its own id`() {
        val existing = listOf(entry(100L), entry(50L))
        val added = DownloadHistoryRepository.withFreeId(entry(100L), existing)
        assertNotEquals(100L, added.id)
        assertTrue(existing.none { it.id == added.id })
        assertEquals(42L, DownloadHistoryRepository.withFreeId(entry(42L), existing).id)
    }

    @Test
    fun `repeated ids in older records become unique, the same way every read`() {
        val stored = listOf(entry(7L), entry(7L), entry(3L), entry(7L))
        val first = DownloadHistoryRepository.uniqueIds(stored).map { it.id }
        assertEquals(first.size, first.toSet().size)
        assertEquals(7L, first[0])
        assertEquals(first, DownloadHistoryRepository.uniqueIds(stored).map { it.id })
    }

    @Test
    fun `unique ids are left alone`() {
        val stored = listOf(entry(1L), entry(2L))
        assertEquals(stored, DownloadHistoryRepository.uniqueIds(stored))
    }
}
