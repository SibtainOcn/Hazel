package com.hazel.android.ui.screens.more

import android.app.Activity
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.util.AppLocale
import java.text.Normalizer

/** What a search result does besides opening a screen: the two rows of More that open a dialog. */
internal enum class SettingAction { LANGUAGE, GETTING_STARTED }

/**
 * One setting search can find: its name, a line on what it does when it has one, the screen it
 * lives on, and where tapping it goes. Words are read from the app's strings, so a search is in
 * the language the app is shown in.
 */
internal data class SettingEntry(
    @param:StringRes val title: Int,
    @param:StringRes val detail: Int?,
    @param:StringRes val screen: Int,
    val route: String? = null,
    val action: SettingAction? = null
)

/** Every setting search knows, in the order the screens show them. */
internal val SETTINGS_INDEX: List<SettingEntry> = buildList {
    fun on(@StringRes screen: Int, route: String, vararg rows: Pair<Int, Int?>) =
        rows.forEach { (title, detail) -> add(SettingEntry(title, detail, screen, route)) }
    val more = R.string.more_title

    // The screens themselves, as More lists them.
    on(more, "appearance", R.string.more_appearance to null)
    on(more, "processing", R.string.more_processing to R.string.processing_description)
    on(more, "advanced", R.string.more_advanced to R.string.advanced_description)
    on(more, "backup", R.string.more_backup to R.string.backup_description)
    on(more, "storage_locations", R.string.more_downloads to R.string.storage_locations_subtitle)
    add(SettingEntry(R.string.more_language, null, more, action = SettingAction.LANGUAGE))
    on(more, "cookies", R.string.more_cookies to R.string.cookies_description)
    on(more, "fetch_settings", R.string.more_link_reading to R.string.fetch_settings_screen_description)
    on(more, "storage_cleanup", R.string.more_temporary_files to R.string.cleanup_header_description)
    on(more, "converter", R.string.more_converter to null)
    on(more, "software_update", R.string.more_software_update to null)
    on(more, "sponsor", R.string.more_support to null)
    add(SettingEntry(R.string.more_getting_started, null, more, action = SettingAction.GETTING_STARTED))
    on(more, "docs", R.string.more_documentation to null)

    on(
        R.string.appearance_title, "appearance",
        R.string.appearance_dark_theme to null,
        R.string.appearance_accent_color to null
    )
    on(
        R.string.processing_title, "processing",
        R.string.processing_instant_save_as to null,
        R.string.processing_sheet_opens_on to null,
        R.string.processing_use_sponsorblock to R.string.processing_use_sponsorblock_summary,
        R.string.processing_sponsorblock_categories to null,
        R.string.processing_sponsorblock_server to R.string.processing_sponsorblock_server_hint,
        R.string.properties_bitrate to null,
        R.string.options_thumbnail_embed to null,
        R.string.options_thumbnail_crop to null,
        R.string.processing_audio_language to null,
        R.string.processing_audio_codec to null,
        R.string.processing_audio_format to null,
        R.string.options_subtitles_embed to null,
        R.string.options_subtitles_save to null,
        R.string.options_subtitles_save_auto to null,
        R.string.processing_delete_subs to R.string.processing_delete_subs_summary,
        R.string.options_subtitles_languages_title to null,
        R.string.processing_video_format to null,
        R.string.options_chapters_in_videos to null,
        R.string.processing_video_codec to null,
        R.string.processing_video_quality to null
    )
    on(R.string.advanced_title, "proxy", R.string.advanced_proxy to R.string.advanced_proxy_hint)
    on(
        R.string.advanced_title, "advanced",
        R.string.advanced_player_clients to null,
        R.string.advanced_auto_po_tokens to R.string.advanced_auto_po_tokens_summary,
        R.string.advanced_po_token_test to R.string.advanced_po_token_test_hint,
        R.string.advanced_po_tokens to R.string.advanced_po_tokens_hint,
        R.string.advanced_visitor_data to R.string.advanced_visitor_data_hint,
        R.string.advanced_metadata_language to R.string.advanced_metadata_language_summary,
        R.string.advanced_youtube_args to R.string.advanced_youtube_args_hint,
        R.string.advanced_sleep_requests to null,
        R.string.advanced_impersonate to null,
        R.string.advanced_no_check_certificates to R.string.advanced_no_check_certificates_summary,
        R.string.advanced_extra_args to R.string.advanced_extra_args_hint
    )
    on(
        R.string.backup_title, "backup",
        R.string.backup_backup to R.string.backup_choose,
        R.string.backup_restore to null,
        R.string.backup_section_saved to null,
        R.string.backup_auto to R.string.backup_auto_summary,
        R.string.backup_schedule to null,
        R.string.backup_keep to null,
        R.string.backup_include to null,
        R.string.backup_folder to null
    )
    on(
        R.string.storage_locations_title, "storage_locations",
        R.string.storage_locations_wifi_only to R.string.storage_locations_wifi_only_description,
        R.string.storage_locations_speed_limit to R.string.storage_locations_speed_limit_description,
        R.string.storage_locations_fragments to R.string.storage_locations_fragments_description,
        R.string.storage_locations_throttled to R.string.storage_locations_throttled_description
    )
    on(
        R.string.cookies_title, "cookies",
        R.string.cookies_use_title to R.string.cookies_use_description,
        R.string.cookies_add_sign_in to R.string.cookies_add_sign_in_summary,
        R.string.cookies_menu_import_file to null,
        R.string.cookies_menu_export to null,
        R.string.cookies_menu_delete_all to null
    )
    on(
        R.string.fetch_settings_title, "fetch_settings",
        R.string.fetch_settings_read_with to null,
        R.string.fetch_settings_patience to null,
        R.string.fetch_settings_force_ipv4 to R.string.fetch_settings_force_ipv4_description,
        R.string.fetch_settings_search_on to null,
        R.string.fetch_settings_search_with to null,
        R.string.fetch_settings_search_results to null,
        R.string.fetch_settings_suggestions to R.string.fetch_settings_suggestions_summary,
        R.string.fetch_settings_play_with to null,
        R.string.fetch_settings_play_quality to null
    )
    on(R.string.cleanup_title, "storage_cleanup", R.string.cleanup_action_clear_everything to null)
    DocPage.entries.forEach { page ->
        add(SettingEntry(page.labelRes, null, R.string.more_documentation, "docs/${page.name}"))
    }
}

