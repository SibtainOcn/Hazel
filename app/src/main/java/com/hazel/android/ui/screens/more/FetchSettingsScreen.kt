package com.hazel.android.ui.screens.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.FetchMode
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.SearchSource
import kotlinx.coroutines.launch

/**
 * How links are read, how searches run, and how media plays.
 *
 * Each engine choice is between NewPipe, which answers fastest on the sites it knows, and
 * yt-dlp, which works everywhere. Whichever is picked, yt-dlp takes over whenever NewPipe
 * cannot answer, and downloads always use yt-dlp.
 */
@Composable
fun FetchSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mode by remember(context) { SettingsRepository.getFetchMode(context) }.collectAsState(initial = FetchMode.DEFAULT)
    val listingSource by remember(context) { SettingsRepository.getListingSource(context) }.collectAsState(initial = ListingSource.DEFAULT)
    val forceIpv4 by remember(context) { SettingsRepository.getForceIpv4(context) }.collectAsState(initial = false)
    val searchSource by remember(context) { SettingsRepository.getSearchSource(context) }.collectAsState(initial = SearchSource.DEFAULT)
    val searchEngine by remember(context) { SettingsRepository.getSearchEngine(context) }.collectAsState(initial = ListingSource.NEWPIPE)
    val searchResults by remember(context) { SettingsRepository.getSearchResults(context) }
        .collectAsState(initial = SettingsRepository.DEFAULT_SEARCH_RESULTS)
    val suggestions by remember(context) { SettingsRepository.getSearchSuggestions(context) }.collectAsState(initial = false)
    val playEngine by remember(context) { SettingsRepository.getPlayEngine(context) }.collectAsState(initial = ListingSource.NEWPIPE)
    val playQuality by remember(context) { SettingsRepository.getPlayQuality(context) }
        .collectAsState(initial = SettingsRepository.DEFAULT_PLAY_QUALITY)

    var open by remember { mutableStateOf(Choice.NONE) }

    val engineChoices = ListingSource.entries.map { it to stringResource(it.labelRes) }

    SettingsScreen(
        title = stringResource(R.string.fetch_settings_title),
        onBack = onBack,
        description = stringResource(R.string.fetch_settings_screen_description)
    ) {
        SettingsSection(
            title = stringResource(R.string.fetch_settings_reading_links),
            rows = listOf(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.fetch_settings_read_with),
                        value = stringResource(listingSource.labelRes),
                        onClick = { open = Choice.READ_ENGINE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Timer,
                        title = stringResource(R.string.fetch_settings_patience),
                        value = stringResource(mode.labelRes) + " · " + pluralStringResource(
                            R.plurals.fetch_settings_timing, mode.retries, mode.socketTimeoutSeconds, mode.retries
                        ),
                        onClick = { open = Choice.PATIENCE }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Lan,
                        title = stringResource(R.string.fetch_settings_force_ipv4),
                        summary = stringResource(R.string.fetch_settings_force_ipv4_description),
                        checked = forceIpv4,
                        onCheckedChange = { scope.launch { SettingsRepository.setForceIpv4(context, it) } }
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.fetch_settings_search),
            rows = listOf(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.TravelExplore,
                        title = stringResource(R.string.fetch_settings_search_on),
                        value = stringResource(searchSource.labelRes),
                        onClick = { open = Choice.SEARCH_SOURCE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Hub,
                        title = stringResource(R.string.fetch_settings_search_with),
                        value = stringResource(searchEngine.labelRes),
                        onClick = { open = Choice.SEARCH_ENGINE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.FormatListNumbered,
                        title = stringResource(R.string.fetch_settings_search_results),
                        value = searchResults.toString(),
                        onClick = { open = Choice.SEARCH_RESULTS }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Lightbulb,
                        title = stringResource(R.string.fetch_settings_suggestions),
                        summary = stringResource(R.string.fetch_settings_suggestions_summary),
                        checked = suggestions,
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
                        value = stringResource(playEngine.labelRes),
                        onClick = { open = Choice.PLAY_ENGINE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.HighQuality,
                        title = stringResource(R.string.fetch_settings_play_quality),
                        value = stringResource(R.string.fetch_settings_play_quality_value, playQuality),
                        onClick = { open = Choice.PLAY_QUALITY }
                    )
                }
            )
        )
    }

    val close = { open = Choice.NONE }
    when (open) {
        Choice.NONE -> Unit
        Choice.READ_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_read_with),
            choices = engineChoices,
            selected = listingSource,
            onSelect = { scope.launch { SettingsRepository.setListingSource(context, it) }; close() },
            onDismiss = close,
            describe = { stringResource(it.descriptionRes) }
        )
        Choice.PATIENCE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_patience),
            choices = FetchMode.entries.map { it to stringResource(it.labelRes) },
            selected = mode,
            onSelect = { scope.launch { SettingsRepository.setFetchMode(context, it) }; close() },
            onDismiss = close,
            describe = { stringResource(it.descriptionRes) }
        )
        Choice.SEARCH_SOURCE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_on),
            choices = SearchSource.entries.map { it to stringResource(it.labelRes) },
            selected = searchSource,
            onSelect = { scope.launch { SettingsRepository.setSearchSource(context, it) }; close() },
            onDismiss = close
        )
        Choice.SEARCH_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_with),
            choices = engineChoices,
            selected = searchEngine,
            onSelect = { scope.launch { SettingsRepository.setSearchEngine(context, it) }; close() },
            onDismiss = close,
            describe = { engineSummary(it) }
        )
        Choice.SEARCH_RESULTS -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_results),
            choices = SettingsRepository.SEARCH_RESULT_COUNTS.map { it to it.toString() },
            selected = searchResults,
            onSelect = { scope.launch { SettingsRepository.setSearchResults(context, it) }; close() },
            onDismiss = close
        )
        Choice.PLAY_ENGINE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_play_with),
            choices = engineChoices,
            selected = playEngine,
            onSelect = { scope.launch { SettingsRepository.setPlayEngine(context, it) }; close() },
            onDismiss = close,
            describe = { engineSummary(it) }
        )
        Choice.PLAY_QUALITY -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_play_quality),
            choices = SettingsRepository.PLAY_QUALITIES.map {
                it to stringResource(R.string.fetch_settings_play_quality_value, it)
            },
            selected = playQuality,
            onSelect = { scope.launch { SettingsRepository.setPlayQuality(context, it) }; close() },
            onDismiss = close
        )
    }
}

@Composable
private fun engineSummary(engine: ListingSource): String = stringResource(
    if (engine == ListingSource.NEWPIPE) R.string.fetch_settings_engine_newpipe_summary
    else R.string.fetch_settings_engine_ytdlp_summary
)

private enum class Choice {
    NONE, READ_ENGINE, PATIENCE, SEARCH_SOURCE, SEARCH_ENGINE, SEARCH_RESULTS, PLAY_ENGINE, PLAY_QUALITY
}
