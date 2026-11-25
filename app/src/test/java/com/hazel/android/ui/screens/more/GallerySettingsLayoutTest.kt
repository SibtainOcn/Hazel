package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryVideoMode
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class GallerySettingsLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every row fits on a small phone at large text`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    GallerySettingsScreen(onBack = {}, onOpenCookies = {}, onOpenProxy = {})
                }
            }
        }
        compose.waitForIdle()
        saveScreen("gallery-settings-$locale")
        val width = context.resources.displayMetrics.widthPixels
        listOf(
            R.string.gallery_settings_image_size,
            R.string.gallery_settings_skip_downloaded_hint,
            R.string.gallery_settings_clear_record,
            R.string.gallery_skip_reposts,
            R.string.gallery_settings_instagram_sections_value,
            R.string.gallery_settings_retries,
            R.string.gallery_settings_ugoira
        ).forEach { id ->
            val text = context.getString(id)
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true)[0]
                .performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("gallery-settings-end-$locale")
    }

    @Test
    fun `options round trip through settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = GalleryOptions(
            imageSize = GalleryImageSize.MEDIUM,
            videoMode = GalleryVideoMode.SINGLE,
            filenameTemplate = "{username}_{num}.{extension}",
            writeMetadata = true,
            keepPostDate = false,
            packZip = true,
            skipDownloaded = true
        )
        SettingsRepository.setGalleryOptions(context, options)
        assertEquals(options, SettingsRepository.getGalleryOptions(context).first())
        SettingsRepository.setGalleryOptions(context, GalleryOptions())
        assertEquals(GalleryOptions(), SettingsRepository.getGalleryOptions(context).first())
    }
}
