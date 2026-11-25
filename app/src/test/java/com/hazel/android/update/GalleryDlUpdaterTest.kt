package com.hazel.android.update

import com.hazel.android.gallery.GalleryDlPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GalleryDlUpdaterTest {

    private val sha = "a".repeat(64)

    private fun feed(
        version: String = "1.33.0",
        url: String = "https://files.pythonhosted.org/packages/ab/gallery_dl-1.33.0-py3-none-any.whl",
        filename: String = "gallery_dl-1.33.0-py3-none-any.whl",
        type: String = "bdist_wheel",
        digest: String = sha,
        size: Long = 900_000,
        yanked: Boolean = false
    ) = """
        {"info": {"version": "$version"}, "urls": [
          {"packagetype": "sdist", "filename": "gallery_dl-$version.tar.gz", "url": "https://files.pythonhosted.org/x.tar.gz",
           "digests": {"sha256": "$sha"}, "size": 500000, "yanked": false},
          {"packagetype": "$type", "filename": "$filename", "url": "$url",
           "digests": {"sha256": "$digest"}, "size": $size, "yanked": $yanked}
        ]}
    """.trimIndent()

    @Test
    fun `the wheel of the latest release is read with its hash and size`() {
        val release = GalleryDlUpdater.parseRelease(feed())!!
        assertEquals("1.33.0", release.version)
        assertTrue(release.url.endsWith("py3-none-any.whl"))
        assertEquals(sha, release.sha256)
        assertEquals(900_000L, release.size)
    }

    @Test
    fun `a release Hazel cannot trust or use is refused`() {
        assertNull(GalleryDlUpdater.parseRelease(feed(url = "https://example.com/gallery_dl-1.33.0-py3-none-any.whl")))
        assertNull(GalleryDlUpdater.parseRelease(feed(url = "http://files.pythonhosted.org/gallery_dl-1.33.0-py3-none-any.whl")))
        assertNull(GalleryDlUpdater.parseRelease(feed(digest = "nothex")))
        assertNull(GalleryDlUpdater.parseRelease(feed(yanked = true)))
        assertNull(GalleryDlUpdater.parseRelease(feed(size = 0)))
        assertNull(GalleryDlUpdater.parseRelease(feed(size = 50L * 1024 * 1024)))
        assertNull(GalleryDlUpdater.parseRelease(feed(filename = "gallery_dl-1.33.0-cp312-abi3-linux.whl")))
        assertNull(GalleryDlUpdater.parseRelease(feed(version = "1.33.0rc1")))
        assertNull(GalleryDlUpdater.parseRelease("not json"))
    }

    @Test
    fun `versions compare by number, not text`() {
        assertTrue(GalleryDlUpdater.isNewer("1.32.22", "1.32.21"))
        assertTrue(GalleryDlUpdater.isNewer("1.33.0", "1.32.21"))
        assertTrue(GalleryDlUpdater.isNewer("1.32.100", "1.32.21"))
        assertFalse(GalleryDlUpdater.isNewer("1.32.21", "1.32.21"))
        assertFalse(GalleryDlUpdater.isNewer("1.32.9", "1.32.21"))
        assertFalse(GalleryDlUpdater.isNewer("garbage", "1.32.21"))
    }

    @Test
    fun `the bundled version matches the pinned payload`() {
        val pinned = File("../tools/gallery-dl/requirements.txt").takeIf { it.isFile }
            ?: File("tools/gallery-dl/requirements.txt")
        val line = pinned.readLines().first { it.startsWith("gallery-dl==") }
        assertEquals(line.substringAfter("==").substringBefore(' '), GalleryDlPayload.BUNDLED_VERSION)
    }
}
