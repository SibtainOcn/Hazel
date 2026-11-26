package com.hazel.android.util

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class StagedFilesTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("staged").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(path: String, text: String = "x"): File =
        File(root, path).also { it.parentFile?.mkdirs(); it.writeText(text) }

    private fun listed() = StagedFiles.list(root).map { it.subPath to it.file.name }







    @Test
    fun `depth is capped`() {
        file("a/b/c/d.mp4")
        assertEquals(listOf("a/b/c" to "d.mp4"), StagedFiles.list(root, maxDepth = 3).map { it.subPath to it.file.name })
        assertEquals(emptyList<StagedFile>(), StagedFiles.list(root, maxDepth = 2))
    }

    @Test
    fun `links are not followed`() {
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            File(outside, "secret.txt").writeText("s")
            val ok = runCatching {
                Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
                Files.createSymbolicLink(File(root, "file-link").toPath(), File(outside, "secret.txt").toPath())
            }.isSuccess
            assumeTrue(ok)
            file("real.mp4")
            assertEquals(listOf("" to "real.mp4"), listed())
            assertTrue(StagedFiles.isLink(File(root, "link")))
            assertFalse(StagedFiles.isLink(File(root, "real.mp4")))
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `a skipped link is reported, not silently lost`() {
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            File(outside, "secret.txt").writeText("s")
            val ok = runCatching {
                Files.createSymbolicLink(File(root, "dir-link").toPath(), outside.toPath())
                Files.createSymbolicLink(File(root, "file-link").toPath(), File(outside, "secret.txt").toPath())
            }.isSuccess
            assumeTrue(ok)
            file("real.mp4")
            val skipped = mutableListOf<String>()
            val found = StagedFiles.list(root, onSkipped = { skipped += it.name }).map { it.file.name }
            assertEquals(listOf("real.mp4"), found)
            assertEquals(listOf("dir-link", "file-link"), skipped.sorted())
        } finally {
            outside.deleteRecursively()
        }
    }





    @Test
    fun `pruning removes empty folders, keeps those with files, and keeps the root`() {
        File(root, "empty/deeper").mkdirs()
        file("full/keep.mp4")
        File(root, "full/emptyToo").mkdirs()
        StagedFiles.pruneEmptyFolders(root)
        assertFalse(File(root, "empty").exists())
        assertFalse(File(root, "full/emptyToo").exists())
        assertTrue(File(root, "full/keep.mp4").exists())
        assertTrue(root.exists())
    }



    // -- publishing: folder names and how deep to look --







    @Test
    fun `leading dots leave the title, not the extension`() {
        kotlin.test.assertEquals("And Justice for All.mp4", StagedFiles.visibleName("...And Justice for All.mp4"))
        kotlin.test.assertEquals("And.en.vtt", StagedFiles.visibleName("...And.en.vtt"))
        kotlin.test.assertEquals("_.mp4", StagedFiles.visibleName(".mp4"))
    }

    @Test
    fun `hidden files are renamed in place, also in folders, without overwriting`() {
        val root = kotlin.io.path.createTempDirectory("unhide").toFile()
        try {
            java.io.File(root, "Uploader").mkdirs()
            java.io.File(root, "...Song.mp3").writeText("a")
            java.io.File(root, "Uploader/.Clip.mp4").writeText("b")
            java.io.File(root, "Uploader/Clip.mp4").writeText("taken")
            java.io.File(root, "plain.mp4").writeText("c")
            StagedFiles.unhideFiles(root)
            kotlin.test.assertEquals("a", java.io.File(root, "Song.mp3").readText())
            kotlin.test.assertEquals("b", java.io.File(root, "Uploader/_Clip.mp4").readText())
            kotlin.test.assertEquals("taken", java.io.File(root, "Uploader/Clip.mp4").readText())
            kotlin.test.assertTrue(java.io.File(root, "plain.mp4").exists())
            kotlin.test.assertTrue(StagedFiles.list(root).none { it.file.name.startsWith(".") })
        } finally {
            root.deleteRecursively()
        }
    }
}
