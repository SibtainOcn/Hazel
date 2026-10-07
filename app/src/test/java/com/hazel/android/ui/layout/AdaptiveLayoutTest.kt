package com.hazel.android.ui.layout

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hazel.android.R
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.screens.download.AlreadyDownloadedDialog
import com.hazel.android.ui.screens.download.FormatSelectionSheet
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.GettingStartedDialog
import com.hazel.android.ui.screens.download.LinkOptionsDialog
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.RemoveEntryDialog
import com.hazel.android.ui.screens.more.AppearanceScreen
import com.hazel.android.ui.screens.more.LanguageSheet
import com.hazel.android.ui.screens.more.StorageLocationsScreen
import com.hazel.android.ui.screens.queue.FailedCard
import com.hazel.android.ui.screens.queue.FailureLogSheet
import com.hazel.android.ui.theme.HazelTheme
import java.util.Locale
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screens and dialogs whose text has to give way on a crowded line, in every
 * language the app ships, at the ordinary font size and the large ones, on a narrow phone.
 *
 * Each case fails when a word is broken into a column of letters to make room for a button,
 * which is what a fixed row does with a long translation or a large font. Hyphenation that
 * leaves at least two letters each side is allowed. Every case also writes a screenshot to
 * app/build/outputs/roborazzi/layout, so the result can be looked at as well as checked.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class AdaptiveLayoutTest(private val language: String, private val fontScale: Float) {

    companion object {
        /** Resource folder suffix to language tag, for every translation the app ships. */
        private val LANGUAGES = linkedMapOf(
            "en" to "en", "ru" to "ru", "de" to "de", "fr" to "fr", "es" to "es",
            "pt-rBR" to "pt-BR", "in" to "id", "hi" to "hi", "ja" to "ja", "zh-rCN" to "zh-CN"
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} x{1}")
        fun cases(): List<Array<Any>> =
            LANGUAGES.keys.flatMap { lang -> listOf(1.0f, 1.35f, 2.0f).map { arrayOf<Any>(lang, it) } }

        private const val URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
    }

    private val tag get() = LANGUAGES.getValue(language)

    /**
     * The device's language and font size, set before the test's activity starts, as a phone
     * set that way would have them. Set on the device rather than inside Compose because a
     * dialog or a sheet opens in a window of its own, which takes them from the device.
     */
    private val device = object : ExternalResource() {
        override fun before() {
            Locale.setDefault(Locale.forLanguageTag(tag))
            RuntimeEnvironment.setQualifiers("+$language")
            RuntimeEnvironment.setFontScale(fontScale)
        }
    }

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(device).around(compose)

    private fun string(id: Int) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    /** A host for the folder pickers, which ask for one; nothing is ever launched. */
    private val pickerHost = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) = Unit
        }
    }

    /** Shows [content] in the app's dark theme. */
    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerHost) {
                HazelTheme(darkTheme = true) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
        check(localesMatch()) { "device is not in $tag" }
    }

    /** The test really is in this case's language and font size. */
    private fun localesMatch(): Boolean {
        val config = ApplicationProvider.getApplicationContext<android.content.Context>().resources.configuration
        return config.locales[0].language == Locale.forLanguageTag(tag).language && config.fontScale == fontScale
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun checkAndCapture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/layout/${name}_${language}_x$fontScale.png")
        val broken = brokenWords()
        assert(broken.isEmpty()) {
            "$name in $language at font x$fontScale broke words to fit: ${broken.joinToString("; ")}"
        }
    }

    /**
     * Every text on screen, in every window, whose lines break a word into a sliver: a piece
     * of one letter, or a word run down three lines or more.
     */
    private fun brokenWords(): List<String> {
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        val found = mutableListOf<String>()
        texts.forEach { node ->
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return@forEach
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            results.forEach { layout -> found += brokenIn(layout) }
        }
        return found
    }

    private fun brokenIn(layout: TextLayoutResult): List<String> {
        val text = layout.layoutInput.text.text
        val found = mutableListOf<String>()
        // How many lines each word touches, keyed by where the word starts.
        val linesOfWord = HashMap<Int, Int>()
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line, visibleEnd = true)
            if (start >= end) continue
            var i = start
            while (i < end) {
                if (isWordChar(text[i])) {
                    val wordStart = wordStartOf(text, i)
                    linesOfWord[wordStart] = (linesOfWord[wordStart] ?: 0) + 1
                    while (i < end && isWordChar(text[i])) i++
                } else i++
            }
            if (line == layout.lineCount - 1) continue
            val breakAt = layout.getLineEnd(line)
            if (breakAt <= 0 || breakAt >= text.length) continue
            val before = text[breakAt - 1]
            val after = text[breakAt]
            if (isWordChar(before) && isWordChar(after) && !isCjk(before) && !isCjk(after)) {
                val left = breakAt - wordStartOf(text, breakAt - 1)
                var right = 0
                while (breakAt + right < text.length && isWordChar(text[breakAt + right])) right++
                if (left < 2 || right < 2) found += "\"${wordAround(text, breakAt)}\" split ${left}|$right"
            }
        }
        linesOfWord.filterValues { it >= 3 }.keys.forEach { found += "\"${wordAround(text, it)}\" over 3+ lines" }
        return found
    }

    /** A letter of a spaced language. Chinese and Japanese break between any two characters. */
    private fun isWordChar(c: Char) = c.isLetterOrDigit() && !isCjk(c)

    private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
        else -> false
    }

    private fun wordStartOf(text: String, at: Int): Int {
        var s = at
        while (s > 0 && isWordChar(text[s - 1])) s--
        return s
    }

    private fun wordAround(text: String, at: Int): String {
        val s = wordStartOf(text, minOf(at, text.length - 1))
        var e = s
        while (e < text.length && isWordChar(text[e])) e++
        return text.substring(s, e)
    }

    // ── The cases ──

    @Test
    fun alreadyDownloadedDialog() {
        show {
            AlreadyDownloadedDialog(
                entry = HistoryEntry(
                    id = 1, url = URL, title = "Video by yaudahadudu", author = "Kukuh Arif",
                    thumbnail = null, durationSeconds = 24, fileName = "video.mp4", fileUri = "",
                    savedPath = "Download/Hazel/Video", isVideo = true, sizeBytes = 3_700_000,
                    completedAt = System.currentTimeMillis() - 7_200_000
                ),
                onPlay = {}, onOpenLocation = {}, onDownloadAgain = {}, onDismiss = {}
            )
        }
        checkAndCapture("already_downloaded")
    }

    @Test
    fun noResultsDialog() {
        show {
            NoResultsDialog(
                message = "ERROR: [youtube] jNQXAC9IVRw: Sign in to confirm your age",
                canFetchCookies = true, canContinue = true, canAddCookies = true, link = URL,
                onCopyLog = {}, onGetCookies = {}, onContinueAnyway = {}, onDismiss = {}
            )
        }
        checkAndCapture("no_results")
    }

    @Test
    fun removeSearchDialog() {
        show { RemoveEntryDialog(entry = URL, onConfirm = {}, onDismiss = {}) }
        checkAndCapture("remove_search")
    }

    @Test
    fun linkOptionsDialog() {
        show { LinkOptionsDialog(links = listOf(URL), onFeedback = {}, onDismiss = {}) }
        checkAndCapture("link_options")
    }

    private val failed = FailedDownload(
        url = URL, title = "Me at the zoo", author = "jawed", thumbnail = null, isVideo = true,
        errorLog = "ERROR: [youtube] jNQXAC9IVRw: Video unavailable"
    )

    @Test
    fun failedCard() {
        show {
            Box(Modifier.padding(16.dp)) {
                FailedCard(item = failed, onOpen = {}, onViewLog = {}, onRetry = {}, onDismiss = {})
            }
        }
        checkAndCapture("failed_card")
    }

    @Test
    fun failureLogSheet() {
        show { FailureLogSheet(item = failed, onCopy = {}, onCopyUrl = {}, onRetry = {}, onDismiss = {}) }
        checkAndCapture("failure_log")
    }

    @Test
    fun downloadSheetWithPlay() {
        show {
            FormatSheet(
                info = MediaInfo(URL, "Video by yaudahadudu", "Kukuh Arif", null, 24, emptyList(), emptyList()),
                options = DownloadOptions(),
                onOptionsChange = {},
                saveDirs = SaveDirs(),
                onPlay = {},
                onOpenSaveDir = {},
                onPickSaveDir = { _, _ -> },
                onResetSaveDir = {},
                onDownload = { _, _, _, _, _ -> },
                onDismiss = {}
            )
        }
        checkAndCapture("download_sheet")
    }

    @Test
    fun formatListHeader() {
        show {
            FormatSelectionSheet(
                info = MediaInfo(URL, "Me at the zoo", "jawed", null, 19, emptyList(), emptyList()),
                selected = null, onConfirm = {}, onDismiss = {}, onRefresh = { _, _ -> }
            )
        }
        checkAndCapture("format_list")
    }

    @Test
    fun gettingStartedPermissionStep() {
        show { GettingStartedDialog(onOpenBatterySettings = {}, onDismiss = {}) }
        // Through to the first step that asks: Deny and Allow side by side.
        repeat(6) {
            val deny = compose.onAllNodesWithText(string(R.string.guide_deny), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (deny.isNotEmpty()) return@repeat
            compose.onAllNodesWithText(string(R.string.guide_next), useUnmergedTree = true)[0].performClick()
            compose.waitForIdle()
        }
        checkAndCapture("getting_started")
    }

    @Test
    fun downloadSettings() {
        show { StorageLocationsScreen(onBack = {}) }
        checkAndCapture("download_settings")
    }

    @Test
    fun appearance() {
        show {
            AppearanceScreen(isDarkTheme = true, onToggleTheme = {}, accentName = "Teal", onAccentChanged = {}, onBack = {})
        }
        checkAndCapture("appearance")
    }

    @Test
    fun languageSheet() {
        show { LanguageSheet(current = tag, onConfirm = {}, onDismiss = {}) }
        checkAndCapture("language")
    }
}
