package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.update.HazelUpdateViewModel.UiState
import com.hazel.android.update.UpdateViewModel.UiState as EngineState
import com.hazel.android.update.HazelUpdater
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Both update screens fit from a small phone at large text to a tablet, on the oldest and
 * newest Android, and every update state draws, with live progress while downloading.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateScreensLayoutTest(private val qualifiers: String, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun screens() = listOf(
            arrayOf<Any>("w320dp-h568dp", 2f),
            arrayOf<Any>("w411dp-h891dp", 1f),
            arrayOf<Any>("w840dp-h1280dp", 1.3f)
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val info = HazelUpdater.ReleaseInfo("1.2.0", HazelUpdater.Channel.STABLE, 12_000_000, "", "", "", "hazel.apk")

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(text: String) {
        val node = compose.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true)[0].fetchSemanticsNode()
        assertTrue("$text runs off the screen", node.positionOnScreen.x + node.size.width <= context.resources.displayMetrics.widthPixels)
    }

    private fun softwareScreen() {
        show { SoftwareUpdateScreen(onBack = {}, onNavigateToHazelUpdate = {}, onNavigateToYtDlpUpdate = {}) }
        listOf(R.string.upd_action_releases, R.string.upd_label_arch, R.string.upd_section_components).forEach { assertInside(context.getString(it)) }
    }

    private fun everyState() {
        val states = listOf(
            UiState.Idle, UiState.Checking, UiState.Available(info),
            UiState.Downloading(info, progressBytes = 6_000_000, totalBytes = 12_000_000, speedBps = 2_000_000, etaSeconds = 3),
            UiState.Downloading(info),
            UiState.ReadyToInstall(info, File("hazel.apk")), UiState.Error("Network unreachable"), UiState.FdroidManaged
        )
        var state by androidx.compose.runtime.mutableStateOf<UiState>(UiState.Idle)
        show { HazelStatus(state, "1.1.15", HazelUpdater.Channel.STABLE, {}, {}, {}, {}, {}, {}) }
        states.forEach { next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            if (next is UiState.Downloading && next.totalBytes > 0) {
                val bars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
                assertTrue("download shows the bar and the mark", bars.size >= 2)
                assertInside("50%")
            }
        }
    }

    private fun everyEngineState() {
        val engine = com.hazel.android.update.YtDlpUpdater.ReleaseInfo("2026.11.02", com.hazel.android.update.YtDlpUpdater.Channel.STABLE, 3_000_000)
        val states = listOf(
            EngineState.Idle, EngineState.Checking, EngineState.Available(engine),
            EngineState.Updating(engine, progressBytes = 1_500_000, totalBytes = 3_000_000),
            EngineState.Updating(engine), EngineState.Installed("2026.11.02"), EngineState.Error("Network unreachable")
        )
        var state by androidx.compose.runtime.mutableStateOf<EngineState>(EngineState.Idle)
        show {
            androidx.compose.foundation.layout.Column {
                com.hazel.android.update.YtDlpStatusCard(state, "2026.10.26", com.hazel.android.update.YtDlpUpdater.Channel.STABLE, {}, {}, {}, {}) { _, _ -> }
                com.hazel.android.update.UpdateSegmentedButton(listOf("Stable", "Nightly", "Master"), 0) {}
            }
        }
        assertInside("Master")
        states.forEach { next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            if (next is EngineState.Updating && next.totalBytes > 0) {
                val bars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
                assertTrue("engine update shows the bar and the mark", bars.size >= 2)
                assertInside("50%")
            }
        }
    }

    @Test @Config(sdk = [24]) fun `every engine state draws on the oldest android`() = everyEngineState()
    @Test @Config(sdk = [35]) fun `every engine state draws on the newest android`() = everyEngineState()
    @Test @Config(sdk = [24]) fun `software screen fits on the oldest android`() = softwareScreen()
    @Test @Config(sdk = [35]) fun `software screen fits on the newest android`() = softwareScreen()
    @Test @Config(sdk = [24]) fun `every update state draws on the oldest android`() = everyState()
    @Test @Config(sdk = [35]) fun `every update state draws on the newest android`() = everyState()
}
