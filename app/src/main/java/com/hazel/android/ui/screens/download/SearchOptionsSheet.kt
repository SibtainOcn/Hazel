package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.gallery.GalleryShow
import com.hazel.android.ui.components.BetaTag
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.screens.more.ConfirmSettingDialog
import com.hazel.android.ui.screens.more.SettingsSection
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import com.hazel.android.ui.screens.more.SuggestionsConsentDialog
import com.hazel.android.ui.screens.more.SwitchSettingRow
import com.hazel.android.ui.screens.more.ValueSettingRow
import com.hazel.android.ui.screens.more.engineSummary
import com.hazel.android.ui.theme.sheetBlackColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search overflow, opened from the home bar and from the search screen alike, so the two
 * entry points show the same thing. Its rows are the search and playback rows of Fetch
 * settings, written to the same preferences, so a change here shows there and back.
 *
 * Choosing where to search opens [SearchSourceSheet] over this one.
 *
 * Every dialog a row raises opens over the sheet and leaves it in place underneath, so backing
 * out of one lands back on the sheet. Confirming a clear closes the sheet, then [onCleared]
 * reports how it went.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOptionsSheet(
    onClearResults: () -> Unit,
    onDismiss: () -> Unit,
    imagesFirst: Boolean = false,
    /** Told how a clear went, after the sheet has slid away, so the screen can say so. */
    onCleared: (message: String, ok: Boolean) -> Unit = { _, _ -> },
    /** Read by the screen while it is shown, so the sheet opens at once on what is saved. */
    prefs: SearchOptionsPrefs = rememberSearchOptionsPrefs()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // One height for both tabs, never the full screen: the rows scroll inside it, so switching
    // tabs or opening never resizes the sheet.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * SHEET_HEIGHT_SHARE).dp
    val resultsCleared = stringResource(R.string.search_results_cleared)
    val historyCleared = stringResource(R.string.search_history_cleared)
    val historyFailed = stringResource(R.string.search_history_clear_failed)
    var finishing by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    if (guideOpen) SearchGuideScreen(onBack = { guideOpen = false })
    fun finish(message: String, ok: Boolean) {
        scope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
                onCleared(message, ok)
            }
        }
    }

    var imagesTab by remember { mutableStateOf(imagesFirst) }
    val profileAsk = prefs.profileAsk.value
    var profileSheet by remember { mutableStateOf<com.hazel.android.gallery.ProfileSite?>(null) }
    profileSheet?.let { site ->
        com.hazel.android.ui.screens.gallery.SavedProfileSectionsSheet(site = site, onDismiss = { profileSheet = null })
    }

    var open by remember { mutableStateOf(SearchChoice.NONE) }
    val engineChoices = ListingSource.entries.map { it to stringResource(it.labelRes) }
    // Until the first read lands the sheet is not shown: the rows open on what is saved, and
    // the sheet measures its full content rather than measuring empty and jumping.
    val current = prefs.search.value ?: return
    val gallery = prefs.gallery.value ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBlackColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // The two clears sit beside the title, within reach of the thumb as the sheet opens.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.search_options_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ClearShape(
                    icon = Icons.Filled.Info,
                    label = stringResource(R.string.search_options_info),
                    description = stringResource(R.string.search_guide_title),
                    filled = false,
                    onClick = { guideOpen = true }
                )
                ClearShape(
                    icon = Icons.Filled.History,
                    label = stringResource(R.string.search_options_history),
                    description = stringResource(R.string.search_clear_history),
                    filled = false,
                    onClick = { if (!finishing) open = SearchChoice.CLEAR_HISTORY }
                )
                ClearShape(
                    icon = Icons.Filled.DeleteSweep,
                    label = stringResource(R.string.search_options_results),
                    description = stringResource(R.string.search_clear_results),
                    filled = true,
                    onClick = { if (!finishing) open = SearchChoice.CLEAR_RESULTS }
                )
            }

            Row(
                modifier = Modifier.offset(x = -SHEET_TAB_PADDING).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SheetTab(
                    label = stringResource(R.string.search_options_tab_videos),
                    selected = !imagesTab,
                    enabled = true,
                    onClick = { imagesTab = false }
                )
                SheetTab(
                    label = stringResource(R.string.search_source_images),
                    selected = imagesTab,
                    enabled = true,
                    trailing = { BetaTag(small = true) },
                    onClick = { imagesTab = true }
                )
            }

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (imagesTab) {
                SettingsSection(
                    title = stringResource(R.string.search_source_images),
                    rows = listOf<@Composable () -> Unit>(
                        {
                            ValueSettingRow(
                                icon = Icons.Filled.FormatListNumbered,
                                title = stringResource(R.string.gallery_posts_per_search),
                                value = gallery.posts.toString(),
                                onClick = { open = SearchChoice.GALLERY_POSTS }
                            )
                        },
                        {
                            ValueSettingRow(
                                icon = Icons.Filled.PhotoLibrary,
                                title = stringResource(R.string.gallery_show),
                                value = stringResource(showLabel(gallery.show)),
                                onClick = { open = SearchChoice.GALLERY_SHOW }
                            )
                        },
                        {
                            SwitchSettingRow(
                                icon = Icons.Filled.Repeat,
                                title = stringResource(R.string.gallery_skip_reposts),
                                checked = gallery.skipReposts,
                                onCheckedChange = { on -> scope.launch { SettingsRepository.setGallerySkipReposts(context, on) } }
                            )
                        },
                        {
                            SwitchSettingRow(
                                icon = Icons.Filled.ViewCarousel,
                                title = stringResource(R.string.gallery_group_by_author),
                                summary = stringResource(R.string.gallery_group_by_author_summary),
                                checked = gallery.groupByAuthor,
                                onCheckedChange = { on -> scope.launch { SettingsRepository.setGalleryGroupByAuthor(context, on) } }
                            )
                        }
                    ) + profileRows(profileAsk, onOpen = { profileSheet = it })
                )
                Spacer(modifier = Modifier.height(8.dp))
                return@Column
            }

            SettingsSection(
                title = stringResource(R.string.fetch_settings_search),
                rows = listOf(
                    {
                        ValueSettingRow(
                            icon = Icons.Filled.TravelExplore,
                            title = stringResource(R.string.fetch_settings_search_on),
                            value = stringResource(current.source.labelRes),
                            onClick = { open = SearchChoice.SOURCE }
                        )
                    },
                    {
                        ValueSettingRow(
                            icon = Icons.Filled.Hub,
                            title = stringResource(R.string.fetch_settings_search_with),
                            value = stringResource(current.searchEngine.labelRes),
                            onClick = { open = SearchChoice.SEARCH_ENGINE }
                        )
                    },
                    {
                        ValueSettingRow(
                            icon = Icons.Filled.FormatListNumbered,
                            title = stringResource(R.string.fetch_settings_search_results),
                            value = current.results.toString(),
                            onClick = { open = SearchChoice.SEARCH_RESULTS }
                        )
                    },
                    {
                        SwitchSettingRow(
                            icon = Icons.Filled.Lightbulb,
                            title = stringResource(R.string.fetch_settings_suggestions),
                            checked = current.suggestions,
                            onCheckedChange = { on ->
                                if (on) open = SearchChoice.SUGGESTIONS
                                else scope.launch { SettingsRepository.setSearchSuggestions(context, false) }
                            }
                        )
                    },
                    {
                        SwitchSettingRow(
                            icon = Icons.Filled.Headphones,
                            title = stringResource(R.string.fetch_settings_play_background),
                            summary = stringResource(R.string.fetch_settings_play_background_summary),
                            checked = current.playInBackground,
                            onCheckedChange = { on -> com.hazel.android.ui.screens.more.setPlayInBackground(context, scope, on) }
                        )
                    },
                    {
                        ValueSettingRow(
                            icon = Icons.Filled.PlayCircle,
                            title = stringResource(R.string.fetch_settings_play_with),
                            value = stringResource(current.playEngine.labelRes),
                            onClick = { open = SearchChoice.PLAY_ENGINE }
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    val close = { open = SearchChoice.NONE }
    when (open) {
        SearchChoice.NONE -> Unit
        SearchChoice.SOURCE -> SearchSourceSheet(selected = current.source, onDismiss = close)
        SearchChoice.SEARCH_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_with),
            choices = engineChoices,
            selected = current.searchEngine,
            onSelect = { scope.launch { SettingsRepository.setSearchEngine(context, it) }; close() },
            onDismiss = close,
            describe = { engineSummary(it) }
        )
        SearchChoice.SEARCH_RESULTS -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_results),
            choices = SettingsRepository.SEARCH_RESULT_COUNTS.map { it to it.toString() },
            selected = current.results,
            onSelect = { scope.launch { SettingsRepository.setSearchResults(context, it) }; close() },
            onDismiss = close
        )
        SearchChoice.PLAY_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_play_with),
            choices = engineChoices,
            selected = current.playEngine,
            onSelect = { scope.launch { SettingsRepository.setPlayEngine(context, it) }; close() },
            onDismiss = close,
            describe = { engineSummary(it) }
        )
        SearchChoice.GALLERY_POSTS -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_posts_per_search),
            choices = SettingsRepository.GALLERY_POST_COUNTS.map { it to it.toString() },
            selected = gallery.posts,
            onSelect = { scope.launch { SettingsRepository.setGalleryPostsPerSearch(context, it) }; close() },
            onDismiss = close
        )
        SearchChoice.GALLERY_SHOW -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_show),
            choices = GalleryShow.entries.map { it to stringResource(showLabel(it)) },
            selected = gallery.show,
            onSelect = { scope.launch { SettingsRepository.setGalleryShow(context, it) }; close() },
            onDismiss = close
        )
        SearchChoice.SUGGESTIONS -> SuggestionsConsentDialog(
            onConfirm = { scope.launch { SettingsRepository.setSearchSuggestions(context, true) }; close() },
            onDismiss = close
        )
        SearchChoice.CLEAR_RESULTS -> ConfirmSettingDialog(
            title = stringResource(R.string.download_clear_confirm_title),
            body = stringResource(R.string.download_clear_confirm_body),
            confirm = stringResource(R.string.download_clear),
            onConfirm = {
                close()
                if (!finishing) {
                    finishing = true
                    onClearResults()
                    finish(resultsCleared, ok = true)
                }
            },
            onDismiss = close
        )
        // The clear cannot be cut short by the sheet closing; the toast follows once it is done.
        SearchChoice.CLEAR_HISTORY -> ConfirmSettingDialog(
            title = stringResource(R.string.search_clear_history_confirm_title),
            body = stringResource(R.string.search_clear_history_confirm_body),
            confirm = stringResource(R.string.search_clear_history),
            onConfirm = {
                close()
                if (!finishing) {
                    finishing = true
                    val appContext = context.applicationContext
                    scope.launch {
                        val ok = withContext(NonCancellable + Dispatchers.IO) {
                            runCatching { SearchHistoryRepository.clear(appContext) }.isSuccess
                        }
                        finish(if (ok) historyCleared else historyFailed, ok)
                    }
                }
            },
            onDismiss = close
        )
    }
}

