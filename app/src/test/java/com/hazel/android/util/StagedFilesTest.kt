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
    fun `flat folder lists files with no sub path`() {
        file("b.mp4"); file("a.mp4")
        assertEquals(listOf("" to "a.mp4", "" to "b.mp4"), listed())
    }

    @Test
    fun `files in folders carry the folders they sit in`() {
        file("top.mp4"); file("Uploader/id1.mp4"); file("Uploader/Sub/id2.mp4")
        assertEquals(
            setOf("" to "top.mp4", "Uploader" to "id1.mp4", "Uploader/Sub" to "id2.mp4"),
            listed().toSet()
        )
    }

    @Test
    fun `missing folder lists nothing`() {
        assertEquals(emptyList<StagedFile>(), StagedFiles.list(File(root, "nope")))
    }

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
    fun `a size-only walk skips the link checks and still ends`() {
        val ok = runCatching {
            // A folder that links back to its parent: followed, so only the depth cap stops it.
            Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        }.isSuccess
        assumeTrue(ok)
        file("real.mp4")
        val files = StagedFiles.list(root, maxDepth = 3, checkLinks = false)
        assertTrue(files.any { it.file.name == "real.mp4" && it.subPath.isEmpty() })
        assertTrue(files.size <= 4)
    }

    @Test
    fun `a work folder reached through a link is still read`() {
        // Reached through a link such as /sdcard: only entries inside the folder count as links.
        val holder = Files.createTempDirectory("holder").toFile()
        try {
            val viaLink = File(holder, "via")
            val ok = runCatching { Files.createSymbolicLink(viaLink.toPath(), root.toPath()) }.isSuccess
            assumeTrue(ok)
            file("a/one.mp4")
            file("two.mp4")
            assertEquals(
                listOf("" to "two.mp4", "a" to "one.mp4"),
                StagedFiles.list(viaLink).map { it.subPath to it.file.name }.sortedBy { it.first }
            )
        } finally {
            holder.deleteRecursively()
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

    @Test
    fun `pruning a missing folder does nothing`() {
        StagedFiles.pruneEmptyFolders(File(root, "nope"))
    }

    // -- publishing: folder names and how deep to look --

    @Test
    fun `a folder name is made safe to publish`() {
        assertEquals("Some Band", StagedFiles.publishName("Some Band"))
        assertEquals("Foo", StagedFiles.publishName("Foo."))
        assertEquals("Foo", StagedFiles.publishName("Foo. . "))
        assertEquals("Foo", StagedFiles.publishName("  Foo"))
        assertEquals("a_b_c", StagedFiles.publishName("a:b?c"))
        assertEquals("_", StagedFiles.publishName("..."))
        assertEquals("_", StagedFiles.publishName(" "))
        assertEquals("_hidden", StagedFiles.publishName(".hidden"))
        assertEquals("tab_here", StagedFiles.publishName("tab\there"))
        assertEquals("2026 - 11", StagedFiles.publishName("2026 - 11"))
    }

    @Test
    fun `a sub path is made safe one folder at a time`() {
        assertEquals("", StagedFiles.publishSubPath(""))
        assertEquals("Band/Album", StagedFiles.publishSubPath("Band/Album"))
        assertEquals("Band/Album", StagedFiles.publishSubPath("Band./Album ."))
        assertEquals("_/x", StagedFiles.publishSubPath("../x"))
    }

    @Test
    fun `depth zero lists only the files directly in the folder`() {
        file("top.mp4")
        file("a/inner.mp4")
        file("a/b/deeper.mp4")
        assertEquals(listOf("" to "top.mp4"), StagedFiles.list(root, maxDepth = 0).map { it.subPath to it.file.name })
        assertEquals(3, StagedFiles.list(root).size)
    }
}
