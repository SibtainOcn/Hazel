package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.GenericFormats
import com.hazel.android.download.MediaFormat
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

/**
 * Issue 159: the ladder a link offers before it is read fills the sheet with no rail, and the
 * rail comes in once the read lands, after a moment of skeleton.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FormatRailTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun video(id: String, height: Int) = MediaFormat(
        formatId = id, selector = id, label = "${height}p", ext = "mp4", vcodec = "avc1", acodec = null,
        height = height, fps = 30, bitrateKbps = 0.0, fileSizeBytes = 100_000_000, hasVideo = true, hasAudio = false
    )

    @Test
    fun `rail only once the formats are read`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        val all = ApplicationProvider.getApplicationContext<Context>().getString(R.string.format_filter_all)
        val ladder = GenericFormats.videoLadder("Best quality", "Worst quality")
        var info by mutableStateOf(MediaInfo("u", "t", "a", null, 0, ladder, emptyList()))
        compose.setContent {
            HazelTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    FormatSelectionSheet(info = info, selected = ladder.first(), isLoadingFormats = true, onConfirm = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText(all).fetchSemanticsNodes().size)
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        assertTrue(compose.onNodeWithText("~1080P").fetchSemanticsNode().boundsInRoot.right <= width)
        saveScreen("ladder-no-rail-$locale")

        info = MediaInfo("u", "t", "a", null, 0, listOf(video("137", 1080), video("136", 720), video("133", 240)), emptyList())
        compose.waitForIdle()
        // The skeleton stands in for a moment when the real formats land, then the rail shows.
        assertEquals(0, compose.onAllNodesWithText(all).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("1080p").fetchSemanticsNodes().size)
        compose.mainClock.advanceTimeBy(1_100)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(all).fetchSemanticsNodes().isNotEmpty())
    }
}
