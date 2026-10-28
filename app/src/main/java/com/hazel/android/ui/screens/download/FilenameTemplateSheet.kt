package com.hazel.android.ui.screens.download

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.download.FilenameTemplate
import com.hazel.android.ui.components.ExpressiveTextField
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.theme.sheetBlackColor
import kotlinx.coroutines.launch

/** A preset by name, with the template it fills in. */
private class Preset(val template: String, @StringRes val name: Int, val icon: ImageVector)

private val Presets = FilenameTemplate.PRESETS.zip(
    listOf(
        R.string.filename_preset_title to Icons.Filled.Title,
        R.string.filename_preset_uploader_title to Icons.Filled.Person,
        R.string.filename_preset_date_title to Icons.Filled.CalendarToday,
        R.string.filename_preset_playlist_title to Icons.Filled.FormatListNumbered,
        R.string.filename_preset_title_id to Icons.Filled.Tag,
        R.string.filename_preset_title_resolution to Icons.Filled.HighQuality,
        R.string.filename_preset_artist_track to Icons.Filled.MusicNote
    )
) { template, (name, icon) -> Preset(template, name, icon) }

/**
 * The yt-dlp output template that names the saved file, in a sheet.
 *
 * It opens at half height with the keyboard closed. Once the keyboard is up, the presets and
 * fields fold into two scrolling rows and the sheet rises to fit them, so the field always
 * sits just above the keyboard; when it goes, the sheet settles back. Saving cleans the
 * template the same way a download does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilenameTemplateSheet(
    template: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(TextFieldValue(template, TextRange(template.length))) }
    var closing by remember { mutableStateOf(false) }
    var raised by remember { mutableStateOf(false) }
    val typing = WindowInsets.isImeVisible

    LaunchedEffect(typing) {
        if (closing) return@LaunchedEffect
        if (typing && sheetState.currentValue != SheetValue.Expanded) {
            raised = true
            sheetState.expand()
        } else if (!typing && raised) {
            raised = false
            if (sheetState.hasPartiallyExpandedState) sheetState.partialExpand()
        }
    }

    // Stable callbacks, so the preset and field lists skip while the user types.
    val onInsert: (String) -> Unit = remember {
        { name ->
            val text = FilenameTemplate.field(name)
            val start = minOf(draft.selection.start, draft.selection.end)
            val end = maxOf(draft.selection.start, draft.selection.end)
            draft = TextFieldValue(draft.text.replaceRange(start, end, text), TextRange(start + text.length))
        }
    }
    val onPick: (String) -> Unit = remember {
        { preset -> draft = TextFieldValue(preset, TextRange(preset.length)) }
    }
    val chosen = Presets.firstOrNull { it.template == draft.text }?.template

    fun save() {
        if (closing || draft.text.isBlank()) return
        closing = true
        val result = FilenameTemplate.normalize(draft.text)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(result) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBlackColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.options_filename_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.options_filename_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = ::save,
                    enabled = draft.text.isNotBlank(),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(stringResource(R.string.options_text_input_save), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text(FilenameTemplate.PRESETS.first(), fontFamily = FontFamily.Monospace) },
                trailingIcon = if (draft.text.isEmpty()) null else {
                    {
                        IconButton(
                            onClick = { draft = TextFieldValue("") },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.options_filename_clear), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel(stringResource(R.string.options_filename_presets))
            PresetList(compact = typing, chosen = chosen, onPick = onPick)
            SectionLabel(stringResource(R.string.options_filename_fields))
            FieldList(compact = typing, onInsert = onInsert)
        }
    }
}

/** Cards when there is room, one scrolling row of pills while the keyboard is up. */
@Composable
private fun PresetList(compact: Boolean, chosen: String?, onPick: (String) -> Unit) {
    if (compact) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Presets.forEach { preset ->
                PresetPill(stringResource(preset.name), preset.template == chosen) { onPick(preset.template) }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.forEach { preset ->
                PresetCard(preset.icon, stringResource(preset.name), preset.template == chosen) { onPick(preset.template) }
            }
        }
    }
}

/** Every field, wrapped when there is room and in one scrolling row while typing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldList(compact: Boolean, onInsert: (String) -> Unit) {
    if (compact) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilenameTemplate.FIELDS.forEach { name -> FieldChip(name) { onInsert(name) } }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilenameTemplate.FIELDS.forEach { name -> FieldChip(name) { onInsert(name) } }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** A preset as a card: the chosen one takes the accent tint and rounder corners. */
@Composable
private fun PresetCard(icon: ImageVector, name: String, selected: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 22.dp else 16.dp, label = "presetCorner")
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A preset while typing, as a pill in one scrolling row. */
@Composable
private fun PresetPill(name: String, selected: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 20.dp else 12.dp, label = "presetPillCorner")
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** A field to insert at the cursor: a small rimmed tile led by an accent plus. */
@Composable
private fun FieldChip(name: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = scheme.onSurface.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+", color = scheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
        }
    }
}
