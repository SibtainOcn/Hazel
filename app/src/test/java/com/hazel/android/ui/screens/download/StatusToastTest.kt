package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.screens.more.SuggestionsConsentDialog
import com.hazel.android.ui.theme.HazelTheme
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
class StatusToastTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a message shows, then goes on its own`() {
        val state = StatusToastState()
        compose.setContent { HazelTheme(darkTheme = true, glass = true) { StatusToastHost(state) } }
        compose.runOnIdle { state.show("Search history cleared") }
        compose.onNodeWithText("Search history cleared").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(FEEDBACK_MS + 1_000)
        compose.onNodeWithText("Search history cleared").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de-w320dp-h568dp")
    fun `the suggestions question keeps both buttons on screen at the largest font`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    SuggestionsConsentDialog(onConfirm = {}, onDismiss = {})
                }
            }
        }
        val screen = context.resources.displayMetrics
        listOf(R.string.search_suggestions_confirm, R.string.download_cancel).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id)).assertIsDisplayed().fetchSemanticsNode()
            assertTrue(node.positionOnScreen.x + node.size.width <= screen.widthPixels)
            assertTrue(node.positionOnScreen.y + node.size.height <= screen.heightPixels)
        }
    }
}
