package com.hazel.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The placeholder fills a small and a large thumbnail slot in light and dark, on any accent. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ArtworkPlaceholderTest(private val dark: Boolean, private val accent: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} {1}")
        fun themes() = listOf(arrayOf<Any>(true, "Cyan"), arrayOf<Any>(false, "Amber"))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `fills small and large slots`() {
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent) {
                Box {
                    ArtworkPlaceholder(Modifier.size(88.dp, 50.dp).testTag("small"))
                    ArtworkPlaceholder(Modifier.size(360.dp, 203.dp).testTag("large"))
                }
            }
        }
        compose.onNodeWithTag("small").assertWidthIsAtLeast(88.dp).assertHeightIsAtLeast(50.dp)
        compose.onNodeWithTag("large").assertWidthIsAtLeast(360.dp).assertHeightIsAtLeast(203.dp)
    }
}
