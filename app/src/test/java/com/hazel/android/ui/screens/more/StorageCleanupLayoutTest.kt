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
import com.hazel.android.util.TempCategory
import com.hazel.android.util.TempStorage
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
class StorageCleanupLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `gallery files row fits`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sample = listOf(
            TempCategory(
                "partial_downloads", R.string.cleanup_category_partial_downloads_label,
                R.string.cleanup_category_partial_downloads_description, 12_000_000
            ),
            TempCategory(
                TempStorage.GALLERY, R.string.cleanup_category_gallery_label,
                R.string.cleanup_category_gallery_description, 3_400_000
            )
        )
        compose.setContent {
            HazelTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    StorageCleanupScreen(onBack = {}, sample = sample)
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.cleanup_category_gallery_label, R.string.cleanup_category_gallery_description).forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("cleanup-gallery-$locale")
    }
}
