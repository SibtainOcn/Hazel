package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import com.hazel.android.ui.theme.glassSheetColor
import com.hazel.android.ui.theme.sheetBlackColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The format list's skeleton, rail and rows, on the sheet's own colour in every look. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h560dp")
class FormatSkeletonThemesTest(private val name: String, private val dark: Boolean, private val style: GlassStyle?) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any?>> = listOf<Array<Any?>>(
            arrayOf("light", false, null),
            arrayOf("dark", true, null)
        ) + (GlassStyle.entries - GlassStyle.WALLPAPER).flatMap { style ->
            listOf(arrayOf<Any?>("glass-${style.key}-dark", true, style), arrayOf<Any?>("glass-${style.key}-light", false, style))
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun skeleton() {
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = style != null, glassStyle = style ?: GlassStyle.DEFAULT) {
                Box(Modifier.fillMaxSize().glassScreen().background(glassSheetColor(sheetBlackColor)).padding(12.dp)) {
                    Row {
                        FormatRailShimmer(stops = 7, modifier = Modifier.width(92.dp))
                        Spacer(Modifier.width(10.dp))
                        FormatListShimmer(rows = 6)
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("skeleton-$name")
    }
}
