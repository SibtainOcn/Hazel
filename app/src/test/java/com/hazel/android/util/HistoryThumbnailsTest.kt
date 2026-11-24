package com.hazel.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryThumbnailsTest {

    private lateinit var context: Context

    private fun dir() = File(context.cacheDir, HistoryThumbnails.DIRECTORY_NAME)

    private fun put(name: String, bytes: Int = 10): File =
        File(dir().apply { mkdirs() }, name).apply { writeBytes(ByteArray(bytes) { 1 }) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        HistoryThumbnails.clearAll(context)
    }

    @After
    fun tearDown() {
        HistoryThumbnails.clearAll(context)
    }

    @Test
    fun `nothing saved reads as no copy`() = runBlocking {
        assertNull(HistoryThumbnails.cached(context, 1L))
    }

    @Test
    fun `a saved copy is found and an empty one is not trusted`() = runBlocking {
        put("1.jpg")
        put("2.jpg", bytes = 0)
        assertNotNull(HistoryThumbnails.cached(context, 1L))
        assertNull(HistoryThumbnails.cached(context, 2L))
    }

    @Test
    fun `discard removes only the named copy`() = runBlocking {
        put("1.jpg")
        put("2.jpg")
        HistoryThumbnails.discard(context, 1L)
        assertNull(HistoryThumbnails.cached(context, 1L))
        assertNotNull(HistoryThumbnails.cached(context, 2L))
    }

    @Test
    fun `delete removes the copy, the marker and a half written file`() {
        put("1.jpg"); put("1.none"); put("1.4821.part"); put("2.jpg"); put("12.jpg")
        HistoryThumbnails.delete(context, listOf(1L))
        assertEquals(setOf("2.jpg", "12.jpg"), dir().list()!!.toSet())
    }

    @Test
    fun `prune keeps entries still listed and drops everything else of ours`() {
        put("1.jpg"); put("2.jpg"); put("2.none"); put("3.77.part"); put("4.none")
        HistoryThumbnails.prune(context, keep = setOf(1L, 2L))
        assertEquals(setOf("1.jpg", "2.jpg", "2.none"), dir().list()!!.toSet())
    }

    @Test
    fun `prune removes stray files that are not named for an entry`() {
        put("1.jpg"); put("notes.txt")
        HistoryThumbnails.prune(context, keep = setOf(1L))
        assertEquals(listOf("1.jpg"), dir().list()!!.toList())
    }

    @Test
    fun `a file that gave nothing is left alone, then asked again after the wait`() {
        assertFalse(HistoryThumbnails.recentlyFoundNothing(context, 7L))
        HistoryThumbnails.markNothingFound(context, 7L)
        assertTrue(HistoryThumbnails.recentlyFoundNothing(context, 7L))

        val later = System.currentTimeMillis() + HistoryThumbnails.NOTHING_FOUND_TTL_MS + 1
        assertFalse(HistoryThumbnails.recentlyFoundNothing(context, 7L, now = later))
        assertFalse(File(dir(), "7.none").exists())
    }

    @Test
    fun `a blank address is never read`() = runBlocking {
        assertNull(HistoryThumbnails.fromSystem(context, 9L, fileUri = "", isVideo = true))
        assertFalse(File(dir(), "9.none").exists())
    }

    @Test
    fun `size counts what is kept and clearing empties it`() {
        assertEquals(0L, HistoryThumbnails.sizeBytes(context))
        put("1.jpg", bytes = 100)
        put("2.jpg", bytes = 50)
        assertEquals(150L, HistoryThumbnails.sizeBytes(context))
        HistoryThumbnails.clearAll(context)
        assertEquals(0L, HistoryThumbnails.sizeBytes(context))
        assertFalse(dir().exists())
    }

    @Test
    fun `reading the size never creates the folder`() {
        HistoryThumbnails.sizeBytes(context)
        assertFalse(dir().exists())
    }
}
