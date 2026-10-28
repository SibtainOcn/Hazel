package com.hazel.android.ui.screens.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ChoiceDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val choices = (0 until 30).map { it to "Choice $it" }

    @Test
    fun opensOnTheSelectedChoiceFarDownTheList() {
        compose.setContent {
            HazelTheme(darkTheme = true) {
                SingleChoiceDialog(title = "Pick", choices = choices, selected = 24, onSelect = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Choice 24").assertIsDisplayed()
        compose.onNodeWithText("Choice 0").assertDoesNotExist()
    }

    @Test
    fun aChoiceNearTheTopOpensAtTheTop() {
        compose.setContent {
            HazelTheme(darkTheme = true) {
                SingleChoiceDialog(title = "Pick", choices = choices, selected = 1, onSelect = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Choice 0").assertIsDisplayed()
        compose.onNodeWithText("Choice 1").assertIsDisplayed()
    }
}
