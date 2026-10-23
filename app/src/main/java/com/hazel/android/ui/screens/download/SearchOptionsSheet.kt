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
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.hazel.android.ui.components.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
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
import com.hazel.android.R
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.ui.screens.download.batch.batchSheetColor
import com.hazel.android.ui.screens.more.ActionSettingRow
import com.hazel.android.ui.screens.more.ConfirmSettingDialog
import com.hazel.android.ui.screens.more.SuggestionsConsentDialog
import com.hazel.android.ui.screens.more.SettingsSection
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import com.hazel.android.ui.screens.more.SwitchSettingRow
import com.hazel.android.ui.screens.more.engineSummary
import com.hazel.android.ui.screens.more.ValueSettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
    /** Told how a clear went, after the sheet has slid away, so the screen can say so. */
    onCleared: (message: String, ok: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val resultsCleared = stringResource(R.string.search_results_cleared)
    val historyCleared = stringResource(R.string.search_history_cleared)
    val historyFailed = stringResource(R.string.search_history_clear_failed)
    var finishing by remember { mutableStateOf(false) }
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

    var open by remember { mutableStateOf(SearchChoice.NONE) }
    val engineChoices = ListingSource.entries.map { it to stringResource(it.labelRes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = batchSheetColor
    ) {
        val current = prefs ?: return@ModalBottomSheet
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

private data class SearchPrefs(
    val source: SearchSource,
    val searchEngine: ListingSource,
    val results: Int,
    val suggestions: Boolean,
    val playEngine: ListingSource
)

private enum class SearchChoice { NONE, SOURCE, SEARCH_ENGINE, SEARCH_RESULTS, PLAY_ENGINE, SUGGESTIONS, CLEAR_RESULTS, CLEAR_HISTORY }
