package com.hazel.android.ui.screens.gallery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The gallery item sheet and the Download all sheet at the largest font on a small phone. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GallerySheetsLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }

        private fun item(index: Int, extension: String, title: String) = GalleryItem(
            index = index, url = "https://cdn.example/$index.$extension", extension = extension,
            title = title, author = "spacebrief", site = "instagram",
            width = 1080, height = 1350, thumbnail = null
        )

        private val POST = listOf(
            item(1, "jpg", "China's next space mission explained in ten slides"),
            item(2, "jpg", "The mission"),
            item(3, "mp4", "Launch clip")
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(vararg texts: String) {
        val width = context.resources.displayMetrics.widthPixels
        texts.forEach { text ->
            val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            val at = node.positionOnScreen
            assertTrue("$text runs off the left edge", at.x >= 0f)
            assertTrue("$text runs off the right edge", at.x + node.size.width <= width)
        }
    }

    @Test
    fun `image item sheet keeps its header and size row inside, and the row opens the size picker`() {
        show { GalleryItemSheet(item = POST[0], postUrl = "https://x.com/a/status/1", onDownload = { _, _, _ -> }, onDismiss = {}) }
        saveScreen("gallery-item-image-$locale")
        assertInside(context.getString(R.string.gallery_image_size))
        compose.onNodeWithContentDescription(context.getString(R.string.format_selection_change)).performClick()
        compose.waitForIdle()
        saveScreen("gallery-item-picker-$locale")
        assertInside(context.getString(R.string.format_selection_subtitle), context.getString(R.string.format_selection_confirm))
    }

    @Test
    fun `video item sheet offers the video quality instead`() {
        show { GalleryItemSheet(item = POST[2], postUrl = "", onDownload = { _, _, _ -> }, onDismiss = {}) }
        saveScreen("gallery-item-video-$locale")
        assertInside(context.getString(R.string.format_sheet_video_quality))
    }

    @Test
    fun `download all sheet keeps the list bar, adjust section and buttons in view`() {
        show { GalleryPostSheet(items = POST, postUrl = "", onDownload = {}, onDismiss = {}) }
        saveScreen("gallery-post-$locale")
        assertInside(
            context.resources.getQuantityString(R.plurals.gallery_items, POST.size, POST.size),
            context.getString(R.string.gallery_adjust_all),
            context.getString(R.string.gallery_size_original).uppercase()
        )
    }

    @Test
    fun `download all sheet splits into two panes on a phone held sideways`() {
        RuntimeEnvironment.setQualifiers("$locale-w800dp-h360dp-land")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                GalleryPostSheet(items = POST, postUrl = "", onDownload = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()
        saveScreen("gallery-post-land-$locale")
        assertInside(
            context.resources.getQuantityString(R.plurals.gallery_items, POST.size, POST.size),
            context.getString(R.string.gallery_adjust_all)
        )
    }

    @Test
    fun `viewer's bottom download opens the sheet for the file in view`() {
        var opened: Int? = null
        show { GalleryViewer(items = POST, startIndex = 1, onOpenSheet = { opened = it.index }, onDismiss = {}) }
        saveScreen("gallery-viewer-$locale")
        compose.onNodeWithText(context.getString(R.string.format_sheet_download)).assertIsDisplayed().performClick()
        assertTrue(opened == 2)
    }
}
