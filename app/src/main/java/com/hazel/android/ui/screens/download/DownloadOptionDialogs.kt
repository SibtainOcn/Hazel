package com.hazel.android.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.SponsorBlock

/**
 * The dialogs behind the option chips at the foot of the download sheet.
 *
 * Each one edits a copy of [DownloadOptions] and hands the whole thing back on confirm, so
 * a dialog can never leave the sheet holding a half-applied change.
 */

/**
 * Picks which SponsorBlock segments to cut out of the download.
 *
 * The ids are yt-dlp's own category names; yt-dlp is what queries the SponsorBlock service,
 * so nothing here has to track the API. Checking nothing turns removal off entirely, which
 * is the default. Segments are still marked as chapters whenever chapters are embedded.
 */
@Composable
fun SponsorBlockDialog(
    options: DownloadOptions,
    onConfirm: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var checked by remember { mutableStateOf(options.sponsorBlockFilters) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Paid, null) },
        title = { Text("Select SponsorBlock filtering", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SponsorBlock.CATEGORIES.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (id in checked) checked - id else checked + id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = id in checked,
                            onCheckedChange = {
                                checked = if (it) checked + id else checked - id
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(options.copy(sponsorBlockFilters = checked))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Whether chapters are embedded into the file, and whether the file is split by them.
 *
 * Embedding is offered for a video download only, since yt-dlp writes the chapter markers
 * into the video container. Splitting applies to both.
 */
@Composable
fun ChaptersDialog(
    options: DownloadOptions,
    isVideo: Boolean,
    onChange: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Book, null) },
        title = { Text("Chapters", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (isVideo) {
                    ToggleRow(
                        label = "Chapters in videos",
                        checked = options.addChapters,
                        onCheckedChange = { onChange(options.copy(addChapters = it)) }
                    )
                }
                ToggleRow(
                    label = "Split by chapters",
                    checked = options.splitByChapters,
                    onCheckedChange = { onChange(options.copy(splitByChapters = it)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Splitting writes one file per chapter instead of a single file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )
}

/**
 * Subtitle handling: embedded into the file, saved alongside it, or both, plus the language
 * selector yt-dlp is given as `--sub-langs`.
 */
@Composable
fun SubtitlesDialog(
    options: DownloadOptions,
    onChange: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var languageDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ClosedCaption, null) },
        title = { Text("Subtitles", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ToggleRow(
                    label = "Embed subtitles",
                    checked = options.embedSubs,
                    onCheckedChange = { onChange(options.copy(embedSubs = it)) }
                )
                ToggleRow(
                    label = "Save subtitles",
                    checked = options.writeSubs,
                    onCheckedChange = { onChange(options.copy(writeSubs = it)) }
                )
                ToggleRow(
                    label = "Save automatic subtitles",
                    checked = options.writeAutoSubs,
                    onCheckedChange = { onChange(options.copy(writeAutoSubs = it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { languageDialog = true }
                        .padding(vertical = 6.dp)
                ) {
                    Text("Subtitle languages", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        options.subLanguages.ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )

    if (languageDialog) {
        TextInputDialog(
            title = "Subtitle languages",
            hint = "A yt-dlp language selector, e.g. en.*,.*-orig for English plus the " +
                    "original track. Use all for every language the source offers.",
            value = options.subLanguages,
            onConfirm = {
                onChange(
                    options.copy(
                        subLanguages = it.ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES }
                    )
                )
                languageDialog = false
            },
            onDismiss = { languageDialog = false }
        )
    }
}

/** The yt-dlp output template used to name the saved file. */
@Composable
fun FilenameTemplateDialog(
    template: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TextInputDialog(
        title = "Filename template",
        hint = "yt-dlp output template. Use %(title)s, %(uploader)s, %(id)s and %(ext)s.",
        value = template,
        onConfirm = { onConfirm(it.ifBlank { DownloadOptions.DEFAULT_FILENAME_TEMPLATE }) },
        onDismiss = onDismiss
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Single-line editor shared by the template and subtitle-language dialogs. */
@Composable
private fun TextInputDialog(
    title: String,
    hint: String,
    value: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
