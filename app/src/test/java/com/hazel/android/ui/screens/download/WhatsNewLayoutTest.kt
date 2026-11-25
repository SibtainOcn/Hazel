package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WhatsNewLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `highlights card shows every row and its button`() {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp-400dpi")
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    WhatsNewDialog(onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        saveScreen("whats-new-1.2.17-$locale")
        listOf(R.string.whats_new_gallery_title, R.string.whats_new_accounts_desc, R.string.whats_new_done).forEach {
            compose.onNodeWithText(context.getString(it)).performScrollTo().assertIsDisplayed()
        }
    }
}
