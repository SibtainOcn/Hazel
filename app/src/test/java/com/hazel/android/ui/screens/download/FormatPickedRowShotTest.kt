package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A real ladder with one row picked: the pick reads on every look and sizes count the audio. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class FormatPickedRowShotTest(private val name: String, private val dark: Boolean, private val glass: Boolean, private val accent: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(
            arrayOf<Any>("picked_dark_plain_white", true, false, "White"),
            arrayOf<Any>("picked_light_plain_white", false, false, "White"),
            arrayOf<Any>("picked_dark_glass_white", true, true, "White"),
            arrayOf<Any>("picked_dark_plain_indigo", true, false, "Indigo")
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private fun video(id: String, height: Int, bytes: Long) = MediaFormat(
        formatId = id, selector = id, label = "${height}p", ext = "mp4", vcodec = "avc1", acodec = null,
        height = height, fps = 30, bitrateKbps = 0.0, fileSizeBytes = bytes, hasVideo = true, hasAudio = false
    )

    @Test
    fun picked() {
        ApplicationProvider.getApplicationContext<Context>()
        val ladder = listOf(video("137", 1080, 1_420_000_000), video("136", 720, 540_000_000), video("133", 240, 92_700_000))
        val audio = MediaFormat(
            formatId = "140", selector = "140", label = "128k", ext = "m4a", vcodec = null, acodec = "mp4a",
            height = 0, fps = 0, bitrateKbps = 128.0, fileSizeBytes = 111_000_000, hasVideo = false, hasAudio = true
        )
        val info = MediaInfo("u", "t", "a", null, 7200, ladder, listOf(audio))
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = glass) {
                FormatSelectionSheet(info = info, selected = ladder.last(), mergeAudio = audio, onConfirm = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText(formatFileSize(203_700_000)).fetchSemanticsNodes().size)
        saveScreen(name)
    }
}
