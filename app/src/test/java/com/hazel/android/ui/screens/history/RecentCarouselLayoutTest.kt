package com.hazel.android.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hazel.android.data.HistoryEntry
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import org.junit.Assert.assertEquals
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
class RecentCarouselLayoutTest(private val locale: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `recent cards show their details and hold opens the entry's actions`() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        val entries = (1..3).map { i ->
            HistoryEntry(
                id = i.toLong(), url = "https://example.com/$i", title = "A long title for download $i",
                author = "Author $i", thumbnail = null, durationSeconds = 0, fileName = "file$i.jpg",
                fileUri = "", savedPath = "", isVideo = false, sizeBytes = 2048L * i,
                completedAt = i.toLong(), isImage = true
            )
        }
        var held: Long? = null
        compose.setContent {
            HazelTheme(darkTheme = true, glass = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassScreen().padding(20.dp)) {
                        RecentCarousel(
                            entries = entries,
                            presence = { true },
                            marks = { null },
                            actionsFor = { entry -> EntryActions({}, { held = entry.id }, {}, {}, {}) },
                            sidePadding = 20.dp
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("A long title for download 1").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1L, held)
        val options = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.hazel.android.R.string.history_card_options)
        compose.onAllNodesWithContentDescription(options, useUnmergedTree = true)[0].assertIsDisplayed()
        saveScreen("recent-carousel-$locale")
    }
}
