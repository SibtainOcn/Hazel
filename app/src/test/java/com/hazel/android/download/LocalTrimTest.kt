package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
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
        val cmd = LocalTrim.trimCommand("ffmpeg", "in.mp4", "out.mp4", streams, 7.0, 17.0, null)
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
    fun `a precise trim re-encodes video and audio and reads chapters from a second copy`() {
        val streams = LocalTrim.parseStreams("Stream #0:0: Video: vp9\nStream #0:1: Audio: opus\n")
        val cmd = LocalTrim.trimCommand(
            "ffmpeg", "in.mkv", "out.mkv", streams, 7.0, 17.0, LocalTrim.Encoders("libx264", "aac")
        )
        assertEquals(2, cmd.count { it == "-i" })
        assertTrue(cmd.containsAll(listOf("-c:v:0", "libx264", "-c:a", "aac", "-map_chapters", "1")))
        assertTrue("-disposition:v:0" !in cmd)
    }
}
