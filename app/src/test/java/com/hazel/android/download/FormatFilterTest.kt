package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals

/** The format list filters narrow whatever a source reported, without knowing the site. */
class FormatFilterTest {

    private fun audio(id: String, codec: String, label: String, size: Long, ext: String = "webm") =
        MediaFormat(
            formatId = id, selector = id, label = label, ext = ext, vcodec = "none",
            acodec = codec, height = 0, fps = 0, bitrateKbps = 0.0, fileSizeBytes = size,
            hasVideo = false, hasAudio = true
        )

    private fun video(id: String, height: Int, size: Long, fps: Int = 30) = MediaFormat(
        formatId = id, selector = id, label = "${height}p", ext = "mp4", vcodec = "avc1",
        acodec = "none", height = height, fps = fps, bitrateKbps = 0.0, fileSizeBytes = size,
        hasVideo = true, hasAudio = false
    )

    // Best first, the order a read hands them over in.
    private val youtubeAudio = listOf(
        MediaProbe.BEST_AUDIO,
        audio("140-drc", "mp4a.40.2", "medium, DRC", 25), audio("140", "mp4a.40.2", "medium", 25, "m4a"),
        audio("251", "opus", "medium", 19), audio("251-drc", "opus", "medium, DRC", 20),
        audio("250", "opus", "low", 11), audio("249", "opus", "low", 9)
    )

    @Test
    fun `all keeps everything`() {
        assertEquals(youtubeAudio, FormatFilter.ALL.apply(youtubeAudio, audio = true))
    }

    @Test
    fun `suggested keeps the best stream per codec and drops loudness copies`() {
        val ids = FormatFilter.SUGGESTED.apply(youtubeAudio, audio = true).map { it.formatId }
        assertEquals(listOf("bestaudio", "140", "251"), ids)
    }

    @Test
    fun `smallest keeps the smallest file at each quality level`() {
        val ids = FormatFilter.SMALLEST.apply(youtubeAudio, audio = true).map { it.formatId }
        assertEquals(listOf("bestaudio", "251", "249"), ids)
    }

    @Test
    fun `smallest video is chosen per resolution`() {
        val formats = listOf(video("a", 1080, 90), video("b", 1080, 60), video("c", 720, 40))
        val ids = FormatFilter.SMALLEST.apply(formats, audio = false).map { it.formatId }
        assertEquals(listOf("b", "c"), ids)
    }

    @Test
    fun `suggested video keeps a high frame rate apart from a standard one`() {
        val formats = listOf(video("a", 1080, 90, fps = 60), video("b", 1080, 60), video("c", 1080, 50))
        val ids = FormatFilter.SUGGESTED.apply(formats, audio = false).map { it.formatId }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun `generic offers the engine's own choice even when the source listed none`() {
        val formats = listOf(video("a", 1080, 90))
        assertEquals(listOf(MediaProbe.BEST_VIDEO), FormatFilter.GENERIC.apply(formats, audio = false))
    }
}
