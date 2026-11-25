package com.hazel.android.ui.screens.gallery

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
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
    fun `a failed run offers its log on the shared sheet`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.fail(
            "https://www.instagram.com/p/abc/",
            GalleryDlException(
                GalleryDlException.Kind.LOGIN_REQUIRED, "login required",
                log = "[instagram][info] Requesting post\n[instagram][error] AuthorizationError: Login required"
            )
        )
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
        val width = context.resources.displayMetrics.widthPixels
        val viewLog = compose.onNodeWithText(context.getString(R.string.gallery_view_log)).performScrollTo().assertIsDisplayed()
        val button = viewLog.fetchSemanticsNode()
        assertTrue("View log runs off the screen", button.positionOnScreen.x + button.size.width <= width)
        saveScreen("gallery-failed-$locale")
        viewLog.performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.history_failed_retry)).assertIsDisplayed()
        compose.onNodeWithText("AuthorizationError", substring = true).assertIsDisplayed()
        saveScreen("gallery-failed-log-$locale")
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

    @Test
    fun `carousel post card keeps its author, count and download inside`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val post = GalleryPost(
            key = "p1",
            source = "https://www.instagram.com/p/AAA/",
            items = (1..4).map { i ->
                GalleryItem(
                    index = i, url = "https://cdn.example/$i.jpg", extension = if (i == 2) "mp4" else "jpg",
                    title = "", author = "spacebrief_with_a_long_name", site = "instagram",
                    width = 1080, height = 1350, thumbnail = null, postKey = "p1"
                )
            }
        )
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp)) {
                        GalleryPostCard(post = post, onOpen = {}, onDownloadAll = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("gallery-post-card-$locale")
        val width = context.resources.displayMetrics.widthPixels
        listOf(
            "spacebrief_with_a_long_name",
            context.resources.getQuantityString(R.plurals.gallery_items, 4, 4),
            "1 / 4"
        ).forEach { text ->
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
    }
}
