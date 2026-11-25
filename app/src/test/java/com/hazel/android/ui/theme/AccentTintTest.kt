package com.hazel.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTintTest {

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `tonal ink reads on its tint for every accent in both themes`() {
        val weak = AccentColors.flatMap { accent ->
            listOf(true to accent.dark, false to accent.light).mapNotNull { (dark, primary) ->
                val fill = accentTint(primary, dark)
                val ratio = contrast(accentInk(primary, dark), fill)
                "${accent.name} ${if (dark) "dark" else "light"} %.2f".format(ratio).takeIf { ratio < 3f }
            }
        }
        assertTrue("Too faint: $weak", weak.isEmpty())
    }
}
