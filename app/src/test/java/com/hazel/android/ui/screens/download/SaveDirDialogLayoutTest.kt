package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.SaveDir
import com.hazel.android.ui.theme.HazelTheme
import org.junit.Assert.assertTrue
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
class SaveDirDialogLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the largest font on a small screen keeps both buttons on screen`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    SaveDirDialog(
                        isVideo = true,
                        saveDir = SaveDir("content://com.android.externalstorage.documents/tree/primary%3AMovies", "Movies"),
                        label = "Movies",
                        onOpen = {},
                        onPick = {},
                        onReset = {},
                        onDismiss = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        val screen = context.resources.displayMetrics
        listOf(R.string.format_sheet_open_folder, R.string.save_location_close).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id)).assertIsDisplayed().fetchSemanticsNode()
            val right = node.positionOnScreen.x + node.size.width
            val bottom = node.positionOnScreen.y + node.size.height
            assertTrue("${context.getString(id)} runs off the right edge", right <= screen.widthPixels)
            assertTrue("${context.getString(id)} runs off the bottom", bottom <= screen.heightPixels)
        }
    }
}
