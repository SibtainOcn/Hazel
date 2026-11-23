package com.hazel.android.ui.screens.download

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import com.hazel.android.ui.theme.glassSheetColor
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.ui.theme.sheetButtonColor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The download sheet's Details and Save rows as connected groups, in long languages, the largest font and every look. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SheetFieldGroupsLayoutTest(
    private val locale: String,
    private val dark: Boolean,
    private val glass: Boolean,
    private val font: Float
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark{1}-glass{2}-font{3}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").map { arrayOf<Any>(it, true, false, 2f) } +
            listOf(arrayOf<Any>("en", false, false, 1f), arrayOf<Any>("en", true, true, 1f), arrayOf<Any>("en", false, true, 1f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val look get() = "$locale-${if (dark) "dark" else "light"}${if (glass) "-glass" else ""}-f${font.toInt()}"

    @Composable
    private fun Sections() {
        val field = sheetButtonColor
        Column(Modifier.padding(16.dp)) {
            SheetSection(Icons.Outlined.HighQuality, "Video quality", "1080p", expanded = false, onToggle = {}, iconShape = MaterialShapes.Cookie7Sided) {}
            SheetSection(Icons.Outlined.Tune, "Adjust video", "Thumbnail", expanded = false, onToggle = {}, iconShape = MaterialShapes.Cookie4Sided, iconTurn = ADJUST_ICON_TURN) {}
            SheetSection(Icons.Outlined.Description, "Details", "Title", expanded = true, onToggle = {}, iconShape = MaterialShapes.Cookie6Sided) {
                Column(verticalArrangement = Arrangement.spacedBy(FIELD_GROUP_GAP)) {
                    EditableField(
                        label = androidx.compose.ui.res.stringResource(R.string.format_sheet_label_title),
                        value = "The Lighthouse Keepers | a long title that runs past the edge",
                        onValueChange = {}, color = field, shape = fieldGroupShape(0, 2), tagLabel = true
                    )
                    EditableField(
                        label = androidx.compose.ui.res.stringResource(R.string.format_sheet_label_author),
                        value = "Author name",
                        onValueChange = {}, color = field, shape = fieldGroupShape(1, 2), tagLabel = true
                    )
                }
            }
            SheetSection(Icons.Outlined.FolderOpen, "Save", "Download/Hazel/Video", expanded = true, onToggle = {}, iconShape = MaterialShapes.Pentagon) {
                Column(verticalArrangement = Arrangement.spacedBy(FIELD_GROUP_GAP)) {
                    SaveDirField(label = "Download/Hazel/Video", onClick = {}, color = field, shape = fieldGroupShape(0, 2))
                    PickerField(
                        label = androidx.compose.ui.res.stringResource(R.string.format_sheet_label_container),
                        value = "Default", onClick = {}, color = field, shape = fieldGroupShape(1, 2), tagLabel = true
                    )
                }
            }
        }
    }

    @Test
    fun groups() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp")
        compose.setContent {
            HazelTheme(darkTheme = dark, glass = glass, glassStyle = GlassStyle.DEFAULT) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                    Box(Modifier.fillMaxSize().glassScreen().background(glassSheetColor(sheetBlackColor))) { Sections() }
                }
            }
        }
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        listOf(R.string.format_sheet_label_title, R.string.format_sheet_label_author, R.string.format_sheet_save_dir, R.string.format_sheet_label_container)
            .map(context::getString)
            .forEach { text ->
                val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
                assertTrue(text, nodes.isNotEmpty())
                nodes.forEach { assertTrue(text, it.boundsInRoot.right <= width) }
            }
        saveScreen("sheet-fields-$look")
    }
}
