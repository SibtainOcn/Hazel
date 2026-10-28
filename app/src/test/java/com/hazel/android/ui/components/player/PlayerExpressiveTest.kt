package com.hazel.android.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.saveScreen
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
@Config(sdk = [35], qualifiers = "w320dp-h568dp")
class PlayerExpressiveTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the play button morphs, takes taps and keeps its icon at the largest font`() {
        var playing by mutableStateOf(false)
        var taps by mutableIntStateOf(0)
        compose.setContent {
            HazelTheme(darkTheme = true, accentName = "White", glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF101014)).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            MorphingPlayButton(playing = playing, onClick = { taps++ }, color = Color.White) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.Black
                                )
                            }
                        }
                        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                            val y = size.height / 2f
                            drawWavyLine(y, 4.dp.toPx(), 0.6f, Color(0xFF5ED3E6), amount = 1f, phase = 0.2f)
                            drawPillThumb(size.width * 0.6f, y, Color(0xFF5ED3E6), 16.dp.toPx(), 5.dp.toPx())
                        }
                        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                            val y = size.height / 2f
                            drawWavyLine(y, 4.dp.toPx(), 0.6f, Color(0xFF5ED3E6), amount = 0f, phase = 0f)
                            drawPillThumb(size.width * 0.6f, y, Color(0xFF5ED3E6), 22.dp.toPx(), 5.dp.toPx())
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        assertEquals(1, taps)
        saveScreen("player_paused_circle_and_waves")

        playing = true
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        saveScreen("player_playing_cookie")
    }
}
