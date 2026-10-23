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
class AmbientStarFieldTest(
    private val dark: Boolean,
    private val glass: Boolean,
    private val style: GlassStyle,
    private val accent: String
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} glass={1} {2} {3}")
        fun looks() = listOf(
            arrayOf<Any>(true, true, GlassStyle.FOREST, "White"),
            arrayOf<Any>(true, true, GlassStyle.AURORA, "Cyan"),
            arrayOf<Any>(true, true, GlassStyle.NEBULA, "Violet"),
            arrayOf<Any>(true, true, GlassStyle.MONO, "White"),
            arrayOf<Any>(false, true, GlassStyle.FOREST, "White"),
            arrayOf<Any>(false, true, GlassStyle.SUNSET, "Amber"),
            arrayOf<Any>(false, true, GlassStyle.MONO, "White"),
            arrayOf<Any>(true, false, GlassStyle.FOREST, "White"),
            arrayOf<Any>(true, false, GlassStyle.FOREST, "Slate"),
            arrayOf<Any>(false, false, GlassStyle.FOREST, "White"),
            arrayOf<Any>(false, false, GlassStyle.FOREST, "Lime")
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val background get() = if (dark) Color.Black else Color.White

    /** How far the middle of the field is from a blank background: more visible stars, more ink. */
    private fun inkNearCentre(): Float {
        val map = compose.onNodeWithTag("stars").captureToImage().toPixelMap()
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
    fun `stars show at rest, brighten under a finger and settle after`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = glass, glassStyle = style) {
                Box(Modifier.size(320.dp).background(background)) {
                    AmbientStarField(Modifier.fillMaxSize().testTag("stars"), ambient = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val rest = inkNearCentre()
        assertTrue("stars are invisible at rest", rest > 0f)

        compose.onNodeWithTag("stars").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        val touched = inkNearCentre()
        assertTrue("a touch did not brighten the stars ($rest -> $touched)", touched > rest * 1.5f)

        compose.onNodeWithTag("stars").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000)
        val settled = inkNearCentre()
        assertTrue("stars did not settle after the finger lifted ($touched -> $settled)", settled < touched * 0.7f)
    }

    @Test
    fun `untouched, the stars drift on their own`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = glass, glassStyle = style) {
                Box(Modifier.size(320.dp).background(background)) {
                    AmbientStarField(Modifier.fillMaxSize().testTag("stars"), ambient = true)
                }
            }
        }
        val frames = (0..6).map { step ->
            compose.mainClock.advanceTimeBy(if (step == 0) 100 else 3_000)
            compose.onNodeWithTag("stars").captureToImage().toPixelMap().let { map -> IntArray(4) { q ->
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
        assertTrue("the stars did not move on their own", changed >= 3)
    }
}
