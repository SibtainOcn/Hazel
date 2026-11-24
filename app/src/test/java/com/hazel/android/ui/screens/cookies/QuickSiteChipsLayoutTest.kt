package com.hazel.android.ui.screens.cookies

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import com.hazel.android.ui.theme.glassSheetColor
import org.junit.Assert.assertEquals
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
class QuickSiteChipsLayoutTest(private val locale: String, private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").flatMap { locale ->
            listOf("glass-dark", "glass-light", "dark", "light").map { arrayOf<Any>(locale, it) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every site chip fits a small screen at the largest font and opens its site`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        var picked: Pair<String, String>? = null
        compose.setContent {
            HazelTheme(darkTheme = theme.endsWith("dark"), glass = theme.startsWith("glass")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassScreen()) {
                        Surface(
                            color = glassSheetColor(MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxSize().padding(top = 120.dp)
                        ) {
                            Box(Modifier.padding(24.dp)) {
                                QuickSiteChips(onPick = { name, home -> picked = name to home })
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        SignInBrowser.QUICK_SITES.forEach { (name, _) ->
            val node = compose.onNodeWithText(name).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$name runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
        compose.onNodeWithText("Instagram").performClick()
        assertEquals("Instagram" to "https://www.instagram.com", picked)
        saveScreen("quick-sites-$theme-$locale")
    }
}
