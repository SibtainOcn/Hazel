package com.hazel.android.ui.screens.more

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The backup screen's card and buttons stay on screen from a small phone at large text to a tablet. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupScreenLayoutTest(private val qualifiers: String, private val fontScale: Float) {

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

    private fun check() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BackupScreen(onBack = {})
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.backup_backup, R.string.backup_last).forEach { id ->
            val node = compose.onAllNodesWithText(context.getString(id), useUnmergedTree = true)[0]
                .assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
        compose.onNodeWithText(context.getString(R.string.backup_none_yet), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun checkSheet() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = com.hazel.android.data.BackupFile(
            android.net.Uri.parse("content://missing/hazel_auto_backup_1.0_2026-10-01_10-00-00.json"),
            "hazel_auto_backup_1.0_2026-10-01_10-00-00.json", 2048, 0
        )
        compose.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BackupFileSheet(file, "", "", onRestore = {}, onShare = {}, onDelete = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        listOf(R.string.backup_share, R.string.backup_delete).forEach { id ->
            val node = compose.onAllNodesWithText(context.getString(id), useUnmergedTree = true)[0].fetchSemanticsNode()
            assertTrue("${context.getString(id)} runs off the screen", node.positionOnScreen.x + node.size.width <= width)
        }
    }

    @Test
    @Config(sdk = [24])
    fun `sheet fits on the oldest android`() = checkSheet()

    @Test
    @Config(sdk = [35])
    fun `sheet fits on the newest android`() = checkSheet()

    @Test
    @Config(sdk = [24])
    fun `fits on the oldest android`() = check()

    @Test
    @Config(sdk = [35])
    fun `fits on the newest android`() = check()
}
