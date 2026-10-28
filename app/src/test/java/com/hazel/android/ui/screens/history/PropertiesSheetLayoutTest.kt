package com.hazel.android.ui.screens.history

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.HistoryEntry
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The four file actions sit side by side inside the screen, on a small phone at the largest font too. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PropertiesSheetLayoutTest(private val qualifiers: String, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun screens() = listOf(
            arrayOf<Any>("w320dp-h568dp", 2f),
            arrayOf<Any>("w411dp-h891dp", 1f)
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val entry = HistoryEntry(
        id = 1L,
        url = "https://www.youtube.com/watch?v=mJYxSed0abc",
        title = "Exploring the Most Remote Island on Earth (15 Days Just to Get There)",
        author = "Ruhi Cenet Documentaries",
        thumbnail = null,
        durationSeconds = 1194,
        fileName = "2026-10-27 Exploring the Most Remote Island on Earth (15 Days Just to Get There).mp4",
        fileUri = "content://media/external/video/media/1",
        savedPath = "Download/Hazel/Video",
        isVideo = true,
        sizeBytes = 38_125_162L,
        completedAt = 0L,
        formatLabel = "144p",
        embedded = "Subtitles, Chapters, Cover art"
    )

    @Test
    fun `file actions fit across the screen`() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    DownloadPropertiesSheet(entry = entry, present = true, onDismiss = {}, onDownloadAgain = {}, onFetchAgain = {})
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        val tops = listOf(R.string.format_sheet_play, R.string.media_open_chooser, R.string.properties_show_file, R.string.properties_share)
            .map { id ->
                val nodes = compose.onAllNodesWithText(context.getString(id), useUnmergedTree = true)
                nodes.assertCountEquals(1)
                val node = nodes[0].fetchSemanticsNode()
                assertTrue("${context.getString(id)} runs off the screen", node.positionOnScreen.x + node.size.width <= width)
                node.positionOnScreen.y
            }
        assertTrue("the actions are not on one row", tops.max() - tops.min() < 2f * context.resources.displayMetrics.density * 20)
    }
}
