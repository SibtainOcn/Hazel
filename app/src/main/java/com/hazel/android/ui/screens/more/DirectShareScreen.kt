package com.hazel.android.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.AUDIO_CONTAINERS
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.VIDEO_CONTAINERS
import com.hazel.android.download.languageLabel
import com.hazel.android.ui.screens.download.AudioLanguageSheet
import com.hazel.android.ui.screens.download.ChaptersDialog
import com.hazel.android.ui.screens.download.FilenameTemplateDialog
import com.hazel.android.ui.screens.download.SponsorBlockDialog
import com.hazel.android.ui.screens.download.SubtitlesDialog
import kotlinx.coroutines.launch

/**
 * What the direct share target does, since it does it without asking.
 *
 * The ordinary share target opens the sheet and every choice is made there. The direct one
 * makes none, so the choices have to exist somewhere, and this is that somewhere. Every
 * answer on this screen belongs to this target alone: the sheet keeps its own, so turning
 * off cover art for one download being watched cannot quietly change what the next
 * unattended share writes.
 *
 * Nothing here is a demand on the source. A ceiling no format clears, subtitles a video
 * does not have, chapters nobody wrote: each is dropped for that download and the rest goes
 * ahead, because a share that asks no questions cannot come back with one.
 */
@Composable
fun DirectShareScreen(onBack: () -> Unit, onOpenCookies: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVideo by SettingsRepository.getQuickIsVideo(context).collectAsState(initial = true)
    val maxHeight by SettingsRepository.getQuickMaxHeight(context).collectAsState(initial = 0)

    val options by SettingsRepository.getInstantOptions(context)
        .collectAsState(initial = DownloadOptions())
    val useCookies by CookieRepository.getUseCookies(context).collectAsState(initial = false)
    val audioLanguage by SettingsRepository.getInstantAudioLanguage(context)
        .collectAsState(initial = "")

    var languageSheetVisible by remember { mutableStateOf(false) }

    fun update(changed: DownloadOptions) {
        scope.launch { SettingsRepository.setInstantOptions(context, changed) }
    }

    var openDialog by remember { mutableStateOf(InstantDialog.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.direct_share_back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.direct_share_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            stringResource(R.string.direct_share_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        SettingGroup(title = stringResource(R.string.direct_share_save_as)) {
            ChoiceRow(
                label = stringResource(R.string.direct_share_video),
                description = stringResource(R.string.direct_share_video_description),
                selected = isVideo,
                onSelect = { scope.launch { SettingsRepository.setQuickIsVideo(context, true) } }
            )
            ChoiceRow(
                label = stringResource(R.string.direct_share_audio_only),
                description = stringResource(R.string.direct_share_audio_description),
                selected = !isVideo,
                onSelect = { scope.launch { SettingsRepository.setQuickIsVideo(context, false) } }
            )
        }

        if (isVideo) {
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle(
                title = stringResource(R.string.direct_share_quality_title),
                description = stringResource(R.string.direct_share_quality_description)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                HEIGHT_CHOICES.forEach { (height, label) ->
                    ChoiceRow(
                        label = label,
                        description = null,
                        selected = maxHeight == height,
                        onSelect = {
                            scope.launch { SettingsRepository.setQuickMaxHeight(context, height) }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Output ──

        SectionTitle(
            title = stringResource(R.string.direct_share_output_title),
            description = stringResource(R.string.direct_share_output_description)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ContainerRow(
                    label = if (isVideo) "Video container" else "Audio container",
                    value = (if (isVideo) options.videoContainer else options.audioContainer)
                        .ifBlank { "Default" },
                    choices = if (isVideo) VIDEO_CONTAINERS else AUDIO_CONTAINERS,
                    onSelect = { choice ->
                        val stored = if (choice == "Default") "" else choice
                        update(
                            if (isVideo) options.copy(videoContainer = stored)
                            else options.copy(audioContainer = stored)
                        )
                    }
                )
                ActionRow(
                    icon = Icons.Filled.Edit,
                    label = stringResource(R.string.direct_share_filename_template),
                    value = options.filenameTemplate,
                    onClick = { openDialog = InstantDialog.FILENAME }
                )
                ActionRow(
                    icon = Icons.Filled.Translate,
                    label = stringResource(R.string.direct_share_audio_language),
                    value = audioLanguage.takeIf { it.isNotBlank() }?.let(::languageLabel)
                        ?: stringResource(R.string.direct_share_audio_language_default),
                    onClick = { languageSheetVisible = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Extras ──

        SectionTitle(
            title = stringResource(R.string.direct_share_extras_title),
            description = stringResource(R.string.direct_share_extras_description)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SwitchRow(
                    icon = Icons.Filled.Image,
                    label = stringResource(R.string.direct_share_cover_art),
                    description = stringResource(R.string.direct_share_cover_art_description),
                    checked = options.embedThumbnail,
                    onCheckedChange = { update(options.copy(embedThumbnail = it)) }
                )
                ActionRow(
                    icon = Icons.Filled.Book,
                    label = stringResource(R.string.direct_share_chapters),
                    value = chapterSummary(options, isVideo),
                    onClick = { openDialog = InstantDialog.CHAPTERS }
                )
                if (isVideo) {
                    ActionRow(
                        icon = Icons.Filled.ClosedCaption,
                        label = stringResource(R.string.direct_share_subtitles),
                        value = subtitleSummary(options),
                        onClick = { openDialog = InstantDialog.SUBTITLES }
                    )
                }
                ActionRow(
                    icon = Icons.Filled.Paid,
                    label = stringResource(R.string.direct_share_sponsorblock),
                    value = if (options.sponsorBlockFilters.isEmpty()) {
                        stringResource(R.string.direct_share_sponsorblock_off)
                    } else {
                        pluralStringResource(
                            R.plurals.direct_share_sponsorblock_cut,
                            options.sponsorBlockFilters.size,
                            options.sponsorBlockFilters.size
                        )
                    },
                    onClick = { openDialog = InstantDialog.SPONSORBLOCK }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Sign-ins ──

        SectionTitle(
            title = stringResource(R.string.direct_share_signins_title),
            description = stringResource(R.string.direct_share_signins_description)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            ActionRow(
                icon = Icons.Filled.Cookie,
                label = stringResource(R.string.direct_share_cookies),
                value = stringResource(
                    if (useCookies) R.string.direct_share_cookies_on
                    else R.string.direct_share_cookies_off
                ),
                onClick = onOpenCookies
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (languageSheetVisible) {
        AudioLanguageSheet(
            // Nothing is known about the link until it is shared, so the list is the
            // languages media commonly carries rather than one source's own.
            languages = COMMON_AUDIO_LANGUAGES,
            selected = audioLanguage.takeIf { it.isNotBlank() },
            onPick = { code ->
                scope.launch {
                    SettingsRepository.setInstantAudioLanguage(context, code.orEmpty())
                }
                languageSheetVisible = false
            },
            onDismiss = { languageSheetVisible = false }
        )
    }

    when (openDialog) {
        InstantDialog.NONE -> Unit

        InstantDialog.CHAPTERS -> ChaptersDialog(
            options = options,
            isVideo = isVideo,
            onChange = ::update,
            onDismiss = { openDialog = InstantDialog.NONE }
        )

        InstantDialog.SUBTITLES -> SubtitlesDialog(
            options = options,
            onChange = ::update,
            onDismiss = { openDialog = InstantDialog.NONE }
        )

        InstantDialog.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = {
                update(it)
                openDialog = InstantDialog.NONE
            },
            onDismiss = { openDialog = InstantDialog.NONE }
        )

        InstantDialog.FILENAME -> FilenameTemplateDialog(
            template = options.filenameTemplate,
            onConfirm = {
                update(options.copy(filenameTemplate = it))
                openDialog = InstantDialog.NONE
            },
            onDismiss = { openDialog = InstantDialog.NONE }
        )
    }
}

/** Which of the screen's dialogs is open. Only one can be at a time. */
private enum class InstantDialog { NONE, CHAPTERS, SUBTITLES, SPONSORBLOCK, FILENAME }

private fun chapterSummary(options: DownloadOptions, isVideo: Boolean): String {
    val parts = buildList {
        if (isVideo && options.addChapters) add("Embedded")
        if (options.splitByChapters) add("Split into files")
    }
    return parts.joinToString(", ").ifBlank { "Off" }
}

private fun subtitleSummary(options: DownloadOptions): String {
    val parts = buildList {
        if (options.embedSubs) add("Embedded")
        if (options.writeSubs) add("Saved beside")
        if (options.writeAutoSubs) add("Automatic")
    }
    return parts.joinToString(", ").ifBlank { "Off" }
}

@Composable
private fun SectionTitle(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
    )
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

/** Row that opens one of the option dialogs, showing what it is currently set to. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Row whose value is picked from a fixed list, used for the container. */
@Composable
private fun ContainerRow(
    label: String,
    value: String,
    choices: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.direct_share_change, label),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    },
                    trailingIcon = if (choice == value) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

/**
 * Soundtracks worth offering for a link nobody has read yet.
 *
 * A source names its own languages, and this screen never sees one, so the list is of the
 * languages media is commonly published in. Anything picked here is a preference: a source
 * that does not carry it is downloaded with what it does.
 */
private val COMMON_AUDIO_LANGUAGES = listOf(
    "en", "hi", "es", "pt", "fr", "de", "it", "ru",
    "ja", "ko", "zh", "ar", "id", "tr", "bn", "ta", "te", "vi", "th", "pl", "nl", "uk"
)

/** Height ceilings offered, coarsest first. 0 means whatever the source calls best. */
private val HEIGHT_CHOICES = listOf(
    0 to "Best available",
    2160 to "Up to 4K",
    1440 to "Up to 1440p",
    1080 to "Up to 1080p",
    720 to "Up to 720p",
    480 to "Up to 480p",
    360 to "Up to 360p"
)
