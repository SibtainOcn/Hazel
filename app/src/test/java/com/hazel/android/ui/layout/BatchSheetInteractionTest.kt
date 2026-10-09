package com.hazel.android.ui.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Before
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
 * Taps every control on the Download all sheet, upright and with the phone on its side
 * (where the sheet is two panes), and checks each one does its job: the buttons and option
 * chips open their sheet or dialog, search narrows the list, selecting starts, a link opens
 * its own sheet, and that sheet's title and author can be edited.
 *
 * Each control is scrolled into view and must be on screen before it is tapped, so a
 * control drawn off the edge or under another fails here rather than on a phone.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BatchSheetInteractionTest(private val size: String, private val qualifiers: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("phone", "w360dp-h780dp-port-xxhdpi"),
            arrayOf("phone_land", "w780dp-h360dp-land-xxhdpi"),
            arrayOf("phone_land_wide", "w915dp-h411dp-land-xxhdpi"),
            arrayOf("tablet_land", "w1280dp-h800dp-land-xhdpi")
        )

        private const val URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
    }

    private val device = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
        }
    }

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(device).around(compose)

    private fun string(id: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    private val results = listOf(
        "Me at the zoo", "Big Buck Bunny", "Sintel, the full film in 4K", "Tears of Steel", "Cosmos Laundromat"
    ).mapIndexed { i, title ->
        MediaInfo("$URL&i=$i", title, "Blender Studio", null, 60 + i * 37, emptyList(), emptyList())
    }

    /** Bumped to throw the sheet away and draw a fresh one, closing whatever it had open. */
    private var generation by mutableIntStateOf(0)

    @Before
    fun showSheet() {
        compose.setContent {
            HazelTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    key(generation) {
                        BatchDownloadSheet(
                            results = results,
                            options = DownloadOptions(),
                            onOptionsChange = {},
                            saveDirs = SaveDirs(),
                            onOpenSaveDir = {},
                            onPickSaveDir = { _, _ -> },
                            onResetSaveDir = {},
                            onResolveFormats = {},
                            onRemove = {},
                            onDownload = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun windows() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size

    private fun reset() {
        generation++
        compose.waitForIdle()
    }

    /** Brings [node] into view, checks it is on screen, and taps it. */
    private fun tap(node: SemanticsNodeInteraction) {
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed()
        node.performClick()
        compose.waitForIdle()
    }

    /** Tapping what [find] returns opens a sheet, dialog or menu of its own. */
    private fun opensWindow(what: String, find: () -> SemanticsNodeInteraction) {
        val before = windows()
        tap(find())
        check(windows() > before) { "$what opened nothing on $size" }
        reset()
    }

    private fun byDescription(text: String) = compose.onNodeWithContentDescription(text, substring = true)

    private fun openAdjust() {
        tap(compose.onNodeWithText(string(R.string.format_sheet_adjust_video)))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun everyControlWorks() {
        captureScreenRoboImage("build/outputs/roborazzi/batch/sheet_$size.png")

        // The buttons along the bottom, and the link button beside them.
        opensWindow("download type") { byDescription(string(R.string.batch_bar_download_type)) }
        opensWindow("quality") { byDescription(string(R.string.batch_bar_quality, "")) }
        opensWindow("save location") { byDescription(string(R.string.batch_bar_save_location)) }
        opensWindow("link options") { byDescription(string(R.string.sheet_link_options)) }
        opensWindow("list menu") { byDescription(string(R.string.batch_list_options)) }

        // Every chip in the Adjust section opens its own dialog or sheet.
        openAdjust()
        captureScreenRoboImage("build/outputs/roborazzi/batch/sheet_adjust_$size.png")
        reset()
        val chips = listOf(
            R.string.batch_bar_thumbnail, R.string.batch_bar_chapters, R.string.batch_bar_subtitles,
            R.string.batch_bar_sponsorblock, R.string.cookies_title
        )
        chips.forEach { label ->
            opensWindow(string(label)) {
                openAdjust()
                compose.onNodeWithText(string(label))
            }
        }
        // The filename dialog puts the keyboard's cursor in its field, and a blinking cursor
        // never lets the test's clock settle at a phone's density. Where it settles it is
        // opened like the rest; elsewhere the chip must be on screen and tappable.
        openAdjust()
        val filenameChip = compose.onNodeWithText(string(R.string.batch_bar_filename_template))
        runCatching { filenameChip.performScrollTo() }
        filenameChip.assertIsDisplayed().assertHasClickAction()
        reset()
        if (size == "tablet_land") {
            opensWindow("filename template") {
                openAdjust()
                compose.onNodeWithText(string(R.string.batch_bar_filename_template))
            }
        }
        opensWindow("container") {
            openAdjust()
            compose.onNodeWithText(string(R.string.batch_bar_container), substring = true)
        }

        // Incognito answers with a message over the sheet rather than a window.
        tap(byDescription("Incognito"))
        reset()

        // Selecting: the button turns into the one that stops it.
        tap(byDescription(string(R.string.batch_start_selecting)))
        byDescription(string(R.string.batch_stop_selecting)).assertIsDisplayed()
        reset()

        // Search narrows the list to what matches.
        tap(byDescription(string(R.string.batch_search_open)))
        compose.onNode(hasSetTextAction()).performTextInput("Bunny")
        compose.waitForIdle()
        compose.onNodeWithText("Big Buck Bunny").assertIsDisplayed()
        check(compose.onAllNodesWithText("Me at the zoo").fetchSemanticsNodes().isEmpty()) {
            "search did not narrow the list on $size"
        }
        reset()

        // Download is on screen and can be tapped.
        compose.onAllNodesWithText(string(R.string.batch_download_action))
            .filter(hasClickAction()).onFirst().assertIsDisplayed()

        // A link opens its own sheet, whose title and author can be renamed.
        val before = windows()
        tap(compose.onNodeWithText("Me at the zoo"))
        check(windows() > before) { "a link's own sheet did not open on $size" }
        tap(compose.onNodeWithText(string(R.string.format_sheet_section_details)))
        compose.onNode(hasSetTextAction() and hasText("Me at the zoo")).performTextReplacement("Zoo, renamed")
        compose.onNode(hasSetTextAction() and hasText("Blender Studio")).performTextReplacement("Someone else")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Zoo, renamed")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Someone else")).assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/batch/link_sheet_$size.png")
    }
}
