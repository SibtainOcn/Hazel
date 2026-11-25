package com.hazel.android.ui.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionTest {

    @Test
    fun `leaving Home moves the video to the mini player and keeps it playing`() {
        val session = PlayerSession()
        session.play("a")
        session.dock.onCardGone("a", fullscreen = false)

        assertEquals("a", session.playingUrl)
        assertEquals(PlayerPlace.MINI, session.dock.place)
        assertTrue(session.outOfCard)

        session.stop()
        assertNull(session.playingUrl)
        assertFalse(session.outOfCard)
    }
}
