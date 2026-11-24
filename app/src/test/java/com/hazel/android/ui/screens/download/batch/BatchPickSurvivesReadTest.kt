package com.hazel.android.ui.screens.download.batch

import com.hazel.android.download.GenericFormats
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue 157: a quality picked for one link while it is still being read stays picked once it is read. */
class BatchPickSurvivesReadTest {

    private val url = "https://youtu.be/video00001"
    private val ladder = GenericFormats.videoLadder("Best quality", "Worst quality")
    private val unread = MediaInfo(url, "Song", "Channel", null, 200, ladder, emptyList())

    private fun video(id: String, height: Int) = MediaFormat(
        formatId = id, selector = id, label = "${height}p", ext = "mp4", vcodec = "avc1", acodec = null,
        height = height, fps = 30, bitrateKbps = 0.0, fileSizeBytes = 1L, hasVideo = true, hasAudio = false
    )

    @Test
    fun `a pick made before the read is kept after it`() {
        val state = BatchDownloadState().apply { results = listOf(unread) }
        val step = ladder.first { GenericFormats.ceilingOf(it) == 720 }
        state.setFormat(unread, step)

        // The read lands under the address the link was listed with, as the view model keeps it.
        state.results = listOf(unread.copy(videoFormats = listOf(video("137", 1080), video("136", 720))))

        assertEquals(step.formatId, state.formatOf(state.results.single())?.formatId)
        assertEquals(true, state.isAdjusted(state.results.single()))
    }
}
