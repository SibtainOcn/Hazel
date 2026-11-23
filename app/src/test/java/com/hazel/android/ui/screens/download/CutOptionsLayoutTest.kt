package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Cut sheet's two option rows, as the sheet lays them out (its player cannot run here). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CutOptionsLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `both rows and their hints fit a small screen at the largest font, and a tap anywhere toggles`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    var precise by remember { mutableStateOf(false) }
                    var trimAfter by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ToggleRow(
                            label = context.getString(R.string.options_cut_precise),
                            checked = precise,
                            onCheckedChange = { precise = it },
                            summary = context.getString(R.string.options_cut_precise_summary)
                        )
                        ToggleRow(
                            label = context.getString(R.string.options_cut_local),
                            checked = trimAfter,
                            onCheckedChange = { trimAfter = it },
                            summary = context.getString(R.string.options_cut_local_summary)
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("cut-options-$locale")

        val screen = context.resources.displayMetrics
        listOf(
            R.string.options_cut_precise, R.string.options_cut_precise_summary,
            R.string.options_cut_local, R.string.options_cut_local_summary
        ).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id)).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the right edge",
                node.positionOnScreen.x + node.size.width <= screen.widthPixels)
        }
        val switches = compose.onAllNodes(isToggleable())
        switches[0].assertIsOff()
        switches[1].assertIsOff()
        switches.fetchSemanticsNodes().forEach { node ->
            assertTrue("a switch runs off the right edge", node.positionOnScreen.x + node.size.width <= screen.widthPixels)
        }

        compose.onNodeWithText(context.getString(R.string.options_cut_local_summary)).performClick()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
    }
}
