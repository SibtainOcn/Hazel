package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One audio choice made for a whole set has to land sensibly on every link in it, whatever
 * site each came from and whether its formats have been read yet.
 */
class BatchAudioFormatsTest {

    private fun audio(id: String, codec: String, kbps: Double, size: Long, ext: String = "webm") =
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
    fun `with nothing read yet only best audio is offered`() {
        val choices = BatchAudioFormats.choices(listOf(unread("a"), unread("b")))
        assertEquals(listOf(BatchAudioFormats.BEST), choices)
    }

    @Test
    fun `formats every link carries are offered with the whole set's size`() {
        val choices = BatchAudioFormats.choices(listOf(youtubeA, youtubeB))
        val opus = choices.first { it.formatId == "251" }
        assertEquals(7_000_000, opus.fileSizeBytes)
        assertTrue(choices.any { it.formatId == "140" })
    }

    @Test
    fun `a format only some links carry is not offered, but its codec is`() {
        val choices = BatchAudioFormats.choices(listOf(youtubeA, saavn))
        assertTrue(choices.none { it.formatId == "251" || it.formatId == "320" })
        assertTrue(choices.any { it.formatId == AudioCodec.OPUS.genericFormat().formatId })
        assertTrue(choices.any { it.formatId == AudioCodec.AAC.genericFormat().formatId })
    }

    @Test
    fun `a size covering only the links read so far is marked as a floor`() {
        val choices = BatchAudioFormats.choices(listOf(youtubeA, youtubeB, unread("c")))
        assertTrue(choices.first { it.formatId == "251" }.isEstimatedSize)
    }

    @Test
    fun `a codec a link does not offer falls back to its best audio`() {
        val picked = BatchAudioFormats.pick(saavn, AudioCodec.OPUS.genericFormat(), null)
        assertEquals("320", picked?.formatId)
    }

    @Test
    fun `a codec choice takes that codec where the link offers it`() {
        val picked = BatchAudioFormats.pick(youtubeA, AudioCodec.AAC.genericFormat(), null)
        assertEquals("140", picked?.formatId)
    }

    @Test
    fun `a concrete format lands on the same id, or its codec where the id is missing`() {
        val choice = youtubeA.audioFormats.first { it.formatId == "140" }
        assertEquals("140", BatchAudioFormats.pick(youtubeB, choice, null)?.formatId)
        assertEquals("320", BatchAudioFormats.pick(saavn, choice, null)?.formatId)
    }

    @Test
    fun `an unread link is handed a codec filter the engine can resolve`() {
        val picked = BatchAudioFormats.pick(unread("x"), AudioCodec.OPUS.genericFormat(), null)
        assertEquals("ba[acodec^=opus]/ba/b", picked?.selector)
    }

    @Test
    fun `a source that names no codec is matched by an extension only that codec uses`() {
        val unnamed = audio("hq", "", 320.0, 9_000_000, "m4a").copy(acodec = null)
        assertEquals(AudioCodec.AAC, AudioCodec.of(unnamed))
        assertEquals(null, AudioCodec.of(unnamed.copy(ext = "mp4")))
    }
}
