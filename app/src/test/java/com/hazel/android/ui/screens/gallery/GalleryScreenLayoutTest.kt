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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
        viewModel.dismissFailureDialog()
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

    @Test
    fun `tapping a card opens its download sheet, not the viewer`() {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp")
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        val items = (1..2).map { GalleryItem(it, "https://x/$it.jpg", "jpg", "Cat $it", "nasa", "x", 0, 0, null, postKey = "p") }
        viewModel.show(GalleryPost.group("https://x/post", items))
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                GalleryScreen(onVideoLinks = {}, onVideoSearch = { _, _ -> }, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Cat 1").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.format_sheet_section_details), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithContentDescription(context.getString(R.string.gallery_close)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `reading shows the home skeleton, not the empty state`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.busy = true
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                GalleryScreen(onVideoLinks = {}, onVideoSearch = { _, _ -> }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.gallery_empty_title)).fetchSemanticsNodes().isEmpty())
        saveScreen("gallery-reading-$locale")
    }

    private fun downloadAllShown(search: Boolean, keys: List<String>): Boolean {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp")
        val viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
        val items = keys.mapIndexed { i, key -> GalleryItem(i + 1, "https://x/$i.jpg", "jpg", "Cat $i", "nasa", "x", 0, 0, null, postKey = key) }
        viewModel.fromSearch = search
        viewModel.show(GalleryPost.group("https://x/source", items))
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                GalleryScreen(onVideoLinks = {}, onVideoSearch = { _, _ -> }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        return compose.onAllNodesWithText(context.getString(R.string.download_download_all)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `search results of single files offer no download all`() {
        assertTrue(!downloadAllShown(search = true, keys = listOf("a", "b", "c")))
        assertTrue(compose.onAllNodesWithText("Cat 0").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `one pasted post with several files offers download all`() {
        assertTrue(downloadAllShown(search = false, keys = listOf("p", "p", "p")))
    }
}
