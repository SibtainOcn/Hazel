package com.hazel.android.ui.layout

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Title and Author boxes on the download sheet ask the keyboard to start in capitals:
 * the title at the start of each sentence, the author at the start of each word. Without
 * it the keyboard opens in small letters and stays there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class DetailsKeyboardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

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

    @Test
    fun titleStartsSentencesInCapitals() =
        assertCaps(TITLE, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

    @Test
    fun authorStartsWordsInCapitals() =
        assertCaps(AUTHOR, InputType.TYPE_TEXT_FLAG_CAP_WORDS)

    private fun assertCaps(fieldText: String, flag: Int) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerHost) {
                HazelTheme {
                    FormatSheet(
                        info = MediaInfo(URL, TITLE, AUTHOR, null, 24, emptyList(), emptyList()),
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
            }
        }
        compose.waitForIdle()

        // Open Details if it starts closed, then tap the field itself.
        val field = hasSetTextAction() and SemanticsMatcher("text is $fieldText") {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == fieldText
        }
        if (compose.onAllNodes(field, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodesWithText(string(R.string.format_sheet_section_details))[0].performClick()
            compose.waitForIdle()
        }
        compose.onAllNodes(field, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()

        val editor = EditorInfo()
        val view = textEditorView()
        view.onCreateInputConnection(editor)
        assertTrue(
            "inputType ${Integer.toHexString(editor.inputType)} lacks ${Integer.toHexString(flag)}",
            editor.inputType and flag == flag
        )
    }

    /** The window's view that is taking text input, wherever the sheet put its window. */
    @Suppress("UNCHECKED_CAST")
    private fun textEditorView(): View {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }
            .get(instance) as List<View>
        fun find(v: View): View? {
            if (v.onCheckIsTextEditor() && v.hasFocus()) return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return views.reversed().firstNotNullOfOrNull { find(it) }
            ?: error("no view is taking text input")
    }

    private companion object {
        const val URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        const val TITLE = "video by yaudahadudu"
        const val AUTHOR = "kukuh arif"
    }
}
