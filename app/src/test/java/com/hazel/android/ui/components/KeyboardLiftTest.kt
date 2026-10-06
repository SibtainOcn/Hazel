package com.hazel.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet's keyboard lift across the devices and settings it meets: screens from 720p to
 * tall phones and tablets, portrait and landscape, keyboards from a third of the screen to
 * two thirds, fields from the top of the sheet to its foot, and densities from mdpi to
 * xxxhdpi for the gap. Each keyboard is replayed frame by frame as it slides in and out,
 * with the field read back from where the lifted sheet put it, as it is on a device.
 */
class KeyboardLiftTest {

    private val none = SheetKeyboard.NONE

    // Window heights in pixels: 720p, 1080p and tall phones, a tablet, then landscape.
    private val portrait = listOf(1280, 1920, 2160, 2340, 2400, 2412, 3120, 3200, 2560)
    private val landscape = listOf(720, 1080, 1220, 1440, 1600)
    private val keyboardShares = listOf(0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65)
    private val fieldShares = listOf(0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95)
    // 12dp at mdpi, hdpi, xhdpi, xxhdpi, the phone on the bench (2.75), and xxxhdpi.
    private val gaps = listOf(12, 18, 24, 33, 36, 48)
    private val fieldHeights = listOf(60, 120, 180)

    private class Frame(val lift: Int, val fieldBottomOnScreen: Int, val keyboardTop: Int)

    /**
     * Replays one keyboard opening and closing, as the sheet sees it: the keyboard's top
     * edge each frame, the field measured where the lifted sheet placed it, and the lift
     * worked out from both. Returns every frame.
     */
    private fun replay(screen: Int, keyboard: Int, fieldBottom: Int, gap: Int, steps: Int = 24): List<Frame> {
        val frames = mutableListOf<Frame>()
        var applied = 0
        // Up, held, then down again.
        val heights = (0..steps).map { keyboard * it / steps } +
            List(4) { keyboard } +
            (steps downTo 0).map { keyboard * it / steps }
        for (height in heights) {
            val keyboardTop = if (height > 0) screen - height else none
            // The field is measured where the sheet now is, then read back as unlifted.
            val measured = fieldBottom - applied
            val unlifted = measured + applied
            val lift = keyboardLift(unlifted, keyboardTop, gap)
            applied = lift
            frames += Frame(lift, fieldBottom - lift, keyboardTop)
        }
        return frames
    }

    private fun everyDevice(check: (screen: Int, keyboard: Int, fieldBottom: Int, gap: Int, fieldHeight: Int) -> Unit) {
        for (screen in portrait + landscape) for (share in keyboardShares) for (field in fieldShares)
            for (gap in gaps) for (fieldHeight in fieldHeights) {
                val fieldBottom = (screen * field).toInt()
                if (fieldBottom - fieldHeight < 0) continue
                check(screen, (screen * share).toInt(), fieldBottom, gap, fieldHeight)
            }
    }

    @Test
    fun `the field ends clear of the keyboard on every device`() {
        var cases = 0
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            val open = replay(screen, keyboard, fieldBottom, gap)[24]
            assertTrue(
                "screen $screen, keyboard $keyboard, field $fieldBottom: field at ${open.fieldBottomOnScreen}, keyboard at ${open.keyboardTop}",
                open.fieldBottomOnScreen + gap <= open.keyboardTop
            )
            cases++
        }
        assertTrue("ran $cases cases", cases > 10_000)
    }

    @Test
    fun `the sheet does not move while the field is clear of the keyboard`() {
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            if (fieldBottom + gap <= screen - keyboard) {
                replay(screen, keyboard, fieldBottom, gap).forEach { assertEquals(0, it.lift) }
            }
        }
    }

    @Test
    fun `the lift is only the overlap, never the whole keyboard`() {
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            val lift = replay(screen, keyboard, fieldBottom, gap)[24].lift
            assertEquals(maxOf(0, fieldBottom + gap - (screen - keyboard)), lift)
            assertTrue(lift <= keyboard + gap)
        }
    }

    @Test
    fun `the lift follows the keyboard smoothly, without jumping back and forth`() {
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            val frames = replay(screen, keyboard, fieldBottom, gap)
            val opening = frames.subList(0, 25)
            val closing = frames.subList(28, frames.size)
            opening.zipWithNext().forEach { (a, b) -> assertTrue(b.lift >= a.lift) }
            closing.zipWithNext().forEach { (a, b) -> assertTrue(b.lift <= a.lift) }
            // Each step is no bigger than the keyboard's own step that frame, plus the gap
            // once, for a field already within the gap of the window's foot.
            frames.zipWithNext().forEach { (a, b) ->
                assertTrue(kotlin.math.abs(b.lift - a.lift) <= keyboard / 24 + 1 + gap)
            }
        }
    }

    @Test
    fun `the sheet is back where it was once the keyboard closes`() {
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            assertEquals(0, replay(screen, keyboard, fieldBottom, gap).last().lift)
        }
    }

    @Test
    fun `a field with room above the keyboard is wholly in view`() {
        everyDevice { screen, keyboard, fieldBottom, gap, fieldHeight ->
            val open = replay(screen, keyboard, fieldBottom, gap)[24]
            if (screen - keyboard - gap >= fieldHeight) {
                val top = open.fieldBottomOnScreen - fieldHeight
                assertTrue("field top $top off screen", top >= 0)
            }
        }
    }

    @Test
    fun `reading the field back from the lifted sheet settles at once`() {
        // A lift that moved the field and then measured it there must give the same lift.
        everyDevice { screen, keyboard, fieldBottom, gap, _ ->
            val top = screen - keyboard
            val first = keyboardLift(fieldBottom, top, gap)
            val again = keyboardLift((fieldBottom - first) + first, top, gap)
            assertEquals(first, again)
        }
    }

    @Test
    fun `nothing is lifted without a keyboard or a focused field`() {
        assertEquals(0, keyboardLift(none, 1500, 33))
        assertEquals(0, keyboardLift(1400, none, 33))
        assertEquals(0, keyboardLift(none, none, 33))
    }

    @Test
    fun `extreme values do not overflow`() {
        assertEquals(0, keyboardLift(Int.MIN_VALUE + 1, Int.MAX_VALUE, 48))
        assertTrue(keyboardLift(Int.MAX_VALUE, 0, 48) >= 0)
        assertEquals(Int.MAX_VALUE, keyboardLift(Int.MAX_VALUE, 0, 48))
    }
}
