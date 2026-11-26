package com.hazel.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * yt-dlp builds are a zip behind a shebang line. Android's ZipFile rejects that layout,
 * which the desktop JVM accepts, so these cases pin the check to the real file shape.
 */
class YtDlpBinaryCheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shebang = "#!/usr/bin/env python3\n".toByteArray()

    private fun zipBytes(vararg names: String, comment: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            comment?.let { zip.setComment(it) }
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                // Incompressible filler so the file clears the minimum size check.
                zip.write(Random(name.hashCode()).nextBytes(300_000))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun write(bytes: ByteArray): File = tmp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `accepts a zip behind a shebang line`() {
        val file = write(shebang + zipBytes("yt_dlp/__init__.py", "yt_dlp/YoutubeDL.py", "__main__.py"))
        assertTrue(YtDlpUpdater.isValidBinary(file))
    }





    @Test
    fun `rejects a cut short download`() {
        val whole = shebang + zipBytes("yt_dlp/__init__.py", "yt_dlp/YoutubeDL.py", "__main__.py")
        assertFalse(YtDlpUpdater.isValidBinary(write(whole.copyOf(whole.size - 5_000))))
    }



    @Test
    fun `rejects a zip that is not yt-dlp`() {
        assertFalse(YtDlpUpdater.isValidBinary(write(shebang + zipBytes("a.txt", "b.txt"))))
    }


}
