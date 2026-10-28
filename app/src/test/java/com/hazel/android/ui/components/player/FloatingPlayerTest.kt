package com.hazel.android.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.hazel.android.download.playback.PlaybackController
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class FloatingPlayerTest {

    @get:Rule
    val compose = createComposeRule()

    private val dock = PlayerDock().apply {
        start("a")
        onCardGone("a", fullscreen = false)
    }

    private fun show() {
        compose.setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val controller = remember { PlaybackController(context, scope, "https://example.com/v") }
            HazelTheme(darkTheme = true) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    MiniPlayer(dock = dock, controller = controller, thumbnail = null, fullscreen = false, onClose = {})
                    if (dock.place == PlayerPlace.LARGE) {
                        LargePlayer(
                            dock = dock,
                            controller = controller,
                            title = "A video",
                            author = "Someone",
                            thumbnail = null,
                            fullscreen = false,
                            onFullscreen = {},
                            onClose = {},
                            onDownload = null
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** A spot on the mini player away from its play and close buttons. */
    private fun tapMini() {
        compose.onRoot().performTouchInput {
            click(Offset(30.dp.toPx(), 800.dp.toPx() - 30.dp.toPx()))
        }
        compose.waitForIdle()
    }

    @Test
    fun oneTapOpensTheLargeViewAndItStaysOpen() {
        show()
        assertEquals(PlayerPlace.MINI, dock.place)
        tapMini()
        assertEquals(PlayerPlace.LARGE, dock.place)
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("A video").assertIsDisplayed()
        assertEquals(PlayerPlace.LARGE, dock.place)
    }

    @Test
    fun afterMinimizingOneTapOpensItAgain() {
        show()
        tapMini()
        compose.mainClock.advanceTimeBy(2_000)
        dock.collapse()
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(PlayerPlace.MINI, dock.place)
        tapMini()
        assertEquals(PlayerPlace.LARGE, dock.place)
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("A video").assertIsDisplayed()
    }
}
