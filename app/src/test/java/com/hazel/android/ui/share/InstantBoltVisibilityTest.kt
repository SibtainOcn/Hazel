package com.hazel.android.ui.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.junit4.createComposeRule
import com.hazel.android.ui.screens.download.contrastOf
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
class InstantBoltVisibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cookie, resting bolt and shine are each visible for every accent, theme and glass`() {
        val weak = mutableListOf<String>()
        compose.setContent {
            for (dark in listOf(true, false)) for (glass in listOf(true, false)) for (accent in AccentColors) {
                HazelTheme(darkTheme = dark, accentName = accent.name, glass = glass) {
                    val s = MaterialTheme.colorScheme
                    val backdrop = if (glass) Color.White.copy(alpha = 0.05f).compositeOver(if (dark) Color.Black else Color.White)
                        else s.surfaceContainerHigh
                    val t = boltTones(s.primary, backdrop)
                    val where = "${accent.name} dark=$dark glass=$glass"
                    val cookie = contrastOf(t.cookie, backdrop)
                    val rest = contrastOf(t.dim, t.cookie)
                    val shine = contrastOf(t.shine, t.dim)
                    if (cookie < MIN_COOKIE) weak += "$where cookie on dialog: $cookie"
                    if (rest < MIN_REST) weak += "$where bolt on cookie: $rest"
                    if (shine < MIN_SHINE) weak += "$where shine on bolt: $shine"
                }
            }
        }
        compose.waitForIdle()
        assertTrue(weak.joinToString("\n"), weak.isEmpty())
    }

    private companion object {
        const val MIN_COOKIE = 1.15f
        const val MIN_REST = BOLT_MIN_REST_CONTRAST
        const val MIN_SHINE = 1.4f
    }
}
