package com.hazel.android.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SplashScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val fullRunMs = SplashTimeline.TOTAL_MS.toLong() + 200L

    private var ready by mutableStateOf(true)
    private var homeShown by mutableStateOf(false)
    private var homeWantedAt = -1f
    private var done = false
    private var elapsed = 0L

    private fun launch(isReady: Boolean = true, autoShowHome: Boolean = true, dark: Boolean = true) {
        ready = isReady
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = dark) {
                SplashScreen(
                    ready = ready,
                    homeShown = homeShown,
                    onWantHome = {
                        homeWantedAt = elapsed.toFloat()
                        if (autoShowHome) homeShown = true
                    },
                    onFinished = { done = true }
                )
            }
        }
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(16L, left)
            compose.mainClock.advanceTimeBy(step)
            elapsed += step
            left -= step
        }
    }

    @Test
    fun firstFrameIsPureBlackInLightTheme() {
        launch(dark = false)
        compose.mainClock.advanceTimeByFrame()
        val pixels = compose.onNodeWithTag(SPLASH_TAG).captureToImage().toPixelMap()
        assertEquals(Color.Black, pixels[2, 2])
        assertEquals(Color.Black, pixels[pixels.width - 3, pixels.height - 3])
    }

    @Test
    fun asksForHomeOnlyAfterTheLogoSettlesThenFinishes() {
        launch()
        advance(SplashTimeline.HOME_AT_MS.toLong() - 100L)
        assertEquals(-1f, homeWantedAt)
        advance(fullRunMs)
        assertTrue(homeWantedAt >= SplashTimeline.HOME_AT_MS - 100L)
        assertTrue(done)
    }

    @Test
    fun holdsBeforeTheRevealUntilHomeHasDrawn() {
        launch(autoShowHome = false)
        advance(fullRunMs * 2)
        assertFalse(done)
        Snapshot.withMutableSnapshot { homeShown = true }
        advance(fullRunMs)
        assertTrue(done)
    }

    @Test
    fun holdsUntilSettingsAreReady() {
        launch(isReady = false)
        advance(fullRunMs * 2)
        assertFalse(done)
        Snapshot.withMutableSnapshot { ready = true }
        advance(fullRunMs)
        assertTrue(done)
    }

    @Test
    fun timelineIsSettledBeforeTheRevealAndOpenAtTheEnd() {
        val settled = SplashTimeline.REVEAL_AT_MS - 1f
        assertEquals(0f, SplashTimeline.trace(0f), 0f)
        assertEquals(1f, SplashTimeline.trace(settled), 0.001f)
        assertEquals(0f, SplashTimeline.spark(settled), 0f)
        for (i in 0 until 5) assertEquals(1f, SplashTimeline.letter(i, settled), 0.001f)
        assertEquals(0f, SplashTimeline.reveal(settled), 0f)
        assertEquals(1f, SplashTimeline.exitAlpha(settled), 0f)
        assertEquals(1f, SplashTimeline.reveal(SplashTimeline.TOTAL_MS), 0.001f)
        assertEquals(0f, SplashTimeline.exitAlpha(SplashTimeline.TOTAL_MS), 0.001f)
    }

    @Test
    fun logoHasLandedBeforeHomeIsBuilt() {
        val home = SplashTimeline.HOME_AT_MS
        assertEquals(1f, SplashTimeline.trace(home), 0.001f)
        assertEquals(0f, SplashTimeline.spark(home), 0f)
        for (i in 0 until 5) assertEquals(1f, SplashTimeline.letter(i, home), 0.001f)
        assertTrue(home < SplashTimeline.REVEAL_AT_MS)
    }
}
