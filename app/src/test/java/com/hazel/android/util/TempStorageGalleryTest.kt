package com.hazel.android.util

import com.hazel.android.gallery.GalleryLogs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class TempStorageGalleryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun File.fill(name: String, bytes: Int) = File(this, name).apply {
        parentFile?.mkdirs()
        writeBytes(ByteArray(bytes))
    }

    @Test
    fun galleryWorkIsCountedOnceUnderGallery() {
        val temp = folder.newFolder("Hazel")
        val card = folder.newFolder("card-work")
        val cache = folder.newFolder("cache")
        temp.fill("video-1/clip.mp4.part", 1_000)
        temp.fill("gallery-1234/a.jpg", 300)
        temp.fill("gallery-5678/b.jpg", 200)
        card.fill("gallery-9/c.jpg", 50)
        card.fill("other/d.part", 70)
        cache.fill("gallery-dl.sqlite3", 40)
        cache.fill("${GalleryLogs.DIRECTORY_NAME}/gallery-1-abc.log", 10)
        cache.fill("cookies.txt", 999)
        cache.fill("yt-dlp/x", 999)

        assertEquals(1_070L, TempStorage.partialSize(listOf(temp, card)))
        val gallery = TempStorage.galleryFiles(listOf(temp, card), cache)
        assertEquals(
            setOf("gallery-1234", "gallery-5678", "gallery-9", "gallery-dl.sqlite3", GalleryLogs.DIRECTORY_NAME),
            gallery.map { it.name }.toSet()
        )
        assertEquals(600L, gallery.sumOf { f -> f.walkBottomUp().filter { it.isFile }.sumOf { it.length() } })
    }

    @Test
    fun aGalleryNamedFileIsNotAWorkFolder() {
        val temp = folder.newFolder("Hazel")
        temp.fill("gallery-notes.txt", 25)
        assertEquals(25L, TempStorage.partialSize(listOf(temp)))
        assertEquals(emptyList(), TempStorage.galleryFiles(listOf(temp), folder.newFolder("cache")))
    }
}
