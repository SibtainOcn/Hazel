package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
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
class StorageLocationsLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `audio video and gallery rows fit`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    StorageLocationsScreen(onBack = {})
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        listOf(
            context.getString(R.string.format_sheet_tab_audio),
            context.getString(R.string.format_sheet_tab_video),
            context.getString(R.string.storage_locations_gallery),
            context.getString(R.string.search_source_images),
            "Download/Hazel/Gallery"
        ).forEach { text ->
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("storage-locations-$locale")
    }
}
