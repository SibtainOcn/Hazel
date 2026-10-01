package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The card and the shade read speed and time remaining off the same yt-dlp lines, and the
 * card decides from them whether bytes are still moving or the file is being worked on.
 */
class ProgressTextTest {

    private val transfer = "[download]  16.0% of ~ 117.30MiB at  4.98MiB/s ETA 00:14 (frag 3/20)"

    @Test
    fun `reads the rate and time remaining from a transfer line`() {
        assertEquals("4.98MiB/s", ProgressText.rate(transfer))
        assertEquals("00:14", ProgressText.eta(transfer))
    }

    @Test
    fun `reads a rate given in plain bytes or kilobytes`() {
        assertEquals("812.00KiB/s", ProgressText.rate("[download]   2.1% of 40.20MiB at 812.00KiB/s ETA 00:49"))
        assertEquals("900B/s", ProgressText.rate("[download]   0.0% of 40.20MiB at 900B/s ETA 12:00"))
    }

    @Test
    fun `a transfer line is one reporting bytes on the move`() {
        assertTrue(ProgressText.isTransferLine(transfer))
        assertTrue(ProgressText.isTransferLine("[download] 100% of  117.30MiB in 00:00:23 at 4.98MiB/s"))
    }

    @Test
    fun `stage and notice lines are not transfers and carry no figures`() {
        val lines = listOf(
            "[Merger] Merging formats into \"video.mp4\"",
            "[download] Destination: video.f137.mp4",
            "[EmbedThumbnail] ffmpeg: Adding thumbnail to \"video.mp4\"",
            "[youtube] abc123: Downloading webpage"
        )
        lines.forEach { line ->
            assertFalse(ProgressText.isTransferLine(line), line)
            assertNull(ProgressText.eta(line), line)
        }
    }
}
