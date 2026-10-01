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
    fun `standard heights stay on their step`() {
        listOf(144, 240, 360, 480, 720, 1080, 1440, 2160).forEach {
            assertEquals(it, video(it).qualityRung())
        }
    }

    @Test
    fun `encoder rounding snaps to the nearest step`() {
        assertEquals(1080, video(1072).qualityRung())
        assertEquals(1080, video(1088).qualityRung())
        assertEquals(720, video(704).qualityRung())
    }

    @Test
    fun `a cinema crop counts by its width`() {
        assertEquals(1080, video(800, "1080p (1920x800)").qualityRung())
        assertEquals(2160, video(1600, "2160p (3840x1600)").qualityRung())
    }

    @Test
    fun `a vertical clip counts by its short side`() {
        assertEquals(1080, video(1920, "1080p (1080x1920)").qualityRung())
        assertEquals(720, video(1280, "720p (720x1280)").qualityRung())
    }

    @Test
    fun `a size near no step keeps its own number`() {
        assertEquals(2880, video(2880).qualityRung())
        assertEquals(600, video(600).qualityRung())
    }

    @Test
    fun `audio and unknown sizes have no step`() {
        assertEquals(0, video(0).qualityRung())
        assertEquals(0, video(720).copy(hasVideo = false).qualityRung())
    }
}
