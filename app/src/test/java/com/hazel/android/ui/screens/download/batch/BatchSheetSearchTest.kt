package com.hazel.android.ui.screens.download.batch

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BatchSheetSearchTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val links = (1..10).map {
        MediaInfo(
            url = "https://example.com/watch?v=$it",
            title = "Lecture S-$it",
            uploader = "Channel",
            thumbnail = null,
            durationSeconds = 600,
            videoFormats = emptyList(),
            audioFormats = emptyList()
        )
    }

    @Test
    fun `narrowing the search keeps the list height, so the field stays above the keyboard`() {
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                BatchDownloadSheet(
                    results = links,
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
        compose.waitForIdle()
        val list = compose.onNodeWithTag(BATCH_LINK_LIST_TAG, useUnmergedTree = true)
        val before = list.getBoundsInRoot()
        saveScreen("playlist_search_before")

        compose.onNodeWithContentDescription(context.getString(R.string.batch_search_open)).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("S-9")
        compose.waitForIdle()

        val after = list.getBoundsInRoot()
        saveScreen("playlist_search_one_match")
        assertEquals(280.dp, after.bottom - after.top)
        assertEquals(before.bottom - before.top, after.bottom - after.top)
    }
}
