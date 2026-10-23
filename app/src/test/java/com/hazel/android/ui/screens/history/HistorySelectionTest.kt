package com.hazel.android.ui.screens.history

import com.hazel.android.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySelectionTest {

    private fun entry(id: Long) = HistoryEntry(
        id = id, url = "https://example.com/$id", title = "t$id", author = "", thumbnail = null,
        durationSeconds = 0, fileName = "f$id.mp4", fileUri = "", savedPath = "", isVideo = true,
        sizeBytes = 0, completedAt = id
    )

    private val list = (1L..5L).map(::entry)

    @Test
    fun `toggling one id touches only that id`() {
        val picked = HistorySelection.toggle(setOf(1L, 2L), 3L)
        assertEquals(setOf(1L, 2L, 3L), picked)
        assertEquals(setOf(1L, 2L), HistorySelection.toggle(picked, 3L))
    }

    @Test
    fun `select all picks every visible row, deleted ones included`() {
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), HistorySelection.all(list))
        assertTrue(HistorySelection.allChosen(HistorySelection.all(list), list))
        assertFalse(HistorySelection.allChosen(setOf(1L, 2L), list))
    }

    @Test
    fun `an empty list is never all chosen`() {
        assertFalse(HistorySelection.allChosen(emptySet(), emptyList()))
    }

    @Test
    fun `targets are only picked rows still on the list`() {
        val shown = list.take(3)
        val targets = HistorySelection.targets(setOf(2L, 4L, 99L), shown)
        assertEquals(listOf(2L), targets.map { it.id })
    }

    @Test
    fun `a single pick never widens to the whole list`() {
        val targets = HistorySelection.targets(setOf(3L), list)
        assertEquals(listOf(3L), targets.map { it.id })
    }

    @Test
    fun `nothing picked means nothing to act on`() {
        assertTrue(HistorySelection.targets(emptySet(), list).isEmpty())
    }

    @Test
    fun `prune drops rows hidden by a filter or removed`() {
        assertEquals(setOf(1L), HistorySelection.prune(setOf(1L, 4L), list.take(2)))
        assertTrue(HistorySelection.prune(setOf(1L), emptyList()).isEmpty())
    }
}
