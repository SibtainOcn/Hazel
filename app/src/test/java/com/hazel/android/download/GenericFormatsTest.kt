package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A choice made on a link's sheet before the link was read still lands on the right stream. */
class GenericFormatsTest {

    private fun video(id: String, height: Int) = MediaFormat(
        formatId = id, selector = id, label = "${height}p", ext = "mp4", vcodec = "avc1",
        acodec = "none", height = height, fps = 30, bitrateKbps = 0.0, fileSizeBytes = 1,
        hasVideo = true, hasAudio = false
    )

    private val read = MediaInfo(
        url = "u", title = "t", uploader = "", thumbnail = null, durationSeconds = 0,
        videoFormats = listOf(video("1080", 1080), video("720", 720), video("360", 360)),
        audioFormats = listOf(MediaProbe.BEST_AUDIO)
    )





    @Test
    fun `a height becomes the tallest stream under it once read`() {
        assertEquals("720", GenericFormats.applyTo(read, GenericFormats.heightCeiling(1000), null)?.formatId)
    }



    @Test
    fun `an unread link shows the ladder with the preferred height chosen, a read one its own list`() {
        val unread = read.copy(videoFormats = listOf(MediaProbe.BEST_VIDEO), audioFormats = listOf(MediaProbe.BEST_AUDIO))
        val shown = GenericFormats.untilRead(unread, "Best", "Best audio", "Worst")
        assertEquals(10, shown.videoFormats.size)
        assertEquals("bv_720p", shown.autoPick(true, 720)?.formatId)
        assertSame(read, GenericFormats.untilRead(read, "Best", "Best audio", "Worst"))
        // An audio-only source gets no video rows.
        val audioOnly = unread.copy(videoFormats = emptyList())
        assertEquals(0, GenericFormats.untilRead(audioOnly, "Best", "Best audio", "Worst").videoFormats.size)
    }



    @Test
    fun `best and worst become the link's own extremes`() {
        assertEquals("1080", GenericFormats.applyTo(read, MediaProbe.BEST_VIDEO, null)?.formatId)
        assertEquals("360", GenericFormats.applyTo(read, WORST_VIDEO, null)?.formatId)
    }


}
