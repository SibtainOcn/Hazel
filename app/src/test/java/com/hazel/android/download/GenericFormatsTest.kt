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
    fun `the placeholder offers both ladders before anything is read`() {
        val info = GenericFormats.placeholder("u", "Best", "Best audio", "Worst")
        // Best, eight heights from 2160p down to 144p, and worst.
        assertEquals(10, info.videoFormats.size)
        assertEquals(7, info.audioFormats.size)
        assertEquals("bv*+ba/b", GenericFormats.heightCeiling(720).selector)
        assertEquals("res:720", GenericFormats.heightCeiling(720).sort)
    }

    @Test
    fun `a ladder step reads back as the ceiling a set of links keeps`() {
        assertEquals(0, GenericFormats.ceilingOf(MediaProbe.BEST_VIDEO))
        assertEquals(WORST_HEIGHT, GenericFormats.ceilingOf(WORST_VIDEO))
        assertEquals(720, GenericFormats.ceilingOf(GenericFormats.heightCeiling(720)))
    }

    @Test
    fun `a height becomes the tallest stream under it once read`() {
        assertEquals("720", GenericFormats.applyTo(read, GenericFormats.heightCeiling(1000), null)?.formatId)
    }

    @Test
    fun `the preferred video codec is taken at the height the choice lands on`() {
        val both = read.copy(videoFormats = read.videoFormats + video("720v", 720).copy(vcodec = "vp09.00.40.08"))
        val ceiling = GenericFormats.heightCeiling(1000)
        assertEquals("720v", GenericFormats.applyTo(both, ceiling, null, VideoCodec.VP9)?.formatId)
        assertEquals("720", GenericFormats.applyTo(both, ceiling, null)?.formatId)
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
    fun `a direct stream is taken over HLS at the same height and frame rate`() {
        val hls = video("311", 720).copy(fps = 60, hls = true)
        val direct = video("298", 720).copy(fps = 60)
        val slower = video("136", 720)
        val info = read.copy(videoFormats = listOf(hls, slower, direct))
        assertEquals("298", GenericFormats.applyTo(info, GenericFormats.heightCeiling(720), null)?.formatId)
        assertEquals("298", info.bestVideo?.formatId)
        // HLS stays when it is the only stream at that height.
        val only = read.copy(videoFormats = listOf(hls, video("360", 360)))
        assertEquals("311", GenericFormats.applyTo(only, GenericFormats.heightCeiling(720), null)?.formatId)
    }

    @Test
    fun `best and worst become the link's own extremes`() {
        assertEquals("1080", GenericFormats.applyTo(read, MediaProbe.BEST_VIDEO, null)?.formatId)
        assertEquals("360", GenericFormats.applyTo(read, WORST_VIDEO, null)?.formatId)
    }

    @Test
    fun `a bitrate step stays the expression it is`() {
        val step = BatchAudioFormats.bitrateCeiling(128)
        assertSame(step, GenericFormats.applyTo(read, step, null))
    }
}
