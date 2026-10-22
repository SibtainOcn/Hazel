package com.hazel.android.ui.layout

import androidx.compose.ui.test.getBoundsInRoot
import com.hazel.android.ui.theme.GlassStyle
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.ui.components.MediaCard
import com.hazel.android.ui.screens.download.GettingStartedDialog
import com.hazel.android.ui.screens.download.SearchSourceSheet
import com.hazel.android.ui.screens.more.AppearanceScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glass theme on the screens, sheets and dialogs it changes, in both themes, plus the
 * Appearance switch and the getting-started appearance card saving what is picked.
 * Screenshots go to app/build/outputs/roborazzi/glass/ with -PlayoutScreenshots=all.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassThemeTest(private val theme: String, private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(arrayOf("dark", true), arrayOf("light", false))
    }

    private val device = object : ExternalResource() {
        override fun before() = RuntimeEnvironment.setQualifiers("w360dp-h780dp-port-xxhdpi")
    }

    private val compose = createComposeRule()
    private val shots = LayoutShots()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettleGuard()).around(device).around(compose).around(shots)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun show(accent: String = "White", content: @Composable () -> Unit) {
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = true) {
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().glassScreen()) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        shots.take("build/outputs/roborazzi/glass/${name}_$theme.png")
    }

    @Test
    fun homeCards() {
        show {
            androidx.compose.foundation.layout.Column {
                listOf("Me at the zoo", "Big Buck Bunny").forEachIndexed { i, title ->
                    MediaCard(
                        info = MediaInfo("https://example.com/$i", title, "Blender Studio", null, 95, emptyList(), emptyList()),
                        isDownloading = false,
                        alreadyDownloaded = i == 1
                    )
                }
            }
        }
        compose.onNodeWithText("Me at the zoo").assertIsDisplayed()
        capture("home_cards")
    }

    @Test
    fun sheetIsReadableOverTheApp() {
        show { SearchSourceSheet(selected = SearchSource.YOUTUBE, onDismiss = {}) }
        capture("search_source_sheet")
    }

    @Test
    fun dialogIsReadableOverTheApp() {
        var confirmed = false
        show {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = { TextButton(onClick = { confirmed = true }) { Text("Clear") } },
                dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                title = { Text("Clear history") },
                text = { Text("Every entry in the list is removed. Files stay on the device.") }
            )
        }
        capture("alert_dialog")
        compose.onNodeWithText("Clear").assertIsDisplayed().performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun instantDialogIsGlass() {
        var confirmed = false
        show { com.hazel.android.ui.share.InstantDialog(onConfirm = { confirmed = true }, onTune = {}, glass = true) }
        capture("instant_dialog")
        compose.onNodeWithText(context.getString(R.string.instant_ok)).assertIsDisplayed().performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun sponsorScreenIsFlatGlass() {
        show { com.hazel.android.ui.screens.more.SponsorScreen(onBack = {}) }
        compose.onNodeWithContentDescription(context.getString(R.string.sponsor_back)).assertHasClickAction()
        capture("sponsor")
    }

    @Test
    fun ytDlpUpdateSwitchesFollowAccent() {
        show(accent = "Pink") { com.hazel.android.update.YtDlpUpdateScreen(onBack = {}) }
        capture("ytdlp_update")
    }

    @Test
    fun hazelUpdateScreenIsGlass() {
        show(accent = "Pink") { com.hazel.android.ui.screens.more.HazelUpdateScreen(onBack = {}) }
        capture("hazel_update")
    }

    @Test
    fun appearanceGlassSwitchToggles() {
        var glass by mutableStateOf(true)
        show {
            AppearanceScreen(
                isDarkTheme = dark,
                onToggleTheme = {},
                accentName = "White",
                onAccentChanged = {},
                glassEnabled = glass,
                onToggleGlass = { glass = !glass },
                glassStyle = GlassStyle.AURORA,
                onGlassStyleChanged = {},
                onBack = {}
            )
        }
        val label = context.getString(R.string.appearance_glass)
        compose.onNodeWithText(label).assertIsDisplayed()
        capture("appearance")
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
        assertEquals(false, glass)
    }

    @Test
    fun gettingStartedAppearanceCardSavesChoices() {
        runBlocking {
            SettingsRepository.setDarkTheme(context, dark)
            SettingsRepository.setGlassEnabled(context, true)
            SettingsRepository.setGlassStyle(context, GlassStyle.AURORA.key)
        }
        show {
            var open by remember { mutableStateOf(true) }
            if (open) GettingStartedDialog(onOpenBatterySettings = {}, onDismiss = { open = false })
        }
        val next = context.getString(R.string.guide_next)
        val nextTop = { compose.onNodeWithText(next).getBoundsInRoot().top }
        val firstTop = nextTop()

        compose.onNodeWithText(next).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.guide_step_appearance_title)).assertIsDisplayed()
        assertEquals(firstTop, nextTop())
        capture("guide_appearance")

        compose.onNodeWithText(context.getString(R.string.appearance_dark_theme)).assertHasClickAction().performClick()
        compose.onNodeWithText(context.getString(R.string.glass_style_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.glass_style_sunset)).assertIsDisplayed()
        capture("guide_glass_styles")
        compose.onNodeWithText(context.getString(R.string.glass_style_sunset)).performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            runBlocking {
                SettingsRepository.isDarkTheme(context).first() == !dark &&
                    SettingsRepository.getGlassStyle(context).first() == GlassStyle.SUNSET.key
            }
        }
        runBlocking {
            SettingsRepository.setDarkTheme(context, true)
            SettingsRepository.setGlassStyle(context, GlassStyle.AURORA.key)
        }

        compose.onNodeWithText(next).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.guide_step2_title)).assertIsDisplayed()
        assertEquals(firstTop, nextTop())
    }

    @Test
    fun colorSchemeIsTranslucentOnlyWithGlass() {
        var surface = Color.Unspecified
        var plain = Color.Unspecified
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = true) { surface = MaterialTheme.colorScheme.surface }
            HazelTheme(darkTheme = dark, glass = false) { plain = MaterialTheme.colorScheme.surface }
        }
        compose.waitForIdle()
        assert(surface.alpha < 1f)
        assertEquals(1f, plain.alpha)
    }
}
