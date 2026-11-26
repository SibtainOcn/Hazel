package com.hazel.android.ui.screens.gallery

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.gallery.GallerySearchSite
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.screens.download.SearchOptionsSheet
import com.hazel.android.ui.theme.HazelTheme
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
class GallerySearchLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(text: String) {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true)[0].fetchSemanticsNode()
        val width = context.resources.displayMetrics.widthPixels
        assertTrue("$text starts off screen", node.positionOnScreen.x >= 0f)
        assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
    }

    @Test
    fun `site row marks the chosen site and switches on tap`() {
        var site by mutableStateOf<GallerySearchSite?>(null)
        show { GallerySiteRow(selected = site, onSelect = { site = it }) }
        compose.onNodeWithText("X").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(GallerySearchSite.X, site)
        val chosen = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes()
        assertEquals(1, chosen.size)
        saveScreen("gallery-site-row-$locale")
    }

    @Test
    fun `images tab of the search options carries the beta tag`() {
        show { SearchOptionsSheet(onClearResults = {}, onDismiss = {}, imagesFirst = true) }
        assertInside(context.getString(R.string.search_source_images))
        assertInside(context.getString(R.string.label_beta))
        saveScreen("gallery-search-options-$locale")
    }

    @Test
    fun `empty gallery tab carries the beta tag`() {
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        show { GalleryScreen(onVideoLinks = {}, onVideoSearch = { _, _ -> }, viewModel = viewModel) }
        assertInside(context.getString(R.string.label_beta))
        saveScreen("gallery-empty-beta-$locale")
    }
}
