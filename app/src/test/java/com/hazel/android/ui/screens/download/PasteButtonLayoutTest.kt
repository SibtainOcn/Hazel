package com.hazel.android.ui.screens.download

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Folded, the paste button is a square around its cookie, at the largest font too. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PasteButtonLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `folds to a square`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    PasteButton(stamp = 1L, onClick = {}, modifier = Modifier.testTag("paste"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("paste").assertWidthIsEqualTo(62.dp).assertHeightIsEqualTo(62.dp)
    }
}
