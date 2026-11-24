package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isSelectable
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.download.CutMode
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tap moves the choice at once, before the saved value comes back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CutModeSelectTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the tapped mode is chosen straight away and the old one lets go`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var asked: CutMode? = null
        compose.setContent {
            HazelTheme(darkTheme = true) {
                // The saved value never changes here, as while a save is still on its way.
                CutModePicker(selected = CutMode.PRECISE, afterAvailable = true, onSelect = { asked = it })
            }
        }
        fun row(label: Int) = compose.onNode(isSelectable() and hasText(context.getString(label), substring = true), useUnmergedTree = false)

        row(R.string.options_cut_precise).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.options_cut_quick)).performClick()
        row(R.string.options_cut_quick).assertIsSelected()
        row(R.string.options_cut_precise).assertIsNotSelected()
        assertEquals(CutMode.QUICK, asked)
    }
}
