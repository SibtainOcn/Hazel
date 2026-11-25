package com.hazel.android.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.update.GalleryDlUpdateViewModel.UiState
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
class GalleryDlUpdateLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val release = GalleryDlUpdater.Release("1.33.0", "https://files.pythonhosted.org/x.whl", "a".repeat(64), 1_000_000)

    @Test
    fun `every gallery-dl update state fits at the largest font`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        var state by mutableStateOf<UiState>(UiState.Idle)
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(UpdateTokens.Bg).padding(16.dp)) {
                        GalleryDlStatusCard(state, "1.32.21", {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val width = context.resources.displayMetrics.widthPixels
        val states = listOf(
            UiState.Checking to R.string.upd_status_checking,
            UiState.Available(release) to R.string.upd_btn_install,
            UiState.Updating(release, done = 500_000) to R.string.upd_btn_cancel,
            UiState.Installed("1.33.0") to R.string.upd_btn_done,
            UiState.Error(GalleryDlUpdater.Problem.WOULD_NOT_START, release) to R.string.upd_btn_retry,
            UiState.Idle to R.string.upd_btn_check_now
        )
        states.forEach { (next, label) ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            val text = context.getString(label)
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true)[0].fetchSemanticsNode()
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
            if (next is UiState.Error) saveScreen("gallery-dl-update-error-$locale")
            if (next is UiState.Updating) saveScreen("gallery-dl-update-progress-$locale")
        }
    }
}
