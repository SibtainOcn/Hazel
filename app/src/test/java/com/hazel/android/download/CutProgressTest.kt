package com.hazel.android.download

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cut has no progress line to read, so the bar is measured against what the kept part
 * should weigh. These pin the scale, and that nothing odd about a cut can make it nonsense.
 */
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
    fun `a finished stream and the part file in hand both count`() = withDir { dir ->
        dir.put("clip.f137.mp4", 200)
        dir.put("clip.f140.m4a.part", 10)
        assertEquals(210L, CutProgress.mediaBytesIn(dir))
    }

    @Test
    fun `part files are left out when a run starts`() = withDir { dir ->
        dir.put("clip.f137.mp4", 200)
        dir.put("clip.f140.m4a.part", 50)
        assertEquals(200L, CutProgress.mediaBytesIn(dir, includePartFiles = false))
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
