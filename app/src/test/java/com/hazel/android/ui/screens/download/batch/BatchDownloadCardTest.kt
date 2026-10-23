package com.hazel.android.ui.screens.download.batch

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.glassSheetColor
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BatchDownloadCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val removeLabel = context.getString(R.string.batch_card_remove)
    private val typeLabel = context.getString(R.string.batch_card_download_type)
    private val title = "BASIC ELECTRICAL & ELECTRONICS | BEE | S-1 | FIRST YEAR ENGINEERING | DC CIRCUITS"

    private val info = MediaInfo(
        url = "https://example.com/watch?v=1",
        title = title,
        uploader = "Channel",
        thumbnail = null,
        durationSeconds = 1505,
        videoFormats = emptyList(),
        audioFormats = emptyList()
    )

    private class Look(val dark: Boolean, val glass: Boolean, val style: GlassStyle, val accent: String)

    private fun setRow(
        look: () -> Look,
        fontScale: Float = 1f,
        removeAsked: () -> Boolean = { false },
        onAsk: () -> Unit = {}
    ) {
        compose.setContent {
            val l = look()
            HazelTheme(darkTheme = l.dark, accentName = l.accent, glass = l.glass, glassStyle = l.style) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.background(glassSheetColor(batchSheetColor)).padding(12.dp)) {
                    BatchDownloadCard(
                        info = info,
                        formatLabel = "Best quality",
                        sizeLabel = "120 MB",
                        isVideo = true,
                        isAdjusted = false,
                        selectionMode = false,
                        checked = false,
                        onClick = {},
                        onLongClick = {},
                        onTypeClick = {},
                        removeAsked = removeAsked(),
                        onAskRemove = onAsk
                    )
                    }
                }
            }
        }
    }

    @Test
    fun `a resting row shows no remove layer in any theme, accent or glass style`() {
        val looks = buildList {
            for (dark in listOf(true, false)) {
                for (accent in listOf(AccentColors.first().name, AccentColors.last().name)) {
                    add(Look(dark, glass = false, style = GlassStyle.DEFAULT, accent = accent))
                    GlassStyle.entries.forEach { add(Look(dark, glass = true, style = it, accent = accent)) }
                }
            }
        }
        var index by mutableIntStateOf(0)
        setRow(look = { looks[index] })
        looks.indices.forEach { i ->
            index = i
            compose.waitForIdle()
            compose.onAllNodesWithText(removeLabel).assertCountEquals(0)
            compose.onNodeWithContentDescription(typeLabel).assertIsDisplayed()
            val l = looks[i]
            if (l.style == GlassStyle.DEFAULT && l.accent == looks[0].accent) {
                saveScreen("row_${if (l.dark) "dark" else "light"}_${if (l.glass) "glass" else "plain"}")
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `the largest font on a small screen keeps the row inside the screen`() {
        setRow(look = { Look(true, true, GlassStyle.DEFAULT, "White") }, fontScale = 2f)
        val root = compose.onRoot().getBoundsInRoot()
        val button = compose.onNodeWithContentDescription(typeLabel).assertIsDisplayed().getBoundsInRoot()
        assertTrue("type button runs off the right edge", button.right <= root.right)
        saveScreen("row_font2x_small_screen")
        compose.onNodeWithText(title, substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `a wide screen draws the row normally`() {
        setRow(look = { Look(false, true, GlassStyle.DEFAULT, "White") })
        compose.onNodeWithText(title, substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(removeLabel).assertCountEquals(0)
    }

    @Test
    fun `a swipe asks once, and cancelling puts the row back`() {
        var asks = 0
        var asked by mutableStateOf(false)
        setRow(
            look = { Look(true, true, GlassStyle.DEFAULT, "White") },
            removeAsked = { asked },
            onAsk = {
                asks++
                asked = true
            }
        )
        compose.onNodeWithText(title, substring = true).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, asks)
        compose.onNodeWithText(removeLabel).assertIsDisplayed()
        saveScreen("row_swiped")

        asked = false
        compose.waitForIdle()
        compose.onAllNodesWithText(removeLabel).assertCountEquals(0)
        assertEquals(1, asks)
    }
}
