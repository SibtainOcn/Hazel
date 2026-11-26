package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryLinkOutputTest {

    @Test
    fun marksSplitOutputPerLinkAndUnreadableLinkHasNone() {
        val urls = listOf("https://a/1", "https://b/2", "https://c/3")
        val output = GalleryLinkOutput(urls, 10_000)
        val closed = listOf(
            "\u001ehttps://a/1", "\u001ehttps://b/2", "[", "  [3]", "]", "\u001ehttps://c/3", "[]"
        ).mapNotNull(output::feed)

        assertEquals(listOf(0, 1), closed)
        assertNull(output.part(0))
        assertEquals("[\n  [3]\n]\n", output.part(1))
        assertEquals("[]\n", output.part(2))
    }
}
