package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
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
class GettingStartedLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every step keeps the card size and the share card fits`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    GettingStartedDialog(onOpenBatterySettings = {}, onDismiss = {})
                }
            }
        }
        val next = context.getString(R.string.guide_next)
        fun nextTop() = compose.onNodeWithText(next).performScrollTo().fetchSemanticsNode().let { it.positionInRoot.y + 0f }
        compose.waitForIdle()
        val first = nextTop()
        repeat(4) {
            compose.onNodeWithText(next).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals("card changed size at step ${it + 2}", first, nextTop(), 1f)
        }
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.share_label_video, R.string.instant_label, R.string.share_label_images, R.string.instant_images_label).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id)).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the edge", node.positionOnScreen.x + node.size.width <= width)
        }
        saveScreen("guide-share-$locale")
    }
}
