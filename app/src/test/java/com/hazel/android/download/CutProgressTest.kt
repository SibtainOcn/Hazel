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













    // ffmpeg's `-progress` report, as yt-dlp's ffmpeg downloader passes it on.

    @Test
    fun `how far a cut has got is read from ffmpeg's report`() {
        assertEquals(2.283537, CutProgress.outTimeSeconds("out_time_us=2283537"))
        assertEquals(4.744853, CutProgress.outTimeSeconds("out_time_ms=4744853"))
        assertEquals(62.5, CutProgress.outTimeSeconds("out_time=00:01:02.500000"))
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
    fun `a copy written while the cover or tags go in does not double the cut`() = withDir { dir ->
        dir.put("clip.mp4", 200)
        dir.put("clip.temp.mp4", 190)
        assertEquals(200L, CutProgress.mediaBytesIn(dir))
    }



    @Test
    fun `engine bookkeeping never counts`() = withDir { dir ->
        dir.put("clip.f137.mp4.ytdl", 500)
        assertEquals(0L, CutProgress.mediaBytesIn(dir))
    }




}
