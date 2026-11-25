package com.hazel.android.ui.share

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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

/** The Hazel Images reading sheet at the largest font on a small phone. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GalleryReadingSheetLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `reading sheet keeps its header and download pill inside`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    GalleryReadingSheet(onDismiss = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        saveScreen("gallery-share-reading-$locale")
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.format_sheet_fetching, R.string.format_sheet_download).forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the left edge", node.positionOnScreen.x >= 0f)
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
    }
}
