package com.hazel.android.ui.screens.download

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatBadgeContrastTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the format block's label reads on its fill for every accent, theme and glass`() {
        val weak = mutableListOf<String>()
        compose.setContent {
            for (dark in listOf(true, false)) for (glass in listOf(true, false)) for (accent in AccentColors) {
                HazelTheme(darkTheme = dark, accentName = accent.name, glass = glass) {
                    val s = MaterialTheme.colorScheme
                    val top = contrastOf(s.onPrimary, s.primary)
                    val bottom = contrastOf(s.onPrimary, badgeFillEnd(s.primary, s.primaryContainer, s.onPrimary))
                    // The gradient never makes the label harder to read than the flat fill was.
                    if (bottom < minOf(top, BADGE_MIN_CONTRAST)) weak += "${accent.name} dark=$dark glass=$glass: $bottom"
                }
            }
        }
        compose.waitForIdle()
        assertTrue(weak.joinToString("\n"), weak.isEmpty())
    }
}
