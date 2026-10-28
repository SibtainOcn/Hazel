package com.hazel.android.ui.components.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerDockTest {

    private val view = Rect(0f, 100f, 400f, 900f)
    private fun cardAt(top: Float) = Rect(20f, top, 380f, top + 200f)

    private fun playing(link: String = "a") = PlayerDock().apply {
        viewport = view
        start(link)
    }

    @Test
    fun scrollingMostOfTheCardAwayMovesToMiniAndBackDocks() {
        val dock = playing()
        dock.onCardMoved("a", cardAt(300f), canDock = true, fullscreen = false)
        assertEquals(PlayerPlace.CARD, dock.place)

        dock.onCardMoved("a", cardAt(-30f), canDock = true, fullscreen = false)
        assertEquals(PlayerPlace.MINI, dock.place)

        dock.onCardMoved("a", cardAt(150f), canDock = true, fullscreen = false)
        assertTrue(dock.docking)
        dock.docked()
        assertEquals(PlayerPlace.CARD, dock.place)
        assertFalse(dock.docking)
    }

    @Test
    fun aCardHalfInViewKeepsTheVideo() {
        val dock = playing()
        dock.onCardMoved("a", cardAt(0f), canDock = true, fullscreen = false)
        assertEquals(PlayerPlace.CARD, dock.place)
    }

    @Test
    fun aDownloadMovesToMiniAndBlocksDockingUntilDone() {
        val dock = playing()
        dock.toMini("a")
        assertEquals(PlayerPlace.MINI, dock.place)
        dock.onCardMoved("a", cardAt(300f), canDock = false, fullscreen = false)
        assertFalse(dock.docking)
        dock.onCardMoved("a", cardAt(300f), canDock = true, fullscreen = false)
        assertTrue(dock.docking)
    }

    @Test
    fun fullScreenNeverSendsToMini() {
        val dock = playing()
        dock.onCardMoved("a", cardAt(-400f), canDock = true, fullscreen = true)
        dock.onCardGone("a", fullscreen = true)
        assertEquals(PlayerPlace.CARD, dock.place)
    }

    @Test
    fun anotherLinksCardLeavingChangesNothing() {
        val dock = playing("b")
        dock.onCardGone("a", fullscreen = false)
        dock.onCardMoved("a", cardAt(-400f), canDock = true, fullscreen = false)
        dock.toMini("a")
        assertEquals(PlayerPlace.CARD, dock.place)
    }

    @Test
    fun expandsOnlyFromMiniAndCollapsesBack() {
        val dock = playing()
        dock.expand()
        assertEquals(PlayerPlace.CARD, dock.place)
        dock.onCardGone("a", fullscreen = false)
        dock.expand()
        assertEquals(PlayerPlace.LARGE, dock.place)
        dock.onCardMoved("a", cardAt(300f), canDock = true, fullscreen = false)
        assertFalse(dock.docking)
        dock.collapse()
        assertEquals(PlayerPlace.MINI, dock.place)
    }

    @Test
    fun theLargeViewTakesTheVideoOnlyOnceShown() {
        val dock = playing()
        dock.onCardGone("a", fullscreen = false)
        dock.largeShown()
        assertFalse(dock.largeReady)
        dock.expand()
        assertFalse(dock.largeReady)
        dock.largeShown()
        assertTrue(dock.largeReady)
        dock.collapse()
        assertFalse(dock.largeReady)
        dock.expand()
        assertFalse(dock.largeReady)
    }

    @Test
    fun scrollingAwayMidFlightCancelsTheDock() {
        val dock = playing()
        dock.onCardGone("a", fullscreen = false)
        dock.onCardMoved("a", cardAt(300f), canDock = true, fullscreen = false)
        assertTrue(dock.docking)
        dock.onCardMoved("a", cardAt(-150f), canDock = true, fullscreen = false)
        assertFalse(dock.docking)
        assertEquals(PlayerPlace.MINI, dock.place)
    }

    @Test
    fun stoppingResets() {
        val dock = playing()
        dock.onCardGone("a", fullscreen = false)
        dock.start(null)
        assertEquals(PlayerPlace.CARD, dock.place)
        dock.onCardGone("a", fullscreen = false)
        assertEquals(PlayerPlace.CARD, dock.place)
    }

    @Test
    fun visibleShareOfACard() {
        assertEquals(1f, PlayerDock.visibleFraction(cardAt(300f), view), 0.001f)
        assertEquals(0.5f, PlayerDock.visibleFraction(cardAt(0f), view), 0.001f)
        assertEquals(0f, PlayerDock.visibleFraction(cardAt(-300f), view), 0.001f)
    }
}
