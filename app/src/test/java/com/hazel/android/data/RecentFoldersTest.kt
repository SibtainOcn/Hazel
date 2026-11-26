package com.hazel.android.data

import com.hazel.android.data.RecentFolders.with
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFoldersTest {

    @Test
    fun `newest first, no repeats, at most five`() {
        var list = emptyList<SaveDir>()
        (1..7).forEach { list = list.with("content://tree/$it", "Folder $it") }
        list = list.with("content://tree/4", "Folder 4 again")
        assertEquals(listOf("4", "7", "6", "5", "3"), list.map { it.uri.substringAfterLast('/') })
        assertEquals("Folder 4 again", list.first().label)
    }

    @Test
    fun `survives a round trip and a label that holds tabs or new lines`() {
        val list = listOf(SaveDir("content://tree/a", "Movies\tand\nmore"), SaveDir("content://tree/b", ""))
        val back = RecentFolders.decode(RecentFolders.encode(list))
        assertEquals(listOf("content://tree/a", "content://tree/b"), back.map { it.uri })
        assertEquals("Movies and more", back[0].label)
        assertEquals("", back[1].label)
    }

    @Test
    fun `blank or broken stored values read as nothing`() {
        assertEquals(emptyList<SaveDir>(), RecentFolders.decode(null))
        assertEquals(emptyList<SaveDir>(), RecentFolders.decode(""))
        assertEquals(listOf("content://tree/x"), RecentFolders.decode("\n\t label\ncontent://tree/x\n").map { it.uri })
    }
}
