package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.CutMode
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

/** The Cut sheet's mode picker, as the sheet lays it out (its player cannot run here). */
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

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(afterAvailable: Boolean) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    var mode by remember { mutableStateOf(CutMode.QUICK) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp)
                    ) {
                        CutModePicker(selected = mode, afterAvailable = afterAvailable, onSelect = { mode = it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(vararg ids: Int) {
        val width = context.resources.displayMetrics.widthPixels
        ids.forEach { id ->
            val node = compose.onNodeWithText(context.getString(id)).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
    }

    @Test
    fun `quick is chosen first, and a tap moves the choice and its hint`() {
        show(afterAvailable = true)
        saveScreen("cut-modes-$locale")
        assertInside(
            R.string.options_cut_quick, R.string.options_cut_quick_summary,
            R.string.options_cut_precise, R.string.options_cut_local
        )
        compose.onNode(isSelectable() and hasText(context.getString(R.string.options_cut_quick), substring = true))
            .assertIsSelected()

        compose.onNodeWithText(context.getString(R.string.options_cut_local)).performClick()
        compose.waitForIdle()
        saveScreen("cut-modes-after-$locale")
        assertInside(R.string.options_cut_local_summary)
        compose.onNode(isSelectable() and hasText(context.getString(R.string.options_cut_local), substring = true))
            .assertIsSelected()
        compose.onAllNodes(hasText(context.getString(R.string.options_cut_quick_summary))).assertCountEquals(0)
    }

    @Test
    fun `trim after download says why when it cannot be chosen`() {
        show(afterAvailable = false)
        saveScreen("cut-modes-unavailable-$locale")
        assertInside(R.string.options_cut_local_unavailable)
        compose.onNode(isSelectable() and hasText(context.getString(R.string.options_cut_local), substring = true))
            .assertIsNotEnabled()
    }
}