internal data class SearchPrefs(
    val source: SearchSource,
    val searchEngine: ListingSource,
    val results: Int,
    val suggestions: Boolean,
    val playEngine: ListingSource,
    val playInBackground: Boolean
)

internal data class GalleryPrefs(
    val posts: Int,
    val show: GalleryShow,
    val skipReposts: Boolean,
    val groupByAuthor: Boolean
)

private fun showLabel(show: GalleryShow): Int = when (show) {
    GalleryShow.ALL -> R.string.gallery_show_all
    GalleryShow.IMAGES -> R.string.gallery_show_images
    GalleryShow.VIDEOS -> R.string.gallery_show_videos
}

private enum class SearchChoice {
    NONE, SOURCE, SEARCH_ENGINE, SEARCH_RESULTS, PLAY_ENGINE, SUGGESTIONS, CLEAR_RESULTS, CLEAR_HISTORY,
    GALLERY_POSTS, GALLERY_SHOW
}

/** A clear action on a six-sided cookie, named in a small line under it. */
@Composable
private fun ClearShape(
    icon: ImageVector,
    label: String,
    description: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ShapeIconButton(onClick = onClick, filled = filled, size = 52.dp) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp)
        )
    }
}

/** The profile-link rows, shared by the three dots menu and gallery-dl settings: one setting, two doors. */
@Composable
internal fun profileRows(ask: Boolean, onOpen: (com.hazel.android.gallery.ProfileSite) -> Unit): List<@Composable () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += {
        SwitchSettingRow(
            icon = Icons.Filled.PersonSearch,
            title = stringResource(R.string.profile_ask),
            summary = stringResource(R.string.profile_ask_hint),
            checked = ask,
            onCheckedChange = { on -> scope.launch { SettingsRepository.setGalleryProfileAsk(context, on) } }
        )
    }
    com.hazel.android.gallery.ProfileSite.entries.forEach { site ->
        rows += {
            val sections by remember(site) { SettingsRepository.getGalleryProfileSections(context, site) }
                .collectAsState(initial = com.hazel.android.gallery.ProfileSection.defaults(site))
            ValueSettingRow(
                icon = Icons.Filled.AccountCircle,
                title = stringResource(R.string.profile_sections_title, site.label),
                value = sections.map { stringResource(it.label) }.joinToString(", "),
                onClick = { onOpen(site) }
            )
        }
    }
    return rows
}

