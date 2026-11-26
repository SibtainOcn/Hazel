package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.hazel.android.ui.theme.glassScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings screens as connected row groups, readable in long languages, at the largest font, in every look. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConnectedGroupsLayoutTest(
    private val locale: String,
    private val dark: Boolean,
    private val glass: Boolean,
    private val font: Float
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark{1}-glass{2}-font{3}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it, true, false, 2f) } +
            listOf(arrayOf<Any>("en", false, false, 1f), arrayOf<Any>("en", true, true, 1f), arrayOf<Any>("en", false, true, 1f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val look get() = "$locale-${if (dark) "dark" else "light"}${if (glass) "-glass" else ""}-f${font.toInt()}"

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass, glassStyle = GlassStyle.DEFAULT) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                    Box(Modifier.fillMaxSize().glassScreen().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(id: Int) {
        val text = ApplicationProvider.getApplicationContext<Context>().getString(id)
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue(text, nodes.isNotEmpty())
        nodes.forEach { assertTrue(text, it.boundsInRoot.right <= width) }
    }

    @Test
    fun more() {
        show { MoreScreen() }
        assertInside(R.string.more_appearance)
        saveScreen("groups-more-$look")
    }

    @Test
    fun processing() {
        show { ProcessingScreen(onBack = {}) }
        saveScreen("groups-processing-$look")
    }

    @Test
    fun appearance() {
        show {
            AppearanceScreen(
                isDarkTheme = dark, onToggleTheme = {}, accentName = "White", onAccentChanged = {},
                glassEnabled = glass, onToggleGlass = {}, glassStyle = GlassStyle.DEFAULT,
                onGlassStyleChanged = {}, onBack = {}
            )
        }
        assertInside(R.string.appearance_title)
        saveScreen("groups-appearance-$look")
    }
}