/** A setting with its words read out, ready to be searched. */
internal data class SettingText(val entry: SettingEntry, val title: String, val detail: String, val screen: String)

/** A setting that matched, with where each typed word sits in its name and its line. */
internal data class SettingHit(
    val text: SettingText,
    val titleMarks: List<IntRange>,
    val detailMarks: List<IntRange>
)

internal object SettingsMatcher {

    /**
     * The settings holding every word of [query], best first: a word in the name counts for
     * more than one in the line under it, and the start of a word for more than its middle.
     * Case and accents are ignored, so "cafe" finds "Café".
     */
    fun find(query: String, settings: List<SettingText>): List<SettingHit> {
        val terms = fold(query).split(' ', '\t', '\n').filter { it.isNotBlank() }.distinct()
        if (terms.isEmpty()) return emptyList()
        val phrase = terms.joinToString(" ")

        return settings.mapIndexedNotNull { order, setting ->
            val title = fold(setting.title)
            val detail = fold(setting.detail)
            val screen = fold(setting.screen)
            var score = 0
            for (term in terms) {
                score += when {
                    title.contains(term) -> if (startsWord(title, term)) 12 else 6
                    detail.contains(term) -> if (startsWord(detail, term)) 3 else 1
                    screen.contains(term) -> 2
                    else -> return@mapIndexedNotNull null
                }
            }
            if (terms.size > 1 && title.contains(phrase)) score += 20
            Triple(score, order, SettingHit(setting, marks(title, terms), marks(detail, terms)))
        }
            .sortedWith(compareByDescending<Triple<Int, Int, SettingHit>> { it.first }.thenBy { it.second })
            .map { it.third }
    }

