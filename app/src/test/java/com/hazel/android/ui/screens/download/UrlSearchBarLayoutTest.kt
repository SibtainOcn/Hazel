package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The search icon's cookie never covers the hint, and the bar fits a small or large screen. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UrlSearchBarLayoutTest(private val qualifiers: String, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun screens() = listOf(
            arrayOf<Any>("w320dp-h568dp", 2f),
            arrayOf<Any>("w411dp-h891dp", 1f),
            arrayOf<Any>("w840dp-h1280dp", 1.3f)
        )
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `hint sits clear of the cookie and inside the screen`() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    UrlSearchBar(url = "", onOpenSearch = {}, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        compose.waitForIdle()
        val metrics = context.resources.displayMetrics
        val hint = compose.onNodeWithText(context.getString(R.string.download_search_hint), useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode()
        val cookieEnd = (16 + 8 + 38) * metrics.density
        assertTrue("hint overlaps the cookie", hint.positionOnScreen.x >= cookieEnd)
        assertTrue("hint runs off the screen", hint.positionOnScreen.x + hint.size.width <= metrics.widthPixels)
    }
}
