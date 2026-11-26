package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.SaveDir
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Save location screen with its recent folders, in long languages, the largest font and every look. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SaveLocationScreenLayoutTest(
    private val locale: String,
    private val dark: Boolean,
    private val glass: Boolean,
    private val font: Float
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark{1}-glass{2}-font{3}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it, true, false, 2f) } +
            listOf(arrayOf<Any>("en", false, false, 1f), arrayOf<Any>("en", true, true, 1f), arrayOf<Any>("en", false, true, 1f))

        private const val MOVIES = "content://com.android.externalstorage.documents/tree/primary%3AMovies%2FCartoons"
        private const val OGGY = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FOggy"
    }

    @get:Rule
    val compose = createComposeRule()

    private val look get() = "$locale-${if (dark) "dark" else "light"}${if (glass) "-glass" else ""}-f${font.toInt()}"

    @Test
    fun `recent folders show and every control stays on screen`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            SettingsRepository.clearRecentFolders(context)
            SettingsRepository.addRecentFolder(context, OGGY, "Documents/Oggy")
            SettingsRepository.addRecentFolder(context, MOVIES, "Movies/Cartoons")
        }
        var used: SaveDir? = null
        var closed = false
        // The screen is a window of its own, which takes its density from the configuration.
        RuntimeEnvironment.setFontScale(font)
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass) {
                run {
                    SaveLocationScreen(
                        isVideo = true,
                        saveDir = SaveDir(MOVIES, "Movies/Cartoons"),
                        label = "Movies/Cartoons",
                        onPick = {},
                        onReset = {},
                        onUseRecent = { used = it },
                        onDismiss = { closed = true },
                        usable = { _, _ -> true }
                    )
                }
            }
        }
        compose.waitForIdle()
        saveScreen("save-location-$look")

        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        listOf(R.string.save_location_recent, R.string.save_location_internal, R.string.format_sheet_open_folder, R.string.save_location_close)
            .map(context::getString)
            .forEach { text ->
                val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
                assertTrue("$text is missing", nodes.isNotEmpty())
                nodes.forEach { assertTrue("$text runs off the edge", it.boundsInRoot.right <= width) }
            }

        compose.onNodeWithText("Documents/Oggy").performScrollTo()
        compose.waitForIdle()
        saveScreen("save-location-scrolled-$look")
        compose.onNodeWithText("Documents/Oggy").performClick()
        compose.waitUntil(5_000) { closed }
        assertEquals(OGGY, used?.uri)
        assertTrue(closed)
    }

    @Test
    fun `a recent folder that is gone is reported and dropped`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            SettingsRepository.clearRecentFolders(context)
            SettingsRepository.addRecentFolder(context, OGGY, "Documents/Oggy")
        }
        var listed = true
        var used: SaveDir? = null
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass) {
                SaveLocationScreen(
                    isVideo = true,
                    saveDir = SaveDir(),
                    label = "",
                    onPick = {},
                    onReset = {},
                    onUseRecent = { used = it },
                    onDismiss = {},
                    // Usable while listing, gone by the time it is tapped.
                    usable = { _, _ -> listed }
                )
            }
        }
        // Both checks run on the IO dispatcher, which the test clock does not wait for.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Documents/Oggy").fetchSemanticsNodes().isNotEmpty()
        }
        listed = false
        compose.onNodeWithText("Documents/Oggy").performScrollTo().performClick()
        val message = context.getString(R.string.save_location_recent_missing)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(message, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(null, used)
        assertTrue(runBlocking { SettingsRepository.getRecentFolders(context).first() }.isEmpty())
    }

    @Test
    fun `a saved folder that was deleted is pointed out in the dialog`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { SettingsRepository.clearRecentFolders(context) }
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass) {
                SaveLocationScreen(
                    isVideo = true,
                    saveDir = SaveDir(OGGY, "Documents/Oggy"),
                    label = "Documents/Oggy",
                    onPick = {},
                    onReset = {},
                    onUseRecent = {},
                    onDismiss = {},
                    usable = { _, _ -> false }
                )
            }
        }
        val message = context.getString(R.string.save_location_current_missing, "Documents/Oggy")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(message, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        saveScreen("save-location-missing-$look")
    }
}
