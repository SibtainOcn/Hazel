package com.hazel.android.ui.screens.gallery

import android.app.Application
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
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.gallery.GalleryItem
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
class GalleryScreenLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `empty gallery tab fits at the largest font`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        GalleryScreen(onVideoLinks = {}, onVideoSearch = { _, _ -> }, viewModel = viewModel)
                    }
                }
            }
        }
        compose.waitForIdle()

        val metrics = context.resources.displayMetrics
        listOf(R.string.gallery_empty_title, R.string.gallery_empty_hint).forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            val at = node.positionOnScreen
            assertTrue("$text runs off the left edge", at.x >= 0f)
            assertTrue("$text runs off the right edge", at.x + node.size.width <= metrics.widthPixels)
            assertTrue("$text runs off the bottom edge", at.y + node.size.height <= metrics.heightPixels)
        }
        saveScreen("gallery-empty-$locale")
    }

    @Test
    fun `post card keeps its title, tag and action inside`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val item = GalleryItem(
            index = 3, url = "https://cdn.example/a.jpg", extension = "jpg",
            title = "China's next space mission explained in ten slides", author = "spacebrief",
            site = "instagram", width = 1080, height = 1350, thumbnail = null
        )
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        GalleryCard(item = item, total = 10, onOpen = {}, onView = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        val metrics = context.resources.displayMetrics
        listOf("spacebrief", "JPG · 3 / 10").forEach { text ->
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= metrics.widthPixels)
        }
        saveScreen("gallery-card-$locale")
    }
}
