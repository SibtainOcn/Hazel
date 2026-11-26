package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals

/** Streams are grouped by the quality step they stand on, not by their raw height. */
class QualityRungTest {

    private fun video(height: Int, label: String = "${height}p") = MediaFormat(
        formatId = "v$height", selector = "v$height", label = label, ext = "mp4", vcodec = "avc1",
        acodec = "none", height = height, fps = 30, bitrateKbps = 0.0, fileSizeBytes = 0,
        hasVideo = true, hasAudio = false
    )



    @Test
    fun `encoder rounding snaps to the nearest step`() {
        assertEquals(1080, video(1072).qualityRung())
        assertEquals(1080, video(1088).qualityRung())
        assertEquals(720, video(704).qualityRung())
    }



    @Test
    fun `a vertical clip counts by its short side`() {
        assertEquals(1080, video(1920, "1080p (1080x1920)").qualityRung())
        assertEquals(720, video(1280, "720p (720x1280)").qualityRung())
    }




}
