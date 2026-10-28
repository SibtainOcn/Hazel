package com.hazel.android.ui.screens.download.batch

import com.hazel.android.download.MediaInfo
import com.hazel.android.download.MediaProbe
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The playlist sheet once said "1 selected" and then downloaded every link in the list.
 * Download takes the ticked links, and the whole list only when none are ticked.
 */
class BatchSelectionDownloadTest {

    private val links = (1..5).map {
        MediaInfo(
            url = "https://www.youtube.com/watch?v=video0000$it",
            title = "Song $it",
            uploader = "Channel",
            thumbnail = null,
            durationSeconds = 200,
            videoFormats = listOf(MediaProbe.BEST_VIDEO),
            audioFormats = listOf(MediaProbe.BEST_AUDIO)
        )
    }

    private fun state() = BatchDownloadState().apply { results = links }

    @Test
    fun `nothing ticked downloads the whole list`() {
        assertEquals(links.map { it.url }, state().plans.map { it.info.url })
    }

    @Test
    fun `one ticked link downloads that link alone`() {
        val state = state()
        state.startSelection(links[2])
        assertEquals(listOf(links[2].url), state.plans.map { it.info.url })
    }

    @Test
    fun `inverting a selection downloads the other links`() {
        val state = state()
        state.startSelection(links[0])
        state.invertSelection()
        assertEquals(links.drop(1).map { it.url }, state.plans.map { it.info.url })
    }

    @Test
    fun `leaving selection goes back to the whole list`() {
        val state = state()
        state.startSelection(links[1])
        state.endSelection()
        assertEquals(links.size, state.plans.size)
    }

    @Test
    fun `selection mode with nothing ticked still downloads the whole list`() {
        val state = state()
        state.startSelection()
        assertEquals(links.size, state.plans.size)
    }
}
