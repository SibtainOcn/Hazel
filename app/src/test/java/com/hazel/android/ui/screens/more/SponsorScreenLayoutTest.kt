package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SponsorScreenLayoutTest(private val locale: String, private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").flatMap { locale ->
            listOf("glass-dark", "glass-light", "dark", "light").map { arrayOf<Any>(locale, it) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `support screen fits at the largest font in the accent`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            // A non-default accent, so a hard-coded colour would show in the shots.
            HazelTheme(darkTheme = theme.endsWith("dark"), accentName = "Violet", glass = theme.startsWith("glass")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassScreen()) {
                        SponsorScreen(onBack = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("support-top-$theme-$locale")
        compose.onNodeWithText(context.getString(R.string.sponsor_bmac_title)).performScrollTo()
        saveScreen("support-ways-$theme-$locale")

        val width = context.resources.displayMetrics.widthPixels
        listOf(
            R.string.sponsor_developer_name, R.string.sponsor_hero_title, R.string.sponsor_github_title,
            R.string.sponsor_bmac_title, R.string.sponsor_star_title, R.string.sponsor_source_title
        ).forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("support-bottom-$theme-$locale")
    }
}
