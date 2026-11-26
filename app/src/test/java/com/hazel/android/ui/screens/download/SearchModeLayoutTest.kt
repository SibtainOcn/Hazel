package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchModeLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `media and gallery fit side by side and the i opens the guide`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                SearchScreen(onSearch = {}, onSearchWords = { _, _ -> }, imagesFirst = true, onDismiss = {})
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.search_mode_media, R.string.nav_gallery).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("search-modes-$locale")
        compose.onNodeWithText("i").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.search_guide_title)).fetchSemanticsNode()
        saveScreen("search-guide-$locale")
    }
}
