package com.hazel.android.ui.screens.download

import com.hazel.android.data.HistoryEntry
import com.hazel.android.download.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchHistoryTest {

    @Test
    fun `a card finds its first record whatever spelling the link has`() {
        fun entry(id: Long, url: String) = HistoryEntry(
            id = id, url = url, title = "", author = "", thumbnail = null, durationSeconds = 0,
            fileName = "", fileUri = "", savedPath = "", isVideo = true, sizeBytes = 0, completedAt = 0
        )
        fun card(url: String) = MediaInfo(url, "", "", null, 0, emptyList(), emptyList())
        val history = listOf(entry(1, "https://youtu.be/abc?si=x"), entry(2, "https://www.youtube.com/watch?v=abc"))

        val found = matchHistory(listOf(card("https://youtube.com/watch?v=abc&list=L"), card("https://youtu.be/zzz")), history)

        assertEquals(mapOf("https://youtube.com/watch?v=abc&list=L" to 1L), found.mapValues { it.value.id })
    }
}
