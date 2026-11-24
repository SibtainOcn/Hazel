package com.hazel.android.download

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CutProgressTest {

    private val hundredMb = 100_000_000L

    @Test
    fun `a cut keeps its share of the size`() {
        // 30 s of a 120 s link is a quarter.
        assertEquals(25_000_000L, CutProgress.expectedBytes(hundredMb, 120, 30.0, 60.0))
    }

    @Test
    fun `a cut of the whole link weighs the whole link`() {
        assertEquals(hundredMb, CutProgress.expectedBytes(hundredMb, 120, 0.0, 120.0))
    }

    @Test
    fun `a cut that runs past the end is measured to the end`() {
        assertEquals(
            CutProgress.expectedBytes(hundredMb, 120, 90.0, 120.0),
            CutProgress.expectedBytes(hundredMb, 120, 90.0, 400.0)
        )
    }

    @Test
    fun `nothing is guessed when the size or the running time is not known`() {
        assertEquals(0L, CutProgress.expectedBytes(0L, 120, 0.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(-1L, 120, 0.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 0, 0.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, -5, 0.0, 10.0))
    }

    @Test
    fun `a cut that is not a part of the link measures nothing`() {
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, 10.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, 30.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, -1.0, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, 200.0, 300.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, Double.NaN, 10.0))
        assertEquals(0L, CutProgress.expectedBytes(hundredMb, 120, 0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a very short cut still has something to measure against`() {
        assertTrue(CutProgress.expectedBytes(1_000L, 36_000, 0.0, 0.001) >= 1L)
    }

    @Test
    fun `a large link does not overflow`() {
        val twentyGb = 20L * 1024 * 1024 * 1024
        val half = CutProgress.expectedBytes(twentyGb, 7_200, 0.0, 3_600.0)
        assertEquals(twentyGb / 2, half)
    }

    // ffmpeg's `-progress` report, as yt-dlp's ffmpeg downloader passes it on.

    @Test
    fun `how far a cut has got is read from ffmpeg's report`() {
        assertEquals(2.283537, CutProgress.outTimeSeconds("out_time_us=2283537"))
        assertEquals(4.744853, CutProgress.outTimeSeconds("out_time_ms=4744853"))
        assertEquals(62.5, CutProgress.outTimeSeconds("out_time=00:01:02.500000"))
    }

    @Test
    fun `nothing is read before the first packet or from other lines`() {
        assertEquals(null, CutProgress.outTimeSeconds("out_time_us=-15238"))
        assertEquals(null, CutProgress.outTimeSeconds("out_time_us=N/A"))
        assertEquals(null, CutProgress.outTimeSeconds("out_time=-00:00:00.015238"))
        assertEquals(null, CutProgress.outTimeSeconds("[download] Destination: clip.mp4"))
    }

    @Test
    fun `report lines are told apart from yt-dlp's`() {
        assertTrue(CutProgress.isProgressLine("progress=continue"))
        assertTrue(CutProgress.isProgressLine("stream_0_0_q=-1.0"))
        assertTrue(CutProgress.isProgressLine("speed=N/A"))
        assertTrue(!CutProgress.isProgressLine("[download] 100% of    7.55MiB in 00:00:14"))
        assertTrue(!CutProgress.isProgressLine("ERROR: unable to download video data"))
    }

    @Test
    fun `speed is read only when there is one`() {
        assertEquals(1.49, CutProgress.speedFactor("speed=   1.49x"))
        assertEquals(null, CutProgress.speedFactor("speed=   0x"))
        assertEquals(null, CutProgress.speedFactor("speed=N/A"))
    }

    @Test
    fun `the kept part's length follows the cut`() {
        assertEquals(30.0, CutProgress.keptSeconds(120, 90.0, 400.0))
        assertEquals(30.0, CutProgress.keptSeconds(0, 40.0, 70.0))
        assertEquals(0.0, CutProgress.keptSeconds(120, 30.0, 10.0))
        assertEquals(0.0, CutProgress.keptSeconds(120, Double.NaN, 10.0))
    }

    @Test
    fun `the share stays short of whole and the time left is never negative`() {
        assertEquals(0.5f, CutProgress.fraction(15.0, 30.0))
        assertEquals(0.99f, CutProgress.fraction(31.0, 30.0))
        assertEquals(0f, CutProgress.fraction(10.0, 0.0))
        assertEquals(10L, CutProgress.etaSeconds(10.0, 30.0, 2.0))
        assertEquals(0L, CutProgress.etaSeconds(31.0, 30.0, 2.0))
        assertEquals(null, CutProgress.etaSeconds(10.0, 30.0, 0.0))
        assertEquals("01:05", CutProgress.clock(65))
        assertEquals("1:01:05", CutProgress.clock(3665))
    }

    private fun withDir(block: (File) -> Unit) {
        val dir = java.nio.file.Files.createTempDirectory("cut-progress").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.put(name: String, size: Int) = File(this, name).writeBytes(ByteArray(size))

    @Test
    fun `a thumbnail and subtitles are not counted as the cut`() = withDir { dir ->
        dir.put("clip.webp", 100)
        dir.put("clip.en.vtt", 50)
        dir.put("clip.f137.mp4.part", 30)
        assertEquals(30L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `the part file being written is the cut`() = withDir { dir ->
        dir.put("clip.mp4.part", 210)
        assertEquals(210L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `a copy written while the cover or tags go in does not double the cut`() = withDir { dir ->
        dir.put("clip.mp4", 200)
        dir.put("clip.temp.mp4", 190)
        assertEquals(200L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `an audio cut being converted is not counted twice`() = withDir { dir ->
        dir.put("clip.webm", 120)
        dir.put("clip.mp3", 150)
        assertEquals(150L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `engine bookkeeping never counts`() = withDir { dir ->
        dir.put("clip.f137.mp4.ytdl", 500)
        assertEquals(0L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `a folder that is missing weighs nothing`() {
        assertEquals(0L, CutProgress.mediaBytesIn(File("/no/such/cut/folder")))
    }
}
