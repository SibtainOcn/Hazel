package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.screens.download.UpdatesStep
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
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
class HazelUpdateTogglesLayoutTest(private val locale: String, private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").flatMap { locale ->
            listOf("glass-dark", "glass-light", "dark", "light").map { arrayOf<Any>(locale, it) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun show(content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = theme.endsWith("dark"), glass = theme.startsWith("glass")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassScreen()) {
                        // Both places scroll in the app: the update screen and the guide dialog.
                        Box(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertFits(vararg ids: Int) {
        val width = context.resources.displayMetrics.widthPixels
        ids.forEach { id ->
            val text = context.getString(id)
            val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text runs off the right edge", node.positionOnScreen.x + node.size.width <= width)
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun `update screen toggles fit and switch`() {
        show {
            var notify by remember { mutableStateOf(false) }
            var auto by remember { mutableStateOf(true) }
            ToggleGroup(
                R.string.upd_section_automatic,
                listOf(
                    ComponentToggle(
                        Icons.Filled.NotificationsActive, MaterialShapes.Cookie9Sided.toShape(),
                        context.getString(R.string.upd_notify_new_title),
                        context.getString(R.string.upd_notify_new_summary), notify
                    ) { notify = it },
                    ComponentToggle(
                        Icons.Filled.CloudDownload, MaterialShapes.Cookie7Sided.toShape(),
                        context.getString(R.string.upd_auto_download_title),
                        context.getString(R.string.upd_auto_download_summary), auto
                    ) { auto = it },
                    ComponentToggle(
                        Icons.Filled.Wifi, MaterialShapes.Cookie4Sided.toShape(),
                        context.getString(R.string.upd_wifi_only_title),
                        context.getString(R.string.upd_wifi_only_summary), false
                    ) {}
                )
            )
        }
        assertFits(R.string.upd_notify_new_title, R.string.upd_auto_download_title, R.string.upd_auto_download_summary)
        val notifyRow = compose.onNodeWithText(context.getString(R.string.upd_notify_new_title), useUnmergedTree = false)
        notifyRow.assertIsOff()
        notifyRow.performClick()
        notifyRow.assertIsOn()
        saveScreen("hazel-update-toggles-$theme-$locale")
    }

    @Test
    fun `onboarding updates step fits`() {
        show { Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) { Box(Modifier.padding(24.dp)) { UpdatesStep() } } }
        assertFits(R.string.guide_step_updates_title, R.string.guide_updates_notify, R.string.guide_updates_ytdlp, R.string.guide_updates_hazel, R.string.guide_updates_wifi)
        val open = compose.onNodeWithContentDescription(context.getString(R.string.more_software_update)).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Software update button runs off the edge", open.positionOnScreen.x + open.size.width <= context.resources.displayMetrics.widthPixels)
        saveScreen("guide-updates-$theme-$locale")
    }
}
