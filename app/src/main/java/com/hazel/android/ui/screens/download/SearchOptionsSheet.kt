package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import com.hazel.android.ui.screens.more.WebSearchDialog
import com.hazel.android.ui.screens.more.webSearchLabel
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.ui.screens.download.batch.batchSheetColor
import com.hazel.android.ui.screens.more.ActionSettingRow
import com.hazel.android.ui.screens.more.ConfirmSettingDialog
import com.hazel.android.ui.screens.more.SettingsSection
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import com.hazel.android.ui.screens.more.SwitchSettingRow
import com.hazel.android.ui.screens.more.ValueSettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The search overflow, opened from the home bar and from the search screen alike, so the two
 * entry points show the same thing. Its rows are the search and playback rows of Fetch
 * settings, written to the same preferences, so a change here shows there and back.
 *
 * Choosing where to search opens [SearchSourceSheet] over this one.
 *
 * Every dialog a row raises, the two clear confirmations included, opens over the sheet and
 * leaves it in place underneath, so backing out of one lands back on the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOptionsSheet(
    onClearResults: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Read as one, and nothing is drawn until it has been: the rows then open on what is
    // saved, never on a default that flips to the saved value a frame later.
    val prefs by remember(context) {
        combine(
            SettingsRepository.getSearchSource(context),
            SettingsRepository.getSearchEngine(context),
            SettingsRepository.getSearchResults(context),
            SettingsRepository.getSearchSuggestions(context),
            SettingsRepository.getPlayEngine(context),
            ::SearchPrefs
        )
    }.collectAsState(initial = null)
    val webServer by remember(context) { SettingsRepository.getWebSearchServer(context) }
        .collectAsState(initial = null)

    var open by remember { mutableStateOf(SearchChoice.NONE) }
    val engineChoices = ListingSource.entries.map { it to stringResource(it.labelRes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = batchSheetColor
    ) {
        val current = prefs ?: return@ModalBottomSheet
        val server = webServer ?: return@ModalBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.search_options_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

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
                            icon = Icons.Filled.Public,
                            title = stringResource(R.string.fetch_settings_web_search),
                            value = webSearchLabel(server),
                            onClick = { open = SearchChoice.WEB_SEARCH }
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
                            onCheckedChange = { scope.launch { SettingsRepository.setSearchSuggestions(context, it) } }
                        )
                    }
                )
            )

            SettingsSection(
                title = stringResource(R.string.fetch_settings_playback),
                rows = listOf(
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

            SettingsSection(
                title = stringResource(R.string.backup_category_search),
                rows = listOf(
                    {
                        ActionSettingRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = stringResource(R.string.search_clear_results),
                            onClick = { open = SearchChoice.CLEAR_RESULTS }
                        )
                    },
                    {
                        ActionSettingRow(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.search_clear_history),
                            onClick = { open = SearchChoice.CLEAR_HISTORY }
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    val current = prefs ?: return
    val close = { open = SearchChoice.NONE }
    when (open) {
        SearchChoice.NONE -> Unit
        SearchChoice.SOURCE -> SearchSourceSheet(selected = current.source, onDismiss = close)
        SearchChoice.WEB_SEARCH -> WebSearchDialog(
            server = webServer.orEmpty(),
            onSave = { scope.launch { SettingsRepository.setWebSearchServer(context, it) }; close() },
            onDismiss = close
        )
        SearchChoice.SEARCH_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_with),
            choices = engineChoices,
            selected = current.searchEngine,
            onSelect = { scope.launch { SettingsRepository.setSearchEngine(context, it) }; close() },
            onDismiss = close
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
            onDismiss = close
        )
        SearchChoice.CLEAR_RESULTS -> ConfirmSettingDialog(
            title = stringResource(R.string.download_clear_confirm_title),
            body = stringResource(R.string.download_clear_confirm_body),
            confirm = stringResource(R.string.download_clear),
            onConfirm = { close(); onClearResults() },
            onDismiss = close
        )
        // Cleared on the app's scope, so closing the sheet straight after cannot cut it short.
        SearchChoice.CLEAR_HISTORY -> ConfirmSettingDialog(
            title = stringResource(R.string.search_clear_history_confirm_title),
            body = stringResource(R.string.search_clear_history_confirm_body),
            confirm = stringResource(R.string.search_clear_history),
            onConfirm = {
                close()
                HazelApp.instance.applicationScope.launch(Dispatchers.IO) {
                    SearchHistoryRepository.clear(context.applicationContext)
                }
            },
            onDismiss = close
        )
    }
}

private data class SearchPrefs(
    val source: SearchSource,
    val searchEngine: ListingSource,
    val results: Int,
    val suggestions: Boolean,
    val playEngine: ListingSource
)

private enum class SearchChoice { NONE, SOURCE, WEB_SEARCH, SEARCH_ENGINE, SEARCH_RESULTS, PLAY_ENGINE, CLEAR_RESULTS, CLEAR_HISTORY }
