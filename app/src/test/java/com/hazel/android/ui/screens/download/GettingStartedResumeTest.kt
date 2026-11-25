package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GettingStartedResumeTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `guide keeps its step when the screen is rebuilt`() {
        var dismissed = 0
        val restore = StateRestorationTester(compose)
        restore.setContent {
            HazelTheme(darkTheme = true, glass = false) {
                GettingStartedDialog(onOpenBatterySettings = {}, onDismiss = { dismissed++ })
            }
        }
        repeat(2) { compose.onNodeWithText(context.getString(R.string.guide_next)).performScrollTo().performClick() }
        val stepThree = context.getString(R.string.guide_step_badge, 3, 7)
        compose.onNodeWithText(stepThree).assertExists()

        restore.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(stepThree).assertExists()
        assertEquals(0, dismissed)
    }
}
