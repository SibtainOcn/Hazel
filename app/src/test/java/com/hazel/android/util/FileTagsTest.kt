package com.hazel.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTagsTest {

    /** Tiny files tagged by ffmpeg the way yt-dlp's --embed-metadata tags a download. */
    @Test
    fun readsTheTagsOfEveryContainerYtDlpWrites() {
        listOf("mkv", "mp3", "m4a", "opus", "flac").forEach { ext ->
            val bytes = javaClass.getResourceAsStream("/tags/sample.$ext")!!.use { it.readBytes() }
            val tags = FileTagReader.read(ArraySource(bytes))
            assertEquals(ext, "Sample title", tags.title)
            assertEquals(ext, "Some Channel", tags.artist)
            assertEquals(ext, "20240131", tags.date)
            assertEquals(ext, "https://example.com/watch", tags.link)
            // The description holds the link here, so the synopsis is the text shown.
            assertEquals(ext, "Line one ✨", tags.description)
            assertEquals(ext, "", tags.comment)
        }
        assertTrue(FileTagReader.read(ArraySource(ByteArray(64) { 7 })).isEmpty)
    }
}
