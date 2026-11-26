package com.hazel.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.components.SegmentIconButton
import com.hazel.android.ui.components.SegmentPosition
import com.hazel.android.ui.saveScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tonal fills in the accent on each theme, saved for a look; the contrast itself is checked in AccentTintTest. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AccentTintShotsTest(private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("glass-dark", "glass-light", "dark", "light").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tonal fills on every accent`() {
        RuntimeEnvironment.setQualifiers("en-w360dp-h640dp")
        val dark = theme.endsWith("dark")
        val glass = theme.startsWith("glass")
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass) {
                val ground = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                Column(if (glass) ground.glassScreen() else ground) {
                    listOf("White", "Cyan", "Lavender", "Gold", "Crimson", "Slate").forEach { accent ->
                        HazelTheme(darkTheme = dark, accentName = accent, glass = glass) {
                            Row(
                                Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(40.dp).background(accentTint(), RoundedCornerShape(12.dp)), Alignment.Center) {
                                    Icon(Icons.Filled.History, null, tint = accentInk())
                                }
                                Box(Modifier.background(accentTint(), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text(accent, color = accentInk())
                                }
                                Box(Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text("On", color = MaterialTheme.colorScheme.onPrimary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SegmentIconButton({}, SegmentPosition.First, size = 36.dp, selected = true) { Icon(Icons.Filled.Search, null) }
                                    SegmentIconButton({}, SegmentPosition.Middle, size = 36.dp) { Icon(Icons.Filled.FilterList, null) }
                                    SegmentIconButton({}, SegmentPosition.Last, size = 36.dp) { Icon(Icons.Filled.MoreVert, null) }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("accent-tint-$theme")
    }
}
