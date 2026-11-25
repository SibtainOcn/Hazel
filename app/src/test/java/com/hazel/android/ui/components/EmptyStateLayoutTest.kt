package com.hazel.android.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The empty states of Home and the Queue's Failed tab, as those screens fill them. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EmptyStateLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(content: @Composable (Modifier) -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    content(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(vararg ids: Int) {
        val metrics = context.resources.displayMetrics
        ids.forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            val at = node.positionOnScreen
            assertTrue("$text runs off the left edge", at.x >= 0f)
            assertTrue("$text runs off the right edge", at.x + node.size.width <= metrics.widthPixels)
            assertTrue("$text runs off the bottom edge", at.y + node.size.height <= metrics.heightPixels)
        }
    }

    @Test
    fun `home shows its title and hint inside the screen`() {
        show { modifier ->
            EmptyState(
                icon = painterResource(R.drawable.ic_video),
                title = stringResource(R.string.home_empty_title),
                hint = stringResource(R.string.home_empty_hint),
                modifier = modifier
            )
        }
        saveScreen("empty-home-$locale")
        assertInside(R.string.home_empty_title, R.string.home_empty_hint)
    }

    @Test
    fun `queue failed tab shows its title inside the screen`() {
        show { modifier ->
            EmptyState(
                icon = rememberVectorPainter(Icons.Filled.ErrorOutline),
                title = stringResource(R.string.history_empty_failed),
                modifier = modifier
            )
        }
        saveScreen("empty-queue-failed-$locale")
        assertInside(R.string.history_empty_failed)
    }
}
