package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One audio choice made for a whole set has to land sensibly on every link in it, whatever
 * site each came from and whether its formats have been read yet.
 */
class BatchAudioFormatsTest {

    private fun audio(id: String, codec: String?, kbps: Double, size: Long, ext: String = "webm") =
        MediaFormat(
            formatId = id,
            selector = id,
            label = "audio $id",
            ext = ext,
            vcodec = "none",
            acodec = codec,
            height = 0,
            fps = 0,
            bitrateKbps = kbps,
            fileSizeBytes = size,
            hasVideo = false,
            hasAudio = true
        )

    private fun link(url: String, vararg formats: MediaFormat) = MediaInfo(
        url = url,
        title = url,
        uploader = "",
        thumbnail = null,
        durationSeconds = 0,
        videoFormats = emptyList(),
        audioFormats = listOf(MediaProbe.BEST_AUDIO) + formats
    )

    private fun unread(url: String) = link(url)

    private val youtubeA = link(
        "a",
        audio("251", "opus", 130.0, 4_000_000),
        audio("140", "mp4a.40.2", 129.0, 5_000_000, "m4a")
    )
    private val youtubeB = link(
        "b",
        audio("251", "opus", 128.0, 3_000_000),
        audio("140", "mp4a.40.2", 129.0, 4_000_000, "m4a")
    )
    private val saavn = link("c", audio("320", "mp4a.40.2", 320.0, 9_000_000, "m4a"))

    @Test
    fun `the ladder is offered at once, with nothing read`() {
        val ids = BatchAudioFormats.choices(listOf(unread("a"), unread("b")), "Best", "Worst")
            .map { it.formatId }
        assertEquals(
            listOf("bestaudio", "ba_192k", "ba_160k", "ba_128k", "ba_96k", "ba_64k", "worstaudio"),
            ids
        )
    }

    @Test
    fun `a bitrate step stays under its bitrate and falls back to the best`() {
        assertEquals("ba/b", BatchAudioFormats.bitrateCeiling(128).selector)
        assertEquals("abr:128", BatchAudioFormats.bitrateCeiling(128).sort)
    }

    @Test
    fun `formats every link carries are offered once all are read, sized for the set`() {
        val shared = BatchAudioFormats.sharedFormats(listOf(youtubeA, youtubeB))
        assertEquals(7_000_000, shared.first { it.formatId == "251" }.fileSizeBytes)
        assertTrue(shared.any { it.formatId == "140" })
    }

    @Test
    fun `nothing is shared while a link is unread or when sources differ`() {
        assertTrue(BatchAudioFormats.sharedFormats(listOf(youtubeA, unread("x"))).isEmpty())
        assertTrue(BatchAudioFormats.sharedFormats(listOf(youtubeA, saavn)).isEmpty())
    }

    @Test
    fun `a ladder row reaches every link unchanged`() {
        val step = BatchAudioFormats.bitrateCeiling(96)
        assertSame(step, BatchAudioFormats.pick(saavn, step, null))
        assertSame(step, BatchAudioFormats.pick(unread("x"), step, null))
    }

    @Test
    fun `a concrete format lands on the same id, or the link's best where it is missing`() {
        val choice = youtubeA.audioFormats.first { it.formatId == "140" }
        assertEquals("140", BatchAudioFormats.pick(youtubeB, choice, null)?.formatId)
        assertEquals("320", BatchAudioFormats.pick(saavn, choice, null)?.formatId)
    }

    @Test
    fun `best audio is the link's own best stream`() {
        assertEquals("251", BatchAudioFormats.pick(youtubeA, null, null)?.formatId)
    }

    @Test
    fun `a source that names no codec is matched by an extension only that codec uses`() {
        val unnamed = audio("hq", null, 320.0, 9_000_000, "m4a")
        assertEquals(AudioCodec.AAC, AudioCodec.of(unnamed))
        assertEquals(null, AudioCodec.of(unnamed.copy(ext = "mp4")))
    }
}
