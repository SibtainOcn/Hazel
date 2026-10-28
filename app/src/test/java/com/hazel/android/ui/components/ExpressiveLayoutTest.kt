package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.MediaFormat
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.screens.download.FormatRow
import com.hazel.android.ui.screens.download.batch.BatchActionBar
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassSheetColor
import com.hazel.android.ui.theme.sheetBlackColor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The button group, the menu and the format badge keep their layout at any font and screen size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExpressiveLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val barLabels = listOf(
        context.getString(R.string.batch_bar_download_type),
        context.getString(R.string.batch_bar_quality, "1080p"),
        context.getString(R.string.batch_bar_save_location),
        context.getString(R.string.sheet_link_options),
        "Incognito off"
    )
    private val longItem = "Retry every failed download in the queue"

    private fun show(fontScale: Float, dark: Boolean = true, glass: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = "White", glass = glass) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().background(glassSheetColor(sheetBlackColor)).padding(16.dp)) {
                        content()
                    }
                }
            }
        }
    }

    private fun rootRight() = compose.onAllNodes(isRoot()).onFirst().getBoundsInRoot().right

    private fun assertBar(fontScale: Float) {
        show(fontScale) {
            BatchActionBar(
                isVideo = true, qualityLabel = "1080p", hqLabel = "1080p",
                onDownloadType = {}, onQuality = {}, onSaveDir = {},
                linksEnabled = true, onLinks = {}, onFeedback = {}
            )
        }
        val right = rootRight()
        barLabels.forEach { label ->
            val bounds = compose.onNodeWithContentDescription(label).assertIsDisplayed().getBoundsInRoot()
            assertTrue("$label runs off the screen at font $fontScale", bounds.right <= right)
            assertTrue("$label is squeezed to nothing at font $fontScale", (bounds.right - bounds.left).value > 24f)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `the button group fits a small screen at the largest font`() {
        assertBar(2f)
        saveScreen("bar_font2x_small_screen")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `the button group fits a small screen at the smallest font`() = assertBar(0.85f)

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `the button group spans a wide screen`() = assertBar(1f)

    private fun assertMenu(fontScale: Float, dark: Boolean, glass: Boolean) {
        show(fontScale, dark, glass) {
            Column {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    DropdownMenuItem(
                        text = { Text(longItem) },
                        onClick = {},
                        icon = Icons.Filled.Refresh,
                        tone = MenuTone.Accent
                    )
                    DropdownMenuItem(text = { Text("Details") }, onClick = {}, icon = Icons.Filled.Info, tone = MenuTone.Info)
                    DropdownMenuItem(text = { Text("Pause") }, onClick = {}, icon = Icons.Filled.Pause, tone = MenuTone.Neutral)
                    DropdownMenuItem(text = { Text("Cancel") }, onClick = {}, icon = Icons.Filled.Close, tone = MenuTone.Danger, enabled = false)
                    DropdownMenuItem(text = { Text("720p") }, onClick = {}, selected = true)
                }
            }
        }
        val right = rootRight()
        val item = compose.onAllNodesWithText(longItem).onFirst().assertIsDisplayed().getBoundsInRoot()
        assertTrue("menu item runs off the screen at font $fontScale", item.right <= right)
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `a long menu item wraps on a small screen at the largest font`() {
        assertMenu(2f, dark = true, glass = true)
        saveScreen("menu_font2x_small_screen")
    }

    @Test
    fun `the menu reads in dark glass`() = assertMenu(1f, dark = true, glass = true)

    @Test
    fun `the menu reads in dark without glass`() = assertMenu(1f, dark = true, glass = false)

    @Test
    fun `the menu reads in light glass`() = assertMenu(1f, dark = false, glass = true)

    @Test
    fun `the menu reads in light without glass`() {
        assertMenu(1f, dark = false, glass = false)
        saveScreen("menu_light_plain")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `a format badge keeps its label inside the cookie at the largest font`() {
        val webm = MediaFormat(
            formatId = "302", selector = "302", label = "720p60", ext = "webm",
            vcodec = "vp9", acodec = null, height = 720, fps = 60, bitrateKbps = 2500.0,
            fileSizeBytes = 68_200_000L, hasVideo = true, hasAudio = false
        )
        show(2f) { FormatRow(format = webm, selected = false, onClick = {}) }
        val label = compose.onNodeWithText("WEBM", useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
        assertTrue("badge label is wider than its cookie: ${label.right - label.left}", (label.right - label.left).value <= 58f)
        saveScreen("format_row_font2x_small_screen")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `Download all fits a small screen at the largest font`() {
        show(2f) { com.hazel.android.ui.screens.download.DownloadAllButton(onClick = {}) }
        val label = context.getString(R.string.download_download_all)
        val bounds = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
        assertTrue("Download all runs off the screen", bounds.right <= rootRight())
        saveScreen("download_all_font2x_small_screen")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `the status dialog fits a small screen at the largest font`() {
        show(2f) {
            com.hazel.android.ui.screens.history.StatusFilterDialog(
                current = com.hazel.android.data.HistoryStatus.ALL, onPick = {}, onDismiss = {}
            )
        }
        com.hazel.android.data.HistoryStatus.entries.forEach {
            compose.onNodeWithText(context.getString(it.labelRes), useUnmergedTree = true).assertIsDisplayed()
        }
        saveScreen("status_dialog_font2x_small_screen")
    }
}
