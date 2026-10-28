package com.hazel.android.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Four tabs fit a small screen, and choosing one morphs its shape in without trouble. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h568dp")
class HazelBottomBarTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tabs fit and switch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = listOf(
            BottomBarItem("home", R.drawable.home, R.string.nav_home),
            BottomBarItem("history", R.drawable.downloads_tab, R.string.nav_history),
            BottomBarItem("queue", R.drawable.queue_tab, R.string.nav_queue, showDot = true),
            BottomBarItem("more", R.drawable.more_tab, R.string.nav_more)
        )
        compose.setContent {
            var route by androidx.compose.runtime.remember { mutableStateOf("home") }
            HazelTheme(darkTheme = dark) {
                HazelBottomBar(items, route, Color.Black, onSelect = { route = it.route })
            }
        }
        val width = context.resources.displayMetrics.widthPixels
        items.forEach { item ->
            val node = compose.onNodeWithContentDescription(context.getString(item.label)).fetchSemanticsNode()
            assertTrue("${item.route} runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.nav_more)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.nav_more)).assertIsSelected()
    }
}
