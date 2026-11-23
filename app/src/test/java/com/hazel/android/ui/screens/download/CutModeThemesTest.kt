package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.CutMode
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cut modes on the Cut screen's own background, in light, dark and every glass style. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CutModeThemesTest(private val name: String, private val dark: Boolean, private val style: GlassStyle?) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any?>> = listOf<Array<Any?>>(
            arrayOf("light", false, null),
            arrayOf("dark", true, null)
        ) + (GlassStyle.entries - GlassStyle.WALLPAPER).flatMap { style ->
            listOf(arrayOf<Any?>("glass-${style.key}-dark", true, style), arrayOf<Any?>("glass-${style.key}-light", false, style))
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every mode reads on the screen background`() {
        RuntimeEnvironment.setQualifiers("en-w360dp-h480dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = style != null, glassStyle = style ?: GlassStyle.DEFAULT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .glassScreen()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp)
                ) {
                    CutModePicker(selected = CutMode.PRECISE, afterAvailable = true, onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        saveScreen("cut-modes-theme-$name")
        listOf(R.string.options_cut_quick_summary, R.string.options_cut_precise_summary, R.string.options_cut_local_summary)
            .forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
    }
}
