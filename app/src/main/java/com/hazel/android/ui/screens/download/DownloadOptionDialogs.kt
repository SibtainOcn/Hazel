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
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.parseTimestamp
import com.hazel.android.download.formatTimestamp
import com.hazel.android.download.OneOffOptions
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ContentCut
import com.hazel.android.download.AUDIO_QUALITY_STEPS
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
        title = { Text(stringResource(R.string.options_sponsorblock_title), fontWeight = FontWeight.Bold) },
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
            }) { Text(stringResource(R.string.options_sponsorblock_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_sponsorblock_cancel)) } }
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
        title = { Text(stringResource(R.string.options_chapters_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (isVideo) {
                    ToggleRow(
                        label = stringResource(R.string.options_chapters_in_videos),
                        checked = options.addChapters,
                        onCheckedChange = { onChange(options.copy(addChapters = it)) }
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.options_chapters_split),
                    checked = options.splitByChapters,
                    onCheckedChange = { onChange(options.copy(splitByChapters = it)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.options_chapters_split_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_chapters_dismiss)) } }
    )
}

/**
 * The artwork written into the file: whether there is a cover at all, and whether it is
 * cropped to a square. Cropping only means something with a cover, so it rests greyed out
 * while the cover is off.
 */
@Composable
fun ThumbnailDialog(
    options: DownloadOptions,
    onChange: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Image, null) },
        title = { Text(stringResource(R.string.options_thumbnail_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ToggleRow(
                    label = stringResource(R.string.options_thumbnail_embed),
                    checked = options.embedThumbnail,
                    onCheckedChange = { onChange(options.copy(embedThumbnail = it)) }
                )
                ToggleRow(
                    label = stringResource(R.string.options_thumbnail_crop),
                    checked = options.embedThumbnail && options.cropThumbnail,
                    enabled = options.embedThumbnail,
                    onCheckedChange = { onChange(options.copy(cropThumbnail = it)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.options_thumbnail_crop_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_chapters_dismiss)) } }
    )
}

/**
 * Keeps only part of the media: a range picked on a slider over its length, or typed
 * exactly, with the choice of exact cut points (slower, as the ends are encoded again) or
 * cut points at the nearest keyframe.
 *
 * The whole length picked is no cut at all, so it clears rather than asking yt-dlp to cut
 * out everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutDialog(
    durationSeconds: Int,
    current: OneOffOptions,
    onApply: (start: Double, end: Double, precise: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val duration = durationSeconds.toDouble()
    var startText by remember {
        mutableStateOf(formatTimestamp(if (current.hasSection) current.sectionStart else 0.0))
    }
    var endText by remember {
        mutableStateOf(
            when {
                current.hasSection -> formatTimestamp(current.sectionEnd)
                duration > 0 -> formatTimestamp(duration)
                else -> ""
            }
        )
    }
    var precise by remember { mutableStateOf(current.preciseCuts) }

    val start = parseTimestamp(startText)
    val end = parseTimestamp(endText)
    val valid = start != null && end != null && end > start && (duration <= 0 || end <= duration + 0.5)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ContentCut, null) },
        title = { Text(stringResource(R.string.options_cut_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (duration > 0) {
                    val from = (start ?: 0.0).coerceIn(0.0, duration).toFloat()
                    val to = (end ?: duration).coerceIn(0.0, duration).toFloat()
                    RangeSlider(
                        value = minOf(from, to)..maxOf(from, to),
                        onValueChange = { range ->
                            startText = formatTimestamp(range.start.toDouble())
                            endText = formatTimestamp(range.endInclusive.toDouble())
                        },
                        valueRange = 0f..duration.toFloat()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatTimestamp(0.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatTimestamp(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text(
                        stringResource(R.string.options_cut_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EditableField(
                        label = stringResource(R.string.options_cut_start),
                        value = startText,
                        onValueChange = { startText = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EditableField(
                        label = stringResource(R.string.options_cut_end),
                        value = endText,
                        onValueChange = { endText = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!valid && startText.isNotBlank() && endText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.options_cut_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    label = stringResource(R.string.options_cut_precise),
                    checked = precise,
                    onCheckedChange = { precise = it }
                )
                Text(
                    stringResource(R.string.options_cut_precise_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val from = start ?: return@TextButton
                    val to = end ?: return@TextButton
                    val whole = duration > 0 && from <= 0.05 && to >= duration - 0.05
                    if (whole) onClear() else onApply(from, to, precise)
                }
            ) { Text(stringResource(R.string.options_cut_apply)) }
        },
        dismissButton = {
            Row {
                if (current.hasSection) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.options_cut_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
            }
        }
    )
}

/**
 * How to take a live stream: from its beginning rather than from now, for one that is live;
 * waited for and then downloaded, for one that has not started.
 */
@Composable
fun LiveStreamDialog(
    isUpcoming: Boolean,
    current: OneOffOptions,
    onChange: (OneOffOptions) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.LiveTv, null) },
        title = { Text(stringResource(R.string.options_live_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (isUpcoming) {
                    ToggleRow(
                        label = stringResource(R.string.options_live_wait),
                        checked = current.waitForVideo,
                        onCheckedChange = { onChange(current.copy(waitForVideo = it)) }
                    )
                    Text(
                        stringResource(R.string.options_live_wait_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                } else {
                    ToggleRow(
                        label = stringResource(R.string.options_live_from_start),
                        checked = current.liveFromStart,
                        onCheckedChange = { onChange(current.copy(liveFromStart = it)) }
                    )
                    Text(
                        stringResource(R.string.options_live_from_start_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_chapters_dismiss)) }
        }
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
        title = { Text(stringResource(R.string.options_subtitles_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ToggleRow(
                    label = stringResource(R.string.options_subtitles_embed),
                    checked = options.embedSubs,
                    onCheckedChange = { onChange(options.copy(embedSubs = it)) }
                )
                ToggleRow(
                    label = stringResource(R.string.options_subtitles_save),
                    checked = options.writeSubs,
                    onCheckedChange = { onChange(options.copy(writeSubs = it)) }
                )
                ToggleRow(
                    label = stringResource(R.string.options_subtitles_save_auto),
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
                    Text(stringResource(R.string.options_subtitles_languages_label), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        options.subLanguages.ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_subtitles_dismiss)) } }
    )

    if (languageDialog) {
        TextInputDialog(
            title = stringResource(R.string.options_subtitles_languages_title),
            hint = stringResource(R.string.options_subtitles_languages_hint),
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
        title = stringResource(R.string.options_filename_title),
        hint = stringResource(R.string.options_filename_hint),
        value = template,
        onConfirm = { onConfirm(it.ifBlank { DownloadOptions.DEFAULT_FILENAME_TEMPLATE }) },
        onDismiss = onDismiss
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Single-line editor shared by the template and subtitle-language dialogs. */
@Composable
internal fun TextInputDialog(
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
        confirmButton = { TextButton(onClick = { onConfirm(draft.trim()) }) { Text(stringResource(R.string.options_text_input_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_text_input_cancel)) } }
    )
}

/**
 * Picks audio quality preset / bitrate ceiling.
 */
@Composable
fun AudioQualityDialog(
    options: DownloadOptions,
    onConfirm: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(options.audioQuality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.HighQuality, null) },
        title = { Text(stringResource(R.string.properties_bitrate), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AUDIO_QUALITY_STEPS.forEach { (quality, labelRes) ->
                    val isChecked = selected == quality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = quality }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isChecked,
                            onClick = { selected = quality }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options.copy(audioQuality = selected)) }) {
                Text(stringResource(R.string.options_text_input_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.options_text_input_cancel))
            }
        }
    )
}

