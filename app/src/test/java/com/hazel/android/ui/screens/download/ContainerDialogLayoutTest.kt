package com.hazel.android.ui.screens.download

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.screens.download.batch.ContainerDialog
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The container choice is the app's choice dialog: every container reachable, Default stored as blank. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ContainerDialogLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `audio containers all reachable`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        var picked: String? = null
        compose.setContent {
            HazelTheme(darkTheme = true) {
                ContainerDialog(isVideo = false, current = "mp3", onSelect = { picked = it }, onDismiss = {})
            }
        }
        compose.waitForIdle()
        saveScreen("container-dialog-$locale")
        compose.onNodeWithText("Default").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("vorbis"))
        compose.onNodeWithText("vorbis").assertIsDisplayed().performClick()
        assertEquals("vorbis", picked)
    }

    @Test
    fun `default is stored as blank`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        var picked: String? = null
        compose.setContent {
            HazelTheme(darkTheme = true) {
                ContainerDialog(isVideo = true, current = "mp4", onSelect = { picked = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Default").performClick()
        assertEquals("", picked)
    }
}
