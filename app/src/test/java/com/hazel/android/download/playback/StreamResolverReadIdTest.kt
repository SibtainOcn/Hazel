package com.hazel.android.download.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverReadIdTest {

    @Test
    fun `every play read gets its own process id`() {
        val ids = List(50) { StreamResolver.newReadId() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("${StreamResolver.PLAY_PROCESS_ID}_") })
    }

    @Test
    fun `stopping a read that is not running is harmless`() {
        StreamResolver.stopRead(StreamResolver.newReadId())
    }
}
