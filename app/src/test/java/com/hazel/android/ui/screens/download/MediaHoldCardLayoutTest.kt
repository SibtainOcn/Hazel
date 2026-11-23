package com.hazel.android.ui.screens.download

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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.saveScreen
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

/** The long-press card: Play on top, the rest as tiles, readable in long languages at the largest font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MediaHoldCardLayoutTest(private val locale: String, private val dark: Boolean, private val glass: Boolean, private val font: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark{1}-glass{2}-font{3}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it, true, false, 2f) } +
            listOf(arrayOf<Any>("en", false, false, 2f), arrayOf<Any>("en", true, true, 2f), arrayOf<Any>("en", false, true, 2f)) +
            listOf("en", "de").flatMap { listOf(arrayOf<Any>(it, true, false, 1f), arrayOf<Any>(it, true, true, 1f)) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val info = MediaInfo(
        "https://example.com/v", "The Lighthouse Keepers | 2 Hour Compilation | New Episodes", "A channel",
        null, 7200, emptyList(), emptyList()
    )

    private fun show(downloaded: Boolean) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).padding(12.dp)) {
                        MediaHoldCard(
                            info = info,
                            onPlay = {},
                            onDownload = {},
                            onOpen = if (downloaded) ({}) else null,
                            onProperties = if (downloaded) ({}) else null
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun `downloaded link offers four actions`() {
        show(downloaded = true)
        val width = compose.onRoot().getBoundsInRoot().right
        listOf(R.string.player_play, R.string.format_sheet_download, R.string.sheet_link_open, R.string.history_card_properties)
            .forEach { id ->
                val node = compose.onNodeWithText(label(id))
                node.assertIsDisplayed()
                assertTrue(label(id), node.getBoundsInRoot().right <= width)
            }
        saveScreen("hold-card-4-$locale-${if (dark) "dark" else "light"}${if (glass) "-glass" else ""}-f${font.toInt()}")
    }

    @Test
    fun `link not on the device offers play and download`() {
        show(downloaded = false)
        compose.onNodeWithText(label(R.string.player_play)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.format_sheet_download)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(label(R.string.sheet_link_open)).fetchSemanticsNodes().size)
        saveScreen("hold-card-2-$locale-${if (dark) "dark" else "light"}${if (glass) "-glass" else ""}-f${font.toInt()}")
    }
}