    /**
     * Lower case without accents, one character for each one in [text], so a place found in
     * the folded text is the same place in the text as shown.
     */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text) {
            val bare = if (c.code < 128) c else Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]
            append(bare.lowercaseChar())
        }
    }

    private fun startsWord(text: String, term: String): Boolean {
        var at = text.indexOf(term)
        while (at != -1) {
            if (at == 0 || !text[at - 1].isLetterOrDigit()) return true
            at = text.indexOf(term, at + 1)
        }
        return false
    }

    /** Every place a term appears in [folded], overlapping places joined. */
    private fun marks(folded: String, terms: List<String>): List<IntRange> {
        val found = terms.flatMap { term ->
            generateSequence(folded.indexOf(term).takeIf { it >= 0 }) { from ->
                folded.indexOf(term, from + term.length).takeIf { it >= 0 }
            }.map { it until it + term.length }.toList()
        }.sortedBy { it.first }
        val joined = mutableListOf<IntRange>()
        for (range in found) {
            val last = joined.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                joined[joined.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                joined += range
            }
        }
        return joined
    }
}

/**
 * Searching every setting, from the button at the top of More.
 *
 * A plain field under the back button and a list of matches under a line, with the words
 * typed marked in each. A match opens the screen it is on; Language and Getting started open
 * here, as they do on More. What was typed is kept, so coming back from a setting returns to
 * the same list.
 */
@Composable
fun SettingsSearchScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }

    // Read once for the language the app is in; matching then runs on these, in memory.
    val settings = remember(configuration) {
        SETTINGS_INDEX.map { entry ->
            SettingText(
                entry,
                resources.getString(entry.title),
                entry.detail?.let(resources::getString).orEmpty(),
                resources.getString(entry.screen)
            )
        }
    }
    val hits = remember(query, settings) { SettingsMatcher.find(query, settings) }

    var languageOpen by remember { mutableStateOf(false) }
    var languageChanged by rememberSaveable { mutableStateOf(false) }
    var languageTag by remember { mutableStateOf(AppLocale.tag(context)) }
    var guideOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 14.dp)
                    .focusRequester(focus),
                decorationBox = { field ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    field()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = ""; runCatching { focus.requestFocus() } }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        when {
            query.isBlank() -> SearchNote(stringResource(R.string.settings_search_hint))
            hits.isEmpty() -> SearchNote(stringResource(R.string.settings_search_none, query.trim()))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(hits, key = { "${it.text.entry.screen}/${it.text.entry.title}/${it.text.entry.route}" }) { hit ->
                    SettingResult(hit) {
                        keyboard?.hide()
                        val entry = hit.text.entry
                        when (entry.action) {
                            SettingAction.LANGUAGE -> languageOpen = true
                            SettingAction.GETTING_STARTED -> guideOpen = true
                            null -> entry.route?.let(onOpen)
                        }
                    }
                }
            }
        }
    }

    if (languageOpen) {
        LanguageSheet(
            current = languageTag,
            onConfirm = { tag ->
                languageOpen = false
                if (tag != languageTag) {
                    AppLocale.set(context, tag)
                    languageTag = tag
                    languageChanged = true
                }
            },
            onDismiss = { languageOpen = false }
        )
    }
    if (languageChanged) {
        LanguageChangedDialog(
            onDismiss = {
                languageChanged = false
                // As on More: below Android 13 the new language takes effect on a rebuild.
                if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
            }
        )
    }
    if (guideOpen) {
        com.hazel.android.ui.screens.download.GettingStartedDialog(
            onOpenBatterySettings = onOpenBatterySettings,
            onDismiss = { guideOpen = false }
        )
    }
}

@Composable
private fun SearchNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
    )
}

/** One match: its name, the screen it is on, and the line on what it does when a word is there. */
@Composable
private fun SettingResult(hit: SettingHit, onClick: () -> Unit) {
    // A marker yellow with dark text, the same on every theme and accent: an accent of grey
    // or white marked in its own colour barely showed.
    val mark = SpanStyle(
        color = MARK_TEXT,
        fontWeight = FontWeight.SemiBold,
        background = MARK_BACKGROUND
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            marked(hit.text.title, hit.titleMarks, mark),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            hit.text.screen,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        if (hit.detailMarks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                marked(hit.text.detail, hit.detailMarks, mark),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun marked(text: String, marks: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (range in marks) {
        if (range.first > at) append(text, at, range.first)
        withStyle(style) { append(text, range.first, range.last + 1) }
        at = range.last + 1
    }
    if (at < text.length) append(text, at, text.length)
}

private val MARK_BACKGROUND = androidx.compose.ui.graphics.Color(0xFFFFD84D)
private val MARK_TEXT = androidx.compose.ui.graphics.Color(0xFF1B1B1B)
