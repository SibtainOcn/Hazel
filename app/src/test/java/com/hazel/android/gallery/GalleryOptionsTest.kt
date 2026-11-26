package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryOptionsTest {



    @Test
    fun `every choice maps to its gallery-dl flag`() {
        val args = GalleryOptions(
            imageSize = GalleryImageSize.LARGE,
            videoMode = GalleryVideoMode.SINGLE,
            filenameTemplate = "{username}_{num}.{extension}",
            writeMetadata = true,
            keepPostDate = false,
            packZip = true,
            skipDownloaded = true,
            retries = 2
        ).toArgs(File("/data/archive.sqlite3"))
        assertEquals("2", args[args.indexOf("--retries") + 1])
        assertTrue("extractor.twitter.size=large,medium,small" in args)
        assertTrue("extractor.instagram.videos=merged" in args)
        assertEquals("{username}_{num}.{extension}", args[args.indexOf("--filename") + 1])
        assertTrue("--write-metadata" in args)
        assertTrue("--no-mtime" in args)
        assertTrue("--zip" in args)
        assertTrue(args[args.indexOf("--download-archive") + 1].replace('\\', '/').endsWith("/data/archive.sqlite3"))
    }

    @Test
    fun `no archive is kept without a file, as in incognito`() {
        assertFalse("--download-archive" in GalleryOptions(skipDownloaded = true).toArgs(null))
    }


}
