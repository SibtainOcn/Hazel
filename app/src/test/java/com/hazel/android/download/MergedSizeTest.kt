package com.hazel.android.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The size a download shows counts the soundtrack a video-only stream is muxed with. */
class MergedSizeTest {

    private fun stream(id: String, bytes: Long, video: Boolean, audio: Boolean, estimated: Boolean = false, language: String? = null) =
        MediaFormat(
            formatId = id, selector = id, label = id, language = language, ext = "mp4",
            vcodec = null, acodec = null, height = if (video) 240 else 0, fps = 0, bitrateKbps = 0.0,
            fileSizeBytes = bytes, isEstimatedSize = estimated, hasVideo = video, hasAudio = audio
        )

    private val video = stream("133", 92_700_000, video = true, audio = false)
    private val audio = stream("140", 111_000_000, video = false, audio = true)

    @Test
    fun `video only counts its audio`() {
        assertEquals(203_700_000L, video.bytesWith(audio))
        assertEquals(formatFileSize(203_700_000L), video.sizeLabelWith(audio))
    }

    @Test
    fun `muxed and audio streams stay as they are`() {
        val muxed = stream("18", 50_000_000, video = true, audio = true)
        assertEquals(50_000_000L, muxed.bytesWith(audio))
        assertEquals(muxed.sizeLabel, muxed.sizeLabelWith(audio))
        assertEquals(audio.sizeLabel, audio.sizeLabelWith(audio))
    }

    @Test
    fun `an estimate on either side marks the whole as one`() {
        assertTrue(video.sizeLabelWith(stream("251", 90_000_000, video = false, audio = true, estimated = true)).startsWith("~"))
        assertTrue(video.sizeLabelWith(stream("251", 0, video = false, audio = true)).startsWith("~"))
    }

    @Test
    fun `no size stays no size`() {
        val unknown = stream("137", 0, video = true, audio = false)
        assertEquals(0L, unknown.bytesWith(audio))
        assertEquals("", unknown.sizeLabelWith(audio))
    }

    @Test
    fun `the picked soundtrack is the one counted`() {
        val english = stream("140-en", 100_000_000, video = false, audio = true, language = "en")
        val hindi = stream("140-hi", 120_000_000, video = false, audio = true, language = "hi")
        val info = MediaInfo("u", "t", "a", null, 7200, listOf(video), listOf(english, hindi))
        assertEquals(212_700_000L, video.bytesWith(info.mergeAudioFor("hi")))
        assertEquals(192_700_000L, video.bytesWith(info.mergeAudioFor(null)))
    }
}
