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










}
