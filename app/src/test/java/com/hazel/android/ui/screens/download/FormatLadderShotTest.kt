package com.hazel.android.ui.screens.download

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.hazel.android.download.GenericFormats
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The quality ladder sheet drawn in a few looks, saved as screenshots, with no "DEFAULT" tiles. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class FormatLadderShotTest(private val name: String, private val dark: Boolean, private val glass: Boolean, private val accent: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(
            arrayOf<Any>("ladder_dark_glass_white", true, true, "White"),
            arrayOf<Any>("ladder_light_glass_white", false, true, "White"),
            arrayOf<Any>("ladder_dark_plain_indigo", true, false, "Indigo"),
            arrayOf<Any>("ladder_dark_glass_indigo", true, true, "Indigo")
        )
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ladder() {
        val ladder = GenericFormats.videoLadder("Best quality", "Worst quality")
        val info = MediaInfo("", "", "", null, 0, ladder, emptyList())
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = glass) {
                FormatSelectionSheet(info = info, selected = ladder.first(), onConfirm = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("1080").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("BEST").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("DEFAULT").fetchSemanticsNodes().size)
        saveScreen(name)
    }
}
