package com.hazel.android.ui.screens.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.AUDIO_CONTAINERS
import com.hazel.android.download.AUDIO_QUALITY_STEPS
import com.hazel.android.download.AudioCodec
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.SponsorBlock
import com.hazel.android.download.VIDEO_CONTAINERS
import com.hazel.android.download.VideoCodec
import com.hazel.android.download.languageLabel
import com.hazel.android.ui.screens.download.AudioQualityDialog
import com.hazel.android.ui.screens.download.SponsorBlockDialog
import com.hazel.android.ui.screens.download.TextInputDialog
import com.hazel.android.ui.screens.download.batch.BATCH_QUALITY_STEPS
import kotlinx.coroutines.launch

/**
 * What every download starts from: cover art, formats, codecs, subtitles, chapters and
 * SponsorBlock.
 *
 * These are the same settings the download sheet changes. A change made in the sheet is
 * saved here and a change made here is what the next sheet opens with, so there is one set
 * of settings rather than defaults and overrides that drift apart. The one exception is
 * whether Hazel Instant saves audio or video, which a sheet is told by its tab; the tab a
 * sheet opens on is set here too.
 */
@Composable
fun ProcessingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options by SettingsRepository.getDownloadOptions(context)
        .collectAsState(initial = DownloadOptions())
    val instantAudioOnly by SettingsRepository.getInstantAudioOnly(context)
        .collectAsState(initial = false)

    var dialog by remember { mutableStateOf(ProcessingDialog.NONE) }
    val close = { dialog = ProcessingDialog.NONE }
    fun update(change: (DownloadOptions) -> DownloadOptions) {
        scope.launch { SettingsRepository.updateDownloadOptions(context, change) }
    }

    val default = stringResource(R.string.processing_default)
    val sourceDefault = stringResource(R.string.direct_share_audio_language_default)

    SettingsScreen(
        title = stringResource(R.string.processing_title),
        description = stringResource(R.string.processing_description),
        onBack = onBack
    ) {
        // ── Audio or video ──
        SettingsSection(
            title = stringResource(R.string.processing_section_kind),
            rows = listOf<@Composable () -> Unit>(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.processing_instant_save_as),
                        value = stringResource(
                            if (instantAudioOnly) R.string.processing_instant_audio_only
                            else R.string.format_sheet_tab_video
                        ),
                        onClick = { dialog = ProcessingDialog.INSTANT_SAVE_AS }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Tab,
                        title = stringResource(R.string.processing_sheet_opens_on),
                        value = stringResource(
                            if (options.sheetOpensOnAudio) R.string.format_sheet_tab_audio
                            else R.string.format_sheet_tab_video
                        ),
                        onClick = { dialog = ProcessingDialog.SHEET_OPENS_ON }
                    )
                }
            )
        )

        // ── General ──
        SettingsSection(
            title = stringResource(R.string.processing_section_general),
            rows = listOf<@Composable () -> Unit>(
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Paid,
                        title = stringResource(R.string.processing_use_sponsorblock),
                        summary = stringResource(R.string.processing_use_sponsorblock_summary),
                        checked = options.useSponsorBlock,
                        onCheckedChange = { on -> update { it.copy(useSponsorBlock = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Paid,
                        title = stringResource(R.string.processing_sponsorblock_categories),
                        value = SponsorBlock.CATEGORIES
                            .filter { it.first in options.sponsorBlockFilters }
                            .joinToString(", ") { it.second }
                            .ifBlank { stringResource(R.string.processing_none) },
                        enabled = options.useSponsorBlock,
                        onClick = { dialog = ProcessingDialog.SPONSORBLOCK }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.processing_sponsorblock_server),
                        value = options.sponsorBlockApiUrl.ifBlank { SponsorBlock.API_URL },
                        enabled = options.useSponsorBlock,
                        onClick = { dialog = ProcessingDialog.SPONSORBLOCK_SERVER }
                    )
                }
            )
        )

        // ── Audio ──
        SettingsSection(
            title = stringResource(R.string.format_sheet_tab_audio),
            rows = listOf<@Composable () -> Unit>(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.HighQuality,
                        title = stringResource(R.string.properties_bitrate),
                        value = stringResource(
                            AUDIO_QUALITY_STEPS.firstOrNull { it.first == options.audioQuality }?.second
                                ?: AUDIO_QUALITY_STEPS.first().second
                        ),
                        onClick = { dialog = ProcessingDialog.BITRATE }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Image,
                        title = stringResource(R.string.options_thumbnail_embed),
                        checked = options.embedThumbnail,
                        onCheckedChange = { on -> update { it.copy(embedThumbnail = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Crop,
                        title = stringResource(R.string.options_thumbnail_crop),
                        checked = options.embedThumbnail && options.cropThumbnail,
                        enabled = options.embedThumbnail,
                        onCheckedChange = { on -> update { it.copy(cropThumbnail = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.processing_audio_language),
                        value = options.preferredAudioLanguage.takeIf { it.isNotBlank() }
                            ?.let(::languageLabel) ?: sourceDefault,
                        onClick = { dialog = ProcessingDialog.AUDIO_LANGUAGE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Audiotrack,
                        title = stringResource(R.string.processing_audio_codec),
                        value = options.audioCodecPreference?.label ?: default,
                        onClick = { dialog = ProcessingDialog.AUDIO_CODEC }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Audiotrack,
                        title = stringResource(R.string.processing_audio_format),
                        value = containerLabel(options.audioContainer, default),
                        onClick = { dialog = ProcessingDialog.AUDIO_FORMAT }
                    )
                }
            )
        )

        // ── Video ──
        SettingsSection(
            title = stringResource(R.string.format_sheet_tab_video),
            rows = listOf<@Composable () -> Unit>(
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.ClosedCaption,
                        title = stringResource(R.string.options_subtitles_embed),
                        checked = options.embedSubs,
                        onCheckedChange = { on -> update { it.copy(embedSubs = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Save,
                        title = stringResource(R.string.options_subtitles_save),
                        checked = options.writeSubs,
                        onCheckedChange = { on -> update { it.copy(writeSubs = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Subtitles,
                        title = stringResource(R.string.options_subtitles_save_auto),
                        checked = options.writeAutoSubs,
                        onCheckedChange = { on -> update { it.copy(writeAutoSubs = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.processing_delete_subs),
                        summary = stringResource(R.string.processing_delete_subs_summary),
                        checked = options.deleteSubsAfterEmbed,
                        enabled = options.embedSubs && !options.writeSubs,
                        onCheckedChange = { on -> update { it.copy(deleteSubsAfterEmbed = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.options_subtitles_languages_title),
                        value = subtitleLanguagesLabel(options.subLanguages),
                        enabled = options.embedSubs || options.writeSubs || options.writeAutoSubs,
                        onClick = { dialog = ProcessingDialog.SUBTITLE_LANGUAGES }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Movie,
                        title = stringResource(R.string.processing_video_format),
                        value = containerLabel(options.videoContainer, default),
                        onClick = { dialog = ProcessingDialog.VIDEO_FORMAT }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Book,
                        title = stringResource(R.string.options_chapters_in_videos),
                        checked = options.addChapters,
                        onCheckedChange = { on -> update { it.copy(addChapters = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.VideoSettings,
                        title = stringResource(R.string.processing_video_codec),
                        value = options.videoCodecPreference?.label ?: default,
                        onClick = { dialog = ProcessingDialog.VIDEO_CODEC }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.HighQuality,
                        title = stringResource(R.string.processing_video_quality),
                        value = stringResource(
                            BATCH_QUALITY_STEPS.firstOrNull { it.first == options.videoQuality }?.second
                                ?: BATCH_QUALITY_STEPS.first().second
                        ),
                        onClick = { dialog = ProcessingDialog.VIDEO_QUALITY }
                    )
                }
            )
        )

        FlatSettingButton(
            icon = Icons.Filled.RestartAlt,
            text = stringResource(R.string.processing_reset),
            onClick = { dialog = ProcessingDialog.RESET }
        )
    }

    when (dialog) {
        ProcessingDialog.NONE -> Unit

        ProcessingDialog.INSTANT_SAVE_AS -> SingleChoiceDialog(
            title = stringResource(R.string.processing_instant_save_as),
            choices = listOf(
                false to stringResource(R.string.format_sheet_tab_video),
                true to stringResource(R.string.processing_instant_audio_only)
            ),
            selected = instantAudioOnly,
            onSelect = { audioOnly ->
                scope.launch { SettingsRepository.setInstantAudioOnly(context, audioOnly) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.SHEET_OPENS_ON -> SingleChoiceDialog(
            title = stringResource(R.string.processing_sheet_opens_on),
            choices = listOf(
                false to stringResource(R.string.format_sheet_tab_video),
                true to stringResource(R.string.format_sheet_tab_audio)
            ),
            selected = options.sheetOpensOnAudio,
            onSelect = { audio ->
                update { it.copy(sheetOpensOnAudio = audio) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = { changed ->
                update { it.copy(sponsorBlockFilters = changed.sponsorBlockFilters) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.SPONSORBLOCK_SERVER -> TextInputDialog(
            title = stringResource(R.string.processing_sponsorblock_server),
            hint = stringResource(R.string.processing_sponsorblock_server_hint),
            value = options.sponsorBlockApiUrl,
            onConfirm = { url ->
                // Only an address the engine can reach is kept; anything else goes back to
                // the public server rather than leaving every lookup to fail.
                val clean = url.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
                update { it.copy(sponsorBlockApiUrl = clean) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.BITRATE -> AudioQualityDialog(
            options = options,
            onConfirm = { changed ->
                update { it.copy(audioQuality = changed.audioQuality) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.AUDIO_LANGUAGE -> SingleChoiceDialog(
            title = stringResource(R.string.processing_audio_language),
            choices = listOf("" to sourceDefault) + COMMON_LANGUAGES.map { it to languageLabel(it) },
            selected = options.preferredAudioLanguage,
            onSelect = { code ->
                update { it.copy(preferredAudioLanguage = code) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.AUDIO_CODEC -> SingleChoiceDialog(
            title = stringResource(R.string.processing_audio_codec),
            choices = listOf("" to default) + AudioCodec.entries.map { it.name to it.label },
            selected = options.preferredAudioCodec,
            onSelect = { name ->
                update { it.copy(preferredAudioCodec = name) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.AUDIO_FORMAT -> SingleChoiceDialog(
            title = stringResource(R.string.processing_audio_format),
            choices = containerChoices(AUDIO_CONTAINERS, default),
            selected = options.audioContainer.takeUnless { it.equals("default", true) }.orEmpty(),
            onSelect = { value ->
                update { it.copy(audioContainer = value) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.SUBTITLE_LANGUAGES -> {
            val (known, custom) = remember(options.subLanguages) { splitSubtitleSelector(options.subLanguages) }
            MultiChoiceDialog(
                title = stringResource(R.string.options_subtitles_languages_title),
                choices = subtitleChoices(stringResource(R.string.processing_sub_original)),
                selected = known,
                onConfirm = { ticked ->
                    val selector = buildSubtitleSelector(ticked, custom)
                    update { it.copy(subLanguages = selector) }
                    close()
                },
                onDismiss = close
            )
        }

        ProcessingDialog.VIDEO_FORMAT -> SingleChoiceDialog(
            title = stringResource(R.string.processing_video_format),
            choices = containerChoices(VIDEO_CONTAINERS, default),
            selected = options.videoContainer.takeUnless { it.equals("default", true) }.orEmpty(),
            onSelect = { value ->
                update { it.copy(videoContainer = value) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.VIDEO_CODEC -> SingleChoiceDialog(
            title = stringResource(R.string.processing_video_codec),
            choices = listOf("" to default) + VideoCodec.entries.map { it.name to it.label },
            selected = options.preferredVideoCodec,
            onSelect = { name ->
                update { it.copy(preferredVideoCodec = name) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.VIDEO_QUALITY -> SingleChoiceDialog(
            title = stringResource(R.string.processing_video_quality),
            choices = BATCH_QUALITY_STEPS.map { (height, label) -> height to stringResource(label) },
            selected = options.videoQuality,
            onSelect = { height ->
                update { it.copy(videoQuality = height) }
                close()
            },
            onDismiss = close
        )

        ProcessingDialog.RESET -> ConfirmSettingDialog(
            title = stringResource(R.string.processing_reset_title),
            body = stringResource(R.string.processing_reset_body),
            confirm = stringResource(R.string.format_sheet_reset),
            onConfirm = {
                scope.launch { SettingsRepository.resetDownloadOptions(context) }
                close()
            },
            onDismiss = close
        )
    }
}

private enum class ProcessingDialog {
    NONE, INSTANT_SAVE_AS, SHEET_OPENS_ON, SPONSORBLOCK, SPONSORBLOCK_SERVER, BITRATE,
    AUDIO_LANGUAGE, AUDIO_CODEC, AUDIO_FORMAT,
    SUBTITLE_LANGUAGES, VIDEO_FORMAT, VIDEO_CODEC, VIDEO_QUALITY, RESET
}

/**
 * Languages offered as a preference, as tags. Their names come from the device, in the
 * user's own language, so the list needs no text of its own.
 */
private val COMMON_LANGUAGES = listOf(
    "en", "hi", "es", "pt", "fr", "de", "it", "ru", "ja", "ko", "zh", "ar", "bn", "ta", "te",
    "mr", "ur", "pa", "gu", "kn", "ml", "id", "tr", "vi", "th", "pl", "nl", "uk", "fa"
)

/** A container list's first entry means "leave it alone", which is stored as blank. */
private fun containerChoices(containers: List<String>, default: String): List<Pair<String, String>> =
    containers.map { if (it.equals("default", true)) "" to default else it to it.uppercase() }

private fun containerLabel(value: String, default: String): String =
    value.takeUnless { it.isBlank() || it.equals("default", true) }?.uppercase() ?: default

// ── Subtitle languages ──
//
// yt-dlp takes subtitle languages as a comma separated list of patterns, matched against
// whole track names without regard to case. A language is written as itself and its
// regional forms by name ("en", "en-US", "en-GB"). A wildcard such as "en-.*" cannot be used
// for those: YouTube names a machine translation "<from>-<to>", so "en-de" is English
// subtitles translated into German, and matching ignores case, so nothing but naming the
// regions tells the two apart. The original-language track of automatic captions is
// ".*-orig".

private const val ORIGINAL_TRACK = ".*-orig"

private fun subtitleChoices(originalLabel: String): List<Pair<String, String>> =
    listOf(ORIGINAL_TRACK to originalLabel) + COMMON_LANGUAGES.map { it to languageLabel(it) }

private fun patternsFor(code: String): List<String> =
    if (code == ORIGINAL_TRACK) listOf(code) else listOf(code) + (REGIONS[code] ?: emptyList()).map { "$code-$it" }

/** The regional forms sources use for a language, where it has any in common use. */
private val REGIONS = mapOf(
    "en" to listOf("US", "GB", "IN", "CA", "AU"),
    "es" to listOf("ES", "419", "MX", "US"),
    "pt" to listOf("BR", "PT"),
    "fr" to listOf("FR", "CA"),
    "de" to listOf("DE"),
    "zh" to listOf("Hans", "Hant", "CN", "TW", "HK"),
    "hi" to listOf("IN"),
    "ar" to listOf("SA", "EG")
)

/**
 * Reads a stored selector back into the languages this screen offers, and whatever else it
 * held (typed in the download sheet), which is kept as it is.
 */
private fun splitSubtitleSelector(selector: String): Pair<Set<String>, List<String>> {
    val parts = selector.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val offered = subtitleChoices("").map { it.first }
    val known = offered.filter { code -> code in parts }.toSet()
    val covered = known.flatMap(::patternsFor).toSet()
    val custom = parts.filter { it !in covered && it !in offered }
    return known to custom
}

private fun buildSubtitleSelector(ticked: Set<String>, custom: List<String>): String {
    val offered = subtitleChoices("").map { it.first }
    val patterns = offered.filter { it in ticked }.flatMap(::patternsFor) + custom
    return patterns.distinct().joinToString(",").ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES }
}

@Composable
private fun subtitleLanguagesLabel(selector: String): String {
    val (known, custom) = splitSubtitleSelector(selector)
    val original = stringResource(R.string.processing_sub_original)
    val names = subtitleChoices(original).filter { it.first in known }.map { it.second } + custom
    return names.joinToString(", ")
}
