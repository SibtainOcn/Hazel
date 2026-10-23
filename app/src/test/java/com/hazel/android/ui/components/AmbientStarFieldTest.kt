package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AmbientDotGridTest(private val dark: Boolean, private val glass: Boolean, private val style: GlassStyle) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} glass={1} {2}")
        fun looks() = listOf(
            arrayOf<Any>(true, true, GlassStyle.FOREST),
            arrayOf<Any>(true, true, GlassStyle.AURORA),
            arrayOf<Any>(false, true, GlassStyle.FOREST),
            arrayOf<Any>(true, false, GlassStyle.FOREST),
            arrayOf<Any>(false, false, GlassStyle.FOREST)
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val background get() = if (dark) Color.Black else Color.White

    /** How far the middle of the grid is from a blank background: more visible dots, more ink. */
    private fun inkNearCentre(): Float {
        val map = compose.onNodeWithTag("grid").captureToImage().toPixelMap()
        val cx = map.width / 2
        val cy = map.height / 2
        var sum = 0f
        for (y in cy - 60 until cy + 60) for (x in cx - 60 until cx + 60) {
            val p = map[x, y]
            sum += abs(p.red - background.red) + abs(p.green - background.green) + abs(p.blue - background.blue)
        }
        return sum
    }

    @Test
    fun `dots show at rest, strengthen under a finger and settle after`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass, glassStyle = style) {
                Box(Modifier.size(320.dp).background(background)) {
                    AmbientDotGrid(Modifier.fillMaxSize().testTag("grid"), ambient = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val rest = inkNearCentre()
        assertTrue("dots are invisible at rest", rest > 0f)

        compose.onNodeWithTag("grid").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        val touched = inkNearCentre()
        assertTrue("a touch did not strengthen the dots ($rest -> $touched)", touched > rest * 1.5f)

        compose.onNodeWithTag("grid").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000)
        val settled = inkNearCentre()
        assertTrue("dots did not settle after the finger lifted ($touched -> $settled)", settled < touched * 0.7f)
    }

    @Test
    fun `untouched, the glow drifts across the grid on its own`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass, glassStyle = style) {
                Box(Modifier.size(320.dp).background(background)) {
                    AmbientDotGrid(Modifier.fillMaxSize().testTag("grid"), ambient = true)
                }
            }
        }
        val frames = (0..6).map { step ->
            compose.mainClock.advanceTimeBy(if (step == 0) 100 else 3_000)
            compose.onNodeWithTag("grid").captureToImage().toPixelMap().let { map -> IntArray(4) { q ->
                val x = if (q % 2 == 0) map.width / 4 else map.width * 3 / 4
                val y = if (q < 2) map.height / 3 else map.height * 2 / 3
                var hit = 0
                for (dy in -40 until 40) for (dx in -40 until 40) {
                    val p = map[x + dx, y + dy]
                    if (abs(p.red - background.red) + abs(p.green - background.green) + abs(p.blue - background.blue) > 0.15f) hit++
                }
                hit
            } }
        }
        val changed = frames.zipWithNext().count { (a, b) -> !a.contentEquals(b) }
        assertTrue("the grid did not move on its own", changed >= 3)
    }
}