/** The search options sheet's fixed height, as a share of the screen. */
private const val SHEET_HEIGHT_SHARE = 0.72f

/**
 * What the options sheet shows, read live from the saved settings. Only read here, never
 * written: each row still writes its own setting. Kept by the screen that opens the sheet,
 * so the values are already in hand when it opens and no frame is spent waiting for them.
 */
@androidx.compose.runtime.Stable
class SearchOptionsPrefs internal constructor(
    internal val search: androidx.compose.runtime.State<SearchPrefs?>,
    internal val gallery: androidx.compose.runtime.State<GalleryPrefs?>,
    internal val profileAsk: androidx.compose.runtime.State<Boolean>
)

@Composable
fun rememberSearchOptionsPrefs(): SearchOptionsPrefs {
    val context = LocalContext.current
    val search = remember(context) {
        combine(
            SettingsRepository.getSearchSource(context),
            SettingsRepository.getSearchEngine(context),
            SettingsRepository.getSearchResults(context),
            SettingsRepository.getSearchSuggestions(context),
            combine(SettingsRepository.getPlayEngine(context), SettingsRepository.getPlayInBackground(context), ::Pair)
        ) { source, engine, results, suggestions, play ->
            SearchPrefs(source, engine, results, suggestions, play.first, play.second)
        }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val gallery = remember(context) {
        combine(
            SettingsRepository.getGalleryPostsPerSearch(context),
            SettingsRepository.getGalleryShow(context),
            SettingsRepository.getGallerySkipReposts(context),
            SettingsRepository.getGalleryGroupByAuthor(context),
            ::GalleryPrefs
        ).distinctUntilChanged()
    }.collectAsState(initial = null)
    val profileAsk = remember(context) { SettingsRepository.getGalleryProfileAsk(context) }.collectAsState(initial = true)
    return remember(search, gallery, profileAsk) { SearchOptionsPrefs(search, gallery, profileAsk) }
}
