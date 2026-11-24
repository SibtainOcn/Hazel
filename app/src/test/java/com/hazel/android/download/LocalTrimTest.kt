package com.hazel.android.download

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalTrimTest {

    @Test
    fun `srt cues are clipped, shifted and renumbered`() {
        val srt = "1\r\n00:00:00,000 --> 00:00:23,000\r\nspans\r\n\r\n" +
            "2\r\n00:00:02,000 --> 00:00:04,000\r\nbefore\r\n\r\n" +
            "3\r\n00:00:08,500 --> 00:00:09,000\r\ninside\r\n"
        assertEquals(
            "1\n00:00:00,000 --> 00:00:10,000\nspans\n\n2\n00:00:01,500 --> 00:00:02,000\ninside\n",
            LocalTrim.cutSubtitles(srt, srt = true, start = 7.0, end = 17.0)
        )
    }

    @Test
    fun `vtt keeps its header, cue settings and shifts inline times`() {
        val vtt = "WEBVTT\nKind: captions\n\n00:07.500 --> 00:00:09.000 align:start\nhi <00:00:08.000><c>there</c>\n\n" +
            "00:30.000 --> 00:31.000\nafter\n"
        assertEquals(
            "WEBVTT\nKind: captions\n\n00:00:00.500 --> 00:00:02.000 align:start\nhi <00:00:01.000><c>there</c>\n",
            LocalTrim.cutSubtitles(vtt, srt = false, start = 7.0, end = 17.0)
        )
    }

    @Test
    fun `streams and covers are read from the listing`() {
        val streams = LocalTrim.parseStreams(
            "  Stream #0:0[0x1](und): Video: h264 (avc1), 320x180 (default)\n" +
                "  Stream #0:1(eng): Audio: aac (mp4a), 44100 Hz\n" +
                "  Stream #0:2[0x0]: Video: mjpeg, 300x300 (attached pic)\n" +
                "  Stream #0:3: Data: bin_data (text)\n" +
                "Duration: 00:00:24.00"
        )
        assertEquals(listOf(0, 1, 2, 3), streams.map { it.index })
        assertEquals(listOf(false, false, true, false), streams.map { it.cover })
    }

    @Test
    fun `a copy trim maps the cover last and flags it again`() {
        val streams = LocalTrim.parseStreams(
            "Stream #0:0: Video: h264\nStream #0:1: Video: mjpeg (attached pic)\nStream #0:2: Audio: aac\n"
        )
        val cmd = LocalTrim.trimCommand("ffmpeg", "in.mp4", "out.mp4", streams, 7.0, 17.0)
        assertEquals(
            listOf(
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-ss", "7.000", "-t", "10.000", "-i", "in.mp4",
                "-map", "0:0", "-map", "0:2", "-map", "0:1", "-map_metadata", "0", "-map_chapters", "0",
                "-c", "copy", "-disposition:v:1", "attached_pic", "out.mp4"
            ),
            cmd
        )
    }

    @Test
    fun `an mkv attachment is kept and nothing is re-encoded`() {
        val streams = LocalTrim.parseStreams(
            "Stream #0:0: Video: vp9\nStream #0:1: Audio: opus\nStream #0:2: Attachment: mjpeg\n"
        )
        val cmd = LocalTrim.trimCommand("ffmpeg", "in.mkv", "out.mkv", streams, 7.0, 17.0)
        assertTrue(cmd.containsAll(listOf("0:0", "0:1", "0:2")))
        assertTrue(cmd.none { it.startsWith("-c:") || it.startsWith("-disposition") })
    }

    @Test
    fun `the newest media file is the one trimmed, as the save picks it`() {
        val dir = kotlin.io.path.createTempDirectory("trim").toFile()
        try {
            File(dir, "clip.webm").apply { writeBytes(ByteArray(500)); setLastModified(1_000_000L) }
            File(dir, "clip.mp3").apply { writeBytes(ByteArray(100)); setLastModified(2_000_000L) }
            File(dir, "clip.jpg").apply { writeBytes(ByteArray(900)); setLastModified(3_000_000L) }
            File(dir, LocalTrim.DONE_MARKER).writeText("x")
            assertEquals("clip.mp3", LocalTrim.mainMedia(dir)?.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    private class FakeProcess(private val exitsAfterChecks: Int) : Process() {
        var checks = 0
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = 0
        override fun exitValue(): Int {
            if (++checks <= exitsAfterChecks) throw IllegalThreadStateException()
            return 0
        }
        override fun destroy() = Unit
    }

    @Test
    fun `legacy wait returns once the process exits`() {
        val p = FakeProcess(exitsAfterChecks = 2)
        assertTrue(LocalTrim.waitFor(p, 5_000L, legacy = true))
        assertEquals(3, p.checks)
    }

    @Test
    fun `legacy wait gives up at the timeout`() {
        assertFalse(LocalTrim.waitFor(FakeProcess(exitsAfterChecks = Int.MAX_VALUE), 300L, legacy = true))
    }
}
