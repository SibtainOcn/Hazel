package com.hazel.android.ui.screens.download

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.FormatFilter
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.FormatListShimmer
import com.hazel.android.ui.components.GlintHost
import com.hazel.android.ui.components.ShimmerLabel
import com.hazel.android.download.qualityRung
import com.hazel.android.ui.theme.SizeBadgeContainer
import com.hazel.android.ui.theme.SizeBadgeContent

/**
 * How the format list is ordered. The default is what the probe already sorted for.
 *
 * The name is held as a resource id rather than as text, the same way the fetch mode and
 * the listing source hold theirs: an enum has no Context, so a label written here is a
 * label nothing can translate.
 */
enum class FormatSort(@param:StringRes val labelRes: Int) {
    QUALITY(R.string.format_sort_quality),
    FILE_SIZE(R.string.format_sort_file_size),
    CONTAINER(R.string.format_sort_container)
}

/**
 * The full format list, opened from the quality row in the download sheet.
 *
 * Laid out in two panes. A rail down the left holds one stop per resolution the source
 * reported, with "All" above them and the audio streams below, and the list on the right
 * scrolls on its own and shows only what the stop holds. A ladder of forty streams is read
 * one rung at a time that way, rather than by scrolling past every 4K entry to reach 1080p.
 * Where there is nothing to split (a source with one resolution, or the audio choices a set
 * of links shares) the rail is left out and the list takes the whole width.
 *
 * It opens half way up the screen and can be dragged to full height, on the stop holding the
 * current choice, scrolled to it.
 *
 * [audioFirst] carries which tab the download sheet is on. The list holds both kinds
 * either way, since picking an audio stream for a video download is allowed, but the one
 * being chosen leads.
 *
 * [isLoadingFormats] covers the case where the sheet is opened on a link that has not been
 * read yet. The ladder is there to choose from at that point, and is a full answer on its
 * own, so the sheet says the rest is still coming under its title rather than standing
 * placeholders under rows that are already real.
 *
 * The filter button narrows the list and orders it. Given [onRefresh], the sheet also offers
 * to read the formats again (called with `fresh` set), and when [canChooseSource] says the
 * links can be read by either reader, to switch to the other one (called without it, so a
 * reader that has read the link before answers from its last read).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionSheet(
    info: MediaInfo,
    selected: MediaFormat?,
    onConfirm: (MediaFormat) -> Unit,
    onDismiss: () -> Unit,
    audioFirst: Boolean = false,
    isLoadingFormats: Boolean = false,
    onRefresh: ((source: ListingSource?, fresh: Boolean) -> Unit)? = null,
    canChooseSource: Boolean = false,
    preferredHeight: Int = 0
) {
    // Half height on open. The sheet is a list, and a list is readable from the top down,
    // so the whole screen is offered rather than demanded.
    val sheetState = rememberModalBottomSheetState()

    var sort by remember { mutableStateOf(FormatSort.QUALITY) }
    var filter by remember { mutableStateOf(FormatFilter.ALL) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(selected) }

    // The reader these formats came from: the one that read them, once they are read, and
    // the setting before that. While a switch is reading, the reader picked is shown.
    val context = LocalContext.current
    val settingSource by remember(context) { SettingsRepository.getListingSource(context) }
        .collectAsState(initial = ListingSource.DEFAULT)
    var chosenSource by remember { mutableStateOf<ListingSource?>(null) }
    val readSource = if (info.hasResolvedFormats) info.readBy else settingSource

    // Set while an update or a switch the user asked for is running. The list it replaces
    // is hidden behind the skeleton until then, so old rows are never mistaken for the new
    // answer. Only while something is actually being read: a switch answered from the cache
    // can finish before the read is ever seen as running, and must not leave the list
    // hidden behind it.
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingFormats) { if (!isLoadingFormats) refreshing = false }
    val hidingRows = refreshing && isLoadingFormats
    val source = (if (hidingRows) chosenSource else null) ?: readSource
    val refresh: (ListingSource?, Boolean) -> Unit = { picked, fresh ->
        refreshing = true
        onRefresh?.invoke(picked, fresh)
    }

    // The stops and the rows under each are laid out once per ordering rather than per
    // frame or per tap. Each row carries the text it draws, so switching stops and scrolling
    // do no formatting work and a fast fling has nothing to do but draw.
    val videoTitle = stringResource(R.string.format_sheet_tab_video)
    val audioTitle = stringResource(R.string.format_sheet_tab_audio)
    val allLabel = stringResource(R.string.format_filter_all)
    val stops = remember(info, sort, filter, audioFirst, videoTitle, audioTitle, allLabel) {
        buildStops(info, sort, filter, audioFirst, videoTitle, audioTitle, allLabel)
    }

    // The rail earns its width only when it splits the list into more than one part. While
    // a link is still being read, a video list will have resolutions to split into shortly,
    // so the rail is there from the start rather than pushing the list aside as they land.
    val showRail = stops.size > 2 || (isLoadingFormats && info.videoFormats.isNotEmpty())

    // Opens on the step of the preferred quality set in settings, or the nearest one below
    // it, so the list starts where the download would land. With no preference it opens on
    // "All", which holds every choice.
    var pickedStop by remember { mutableStateOf<String?>(null) }
    val active = remember(stops, pickedStop, showRail, preferredHeight) {
        if (!showRail) stops.first()
        else stops.firstOrNull { it.key == pickedStop }
            ?: stops.firstOrNull { audioFirst && it.key == STOP_AUDIO }
            ?: preferredHeight.takeIf { it > 0 }?.let { ceiling ->
                stops.firstOrNull { stop ->
                    stop.key.startsWith("h") && (stop.key.drop(1).toIntOrNull() ?: Int.MAX_VALUE) <= ceiling
                }
            }
            ?: stops.first()
    }
    val rows = active.rows

    val listState = rememberLazyListState()

    // On a stop that holds the current choice the list opens scrolled to it, with the row
    // above coming along for context. A stop that does not hold it opens at its top. Rows
    // rebuilt while formats arrive leave the scroll where it is.
    var shownStop by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active.key, rows) {
        val index = rows.indexOfFirst { it is FormatListRow.Entry && it.format.formatId == draft?.formatId }
        when {
            index > 0 -> listState.scrollToItem(index - 1)
            shownStop != active.key -> listState.scrollToItem(0)
        }
        shownStop = active.key
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header: stays put while the list scrolls under it ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.format_selection_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.format_selection_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                HeaderIconButton(
                    icon = Icons.Outlined.Tune,
                    description = stringResource(R.string.format_filter_title),
                    onClick = { filterSheetOpen = true }
                )

                if (onRefresh != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    HeaderIconButton(
                        icon = Icons.Filled.Refresh,
                        description = stringResource(R.string.format_update),
                        enabled = !isLoadingFormats,
                        onClick = { refresh(readSource, true) }
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = { draft?.let(onConfirm) },
                    enabled = draft != null,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.format_selection_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isLoadingFormats) {
                // Light runs through the line, so the list reads as still being filled in
                // without placeholders standing under its rows.
                ShimmerLabel(
                    stringResource(R.string.format_selection_loading),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Clears the system's own bar, so the last row can be read and tapped rather
            // than sitting under it.
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // With a rail the panes take the sheet's full height, so switching to a
                    // stop with fewer rows does not make the sheet jump.
                    .weight(1f, fill = showRail)
                    .padding(horizontal = if (showRail) 12.dp else 0.dp)
            ) {
                if (showRail) {
                    FormatRail(
                        stops = stops,
                        activeKey = active.key,
                        loading = isLoadingFormats,
                        bottomInset = bottomInset,
                        onPick = { pickedStop = it },
                        modifier = Modifier
                            .width(RAIL_WIDTH)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (showRail) Modifier.fillMaxHeight() else Modifier),
                    verticalArrangement = Arrangement.spacedBy(if (showRail) 6.dp else 0.dp),
                    contentPadding = PaddingValues(bottom = bottomInset)
                ) {
                    items(
                        count = if (hidingRows) 0 else rows.size,
                        key = { rows[it].key },
                        contentType = { if (rows[it] is FormatListRow.Header) 0 else 1 }
                    ) { index ->
                        // Rows keep their keys across stops, so the one list moves between
                        // them: rows that stay slide into place and the rest fade.
                        val motion = Modifier.animateItem(
                            fadeInSpec = tween(ROW_FADE_MS),
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold
                            ),
                            fadeOutSpec = tween(ROW_FADE_MS)
                        )
                        when (val row = rows[index]) {
                            is FormatListRow.Header -> SectionHeader(
                                row.title,
                                compact = showRail,
                                modifier = motion
                            )
                            is FormatListRow.Entry -> Box(modifier = motion) {
                                FormatRow(
                                    format = row.format,
                                    // By id: a list rebuilt while formats arrive holds new
                                    // objects for the same entries.
                                    selected = row.format.formatId == draft?.formatId,
                                    onClick = { draft = row.format },
                                    compact = showRail
                                )
                            }
                        }
                    }

                    // Audio keeps its place on the rail whatever a source offers, so a link with
                    // no separate audio stream says so here rather than showing a blank pane.
                    if (!isLoadingFormats && active.key == STOP_AUDIO && active.count == 0) {
                        item(key = "audio_empty", contentType = 3) {
                            Text(
                                stringResource(R.string.format_audio_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                            )
                        }
                    }

                    // Skeletons only stand in for a list with nothing to show: one hidden
                    // behind an update, or a stop the read has not filled yet. Under rows
                    // that are already there (the ladder a link opens with, while the link
                    // is read) they would only look like rows that never arrive; the line
                    // under the title and the rail's glint say the read is running.
                    if (isLoadingFormats && (hidingRows || rows.isEmpty())) {
                        item(key = "loading", contentType = 2) {
                            FormatListShimmer(
                                rows = if (hidingRows) 6 else 4,
                                modifier = Modifier.padding(horizontal = if (showRail) 4.dp else 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        FormatFilterSheet(
            filter = filter,
            sort = sort,
            source = source.takeIf { canChooseSource && onRefresh != null },
            onFilter = {
                filter = it
                filterSheetOpen = false
            },
            onSort = {
                sort = it
                filterSheetOpen = false
            },
            onSource = { picked ->
                filterSheetOpen = false
                if (picked != readSource || !info.hasResolvedFormats) {
                    chosenSource = picked
                    filter = FormatFilter.ALL
                    refresh(picked, false)
                }
            },
            onDismiss = { filterSheetOpen = false }
        )
    }
}

private val RAIL_WIDTH = 76.dp
private const val ROW_FADE_MS = 140
private const val STOP_ALL = "all"
private const val STOP_AUDIO = "audio"

/**
 * The column of stops down the left of the sheet. It scrolls on its own where a source
 * reports more resolutions than fit, and otherwise stays still while the list moves.
 *
 * While formats are still being read, a glint runs down the stops themselves rather than
 * past placeholders below them: the stops already there are real, and their counts and the
 * steps still to come change as the rest arrives, so it is the rail as a whole that is
 * marked as not finished.
 */
@Composable
private fun FormatRail(
    stops: List<FormatStop>,
    activeKey: String,
    loading: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlintHost(active = loading, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stops.forEach { stop ->
                RailStop(
                    label = stop.label,
                    count = stop.count,
                    icon = if (stop.key == STOP_AUDIO) Icons.Filled.MusicNote else null,
                    selected = stop.key == activeKey,
                    onClick = { onPick(stop.key) }
                )
            }
        }
    }
}

private val RAIL_STOP_HEIGHT = 54.dp

/**
 * One stop on the rail: its label and how many formats it holds.
 *
 * Flat, as the rest of the sheet is: a step-up tone at rest and the accent's tint when
 * chosen, cross-faded rather than switched. The fill is read while drawing, so the fade
 * repaints the block without recomposing it.
 */
@Composable
private fun RailStop(
    label: String,
    count: Int,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainerHigh,
        animationSpec = tween(STOP_FADE_MS),
        label = "railFill"
    )
    val content by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurface,
        animationSpec = tween(STOP_FADE_MS),
        label = "railContent"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(RAIL_STOP_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { drawRect(fill) }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = content,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (icon == null || count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.55f)
            )
        }
    }
}

private const val STOP_FADE_MS = 160

/** A round button in the sheet's header, drawn the way the sort button always was. */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.18f else 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}

/**
 * What the format list shows, how it is ordered, and which reader its formats come from.
 *
 * Each section is a short list with the current choice ticked, and a choice applies and
 * closes the sheet at once. [source] is null where the links cannot be read both ways, and
 * the section is then left out rather than offering a reader that would not work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatFilterSheet(
    filter: FormatFilter,
    sort: FormatSort,
    source: ListingSource?,
    onFilter: (FormatFilter) -> Unit,
    onSort: (FormatSort) -> Unit,
    onSource: (ListingSource) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            FilterSection(stringResource(R.string.format_filter_title))
            FormatFilter.entries.forEach { option ->
                FilterOption(stringResource(option.labelRes), option == filter) { onFilter(option) }
            }

            FilterSection(stringResource(R.string.format_sort_title))
            FormatSort.entries.forEach { option ->
                FilterOption(stringResource(option.labelRes), option == sort) { onSort(option) }
            }

            if (source != null) {
                FilterSection(stringResource(R.string.format_source_title))
                // The reader that answers soonest first, as the reading settings list them.
                listOf(ListingSource.NEWPIPE, ListingSource.YT_DLP).forEach { option ->
                    FilterOption(stringResource(option.labelRes), option == source) { onSource(option) }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun FilterSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A header bar or one format, in the order the list draws them. */
@Immutable
private sealed interface FormatListRow {
    val key: String

    data class Header(val title: String) : FormatListRow {
        override val key get() = "header_$title"
    }

    data class Entry(val format: MediaFormat, val section: String) : FormatListRow {
        override val key get() = "${section}_${format.formatId}"
    }
}

/**
 * One stop on the rail and the rows the list shows while it is chosen.
 *
 * [count] is worked out here, once, rather than by the rail on every draw.
 */
@Immutable
private class FormatStop(val key: String, val label: String, val rows: List<FormatListRow>) {
    val count: Int = rows.count { it is FormatListRow.Entry }
}

/**
 * Splits the formats into the rail's stops: "All" first, holding both kinds under their
 * headers with the one being chosen leading; then one stop per resolution, highest first;
 * then the audio streams, which keep their stop even when there are none.
 *
 * A resolution's rows keep the order the list is sorted in. Video the source gave no height
 * for, and the generic rows that let the engine decide, have no rung of their own and are
 * found under "All". Entries keep the keys they have under "All", so moving between stops
 * moves the same rows rather than drawing new ones.
 */
private fun buildStops(
    info: MediaInfo,
    sort: FormatSort,
    filter: FormatFilter,
    audioFirst: Boolean,
    videoTitle: String,
    audioTitle: String,
    allLabel: String
): List<FormatStop> {
    val video = filter.apply(info.videoFormats, audio = false).sortedBy(sort)
    val audio = filter.apply(info.audioFormats, audio = true).sortedBy(sort)

    fun entries(title: String, formats: List<MediaFormat>): List<FormatListRow> =
        formats.map { FormatListRow.Entry(it, title) }

    // A section with nothing in it is left out rather than shown as a header with no rows.
    fun section(title: String, formats: List<MediaFormat>): List<FormatListRow> =
        if (formats.isEmpty()) emptyList()
        else listOf(FormatListRow.Header(title)) + entries(title, formats)

    // The kind that leads "All" goes without a header: the rail already says what the stop
    // is, and a label above the first row only pushes the list out of line with the rail.
    val videoRows = if (audioFirst) section(videoTitle, video) else entries(videoTitle, video)
    val audioRows = if (audioFirst) entries(audioTitle, audio) else section(audioTitle, audio)
    val all = if (audioFirst) audioRows + videoRows else videoRows + audioRows

    // The steps come from what the source offers, read through [qualityRung] so a cropped
    // or vertical picture lands on the step it belongs to rather than a rung of its own.
    val byRung = video
        .filter { !it.isGeneric }
        .groupBy { it.qualityRung() }
        .filterKeys { it > 0 }
        .toSortedMap(compareByDescending { it })

    return buildList {
        add(FormatStop(STOP_ALL, allLabel, all))
        byRung.forEach { (rung, formats) ->
            add(FormatStop("h$rung", "${rung}P", entries(videoTitle, formats)))
        }
        // Always there, so the audio streams are found in the same place on every link,
        // including while they are still being read.
        add(FormatStop(STOP_AUDIO, audioTitle, entries(audioTitle, audio)))
    }
}

/**
 * Reorders a tab's formats. The generic "best" row is pinned to the top under every
 * ordering, since it is the recommendation rather than one of the measured entries.
 */
private fun List<MediaFormat>.sortedBy(sort: FormatSort): List<MediaFormat> {
    val (generic, real) = partition { it.isGeneric }
    val ordered = when (sort) {
        // The probe already sorted by quality; keep that order untouched.
        FormatSort.QUALITY -> real
        FormatSort.FILE_SIZE -> real.sortedByDescending { it.fileSizeBytes }
        FormatSort.CONTAINER -> real.sortedWith(
            compareBy<MediaFormat> { it.ext }.thenByDescending { it.height }
        )
    }
    return generic + ordered
}

/**
 * Names the kind of stream the rows under it hold. Full width bar in the single list; in
 * the narrower pane beside the rail, a plain label, since the rail already frames the list.
 */
@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
        )
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

/**
 * One format, with the badges the source actually reported.
 *
 * The container leads as a block the eye can pick out of a long list, the quality is the
 * headline, and everything measured sits under it as pills that scroll sideways rather
 * than wrapping the row into different heights. A list whose rows are all the same height
 * is what keeps a fast fling smooth.
 *
 * Shared with the download sheet, which shows a single instance of this row for whatever is
 * currently selected. [showChevron] marks that instance as the thing you tap to open the
 * full list.
 *
 * [compact] is the row as the pane beside the format rail draws it: a smaller block and
 * headline to fit the narrower width, on a step-up tone of its own so each row reads as
 * something to tap with the rail's stops beside it.
 *
 * [pendingBadges] says the formats are still being read. A row standing in for the best
 * has nothing measured to show yet, so two empty pills hold the places the codec and the
 * size will take, filling with colour on a loop. [popBadges] lets the real badges spring in
 * when they arrive, for the one row in the download sheet that was waiting on them.
 */
@Composable
fun FormatRow(
    format: MediaFormat,
    selected: Boolean,
    onClick: () -> Unit,
    showChevron: Boolean = false,
    mergeAudioId: String? = null,
    compact: Boolean = false,
    pendingBadges: Boolean = false,
    popBadges: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        when {
            selected -> scheme.primary.copy(alpha = 0.14f)
            compact -> scheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        animationSpec = tween(STOP_FADE_MS),
        label = "formatRowFill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { drawRect(fill) }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContainerBadge(text = format.displayContainer, compact = compact)

        Spacer(modifier = Modifier.width(if (compact) 10.dp else 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                val headline = format.label.uppercase()
                val long = headline.length > LONG_HEADLINE
                Text(
                    headline,
                    style = MaterialTheme.typography.titleLarge,
                    // A resolution is short and reads well large. The notes a source
                    // puts on an audio stream are a sentence, and at the same size they
                    // take two lines and shout over the rest of the row.
                    fontSize = when {
                        compact -> if (long) 13.sp else 15.sp
                        long -> 16.sp
                        else -> 20.sp
                    },
                    fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) scheme.primary else scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
                // Bounded, so a long DASH id ("dash-1234…") ellipsizes in its own corner
                // rather than squeezing the headline down to nothing.
                Text(
                    "id: ${shortId(format.formatId)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = if (compact) 10.sp else MaterialTheme.typography.labelMedium.fontSize,
                    color = scheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (compact) 64.dp else 96.dp)
                )
            }

            // Badges are omitted entirely when the extractor did not report them,
            // which is common outside the largest sites.
            val codec = format.codecLabel
            val size = format.sizeLabel
            // The bitrate says something the resolution does not only for audio; on a
            // video row it repeats what the size already showed.
            val generic = format.isGeneric
            // A ladder row's bitrate is its name already, so it is not repeated as a badge.
            val bitrate = format.bitrateLabel.takeIf { !format.hasVideo && !generic }.orEmpty()

            val hasBadges = mergeAudioId != null || codec.isNotBlank() ||
                size.isNotBlank() || bitrate.isNotBlank() || generic

            // Starts small and springs to size once, when this format's badges first show.
            val pop = remember(format.formatId) { Animatable(if (popBadges) BADGE_POP_FROM else 1f) }
            LaunchedEffect(format.formatId, popBadges) {
                if (popBadges && hasBadges) {
                    pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                } else {
                    pop.snapTo(1f)
                }
            }

            // A ladder row while the formats are read holds the places the real badges will
            // take, rather than saying it will be matched later.
            if (pendingBadges && (generic || !hasBadges)) {
                Spacer(modifier = Modifier.height(6.dp))
                FillingBadges()
            } else if (hasBadges) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = pop.value
                            scaleX = p
                            scaleY = p
                            alpha = ((p - BADGE_POP_FROM) / (1f - BADGE_POP_FROM)).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mergeAudioId != null) {
                        MetaBadge(
                            text = "id: ${shortId(mergeAudioId)}",
                            icon = Icons.Filled.MusicNote,
                            tone = BadgeTone.SOLID
                        )
                    }
                    // Said once, on the ladder's rows: the step is matched to a real stream
                    // when the download starts, so there is no codec or size to show yet.
                    if (generic) MetaBadge(stringResource(R.string.format_generic_badge), tone = BadgeTone.ACCENT)
                    if (codec.isNotBlank()) MetaBadge(codec)
                    if (size.isNotBlank()) MetaBadge(size, tone = BadgeTone.SIZE)
                    if (bitrate.isNotBlank()) MetaBadge(bitrate, tone = BadgeTone.ACCENT)
                }
            }
        }

        if (showChevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = stringResource(R.string.format_selection_change),
                modifier = Modifier.size(18.dp),
                tint = scheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * An id short enough to sit beside a headline. Sites such as Instagram number their DASH
 * streams with twenty digits, which pushed the row to three lines; the start is enough to
 * tell them apart at a glance.
 */
internal fun shortId(id: String): String =
    if (id.length > MAX_ID_CHARS) id.take(MAX_ID_CHARS - 1) + "…" else id

private const val MAX_ID_CHARS = 12

/** Past this many characters a headline is a sentence rather than a label. */
private const val LONG_HEADLINE = 22

/** The container block a row leads with, sized the same whatever the word inside it is. */
@Composable
private fun ContainerBadge(text: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .size(width = if (compact) 48.dp else 60.dp, height = if (compact) 44.dp else 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        val cleanText = text.trim().uppercase()
        val fontSize = when {
            cleanText.length > 5 -> if (compact) 9.sp else 10.sp
            cleanText.length >= 4 -> if (compact) 10.sp else 11.5.sp
            else -> if (compact) 12.sp else 13.sp
        }
        Text(
            text = cleanText,
            style = MaterialTheme.typography.labelLarge,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
        )
    }
}

/** How small the badges start when they spring in. */
private const val BADGE_POP_FROM = 0.6f

/** One fill of a waiting pill, from empty through full to faded. */
private const val BADGE_FILL_MS = 1300

/** Share of a fill spent growing; the rest is spent full and fading out. */
private const val BADGE_FILL_GROW = 0.7f

/**
 * The two pills that stand where the codec and the size will be, filling with colour while
 * the formats are read: the codec's in the accent, the size's in its own blue, a beat
 * behind. One loop drives both, and it is read while drawing, so the fill repaints the
 * pills without recomposing them; it stops as soon as the row has real badges to show.
 */
@Composable
private fun FillingBadges() {
    val transition = rememberInfiniteTransition(label = "fillingBadges")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BADGE_FILL_MS, easing = LinearEasing)),
        label = "fillingBadgesFill"
    )
    val accent = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FillingPill(
            width = 44.dp,
            rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            fill = accent.copy(alpha = 0.45f),
            progress = { progress.value },
            phase = 0f
        )
        FillingPill(
            width = 70.dp,
            rest = SizeBadgeContainer,
            fill = SIZE_FILL_BLUE,
            progress = { progress.value },
            phase = 0.2f
        )
    }
}

/** The size badge's blue, lifted, so the fill shows against the badge it is filling. */
private val SIZE_FILL_BLUE = Color(0x998FB8F0)

@Composable
private fun FillingPill(
    width: androidx.compose.ui.unit.Dp,
    rest: Color,
    fill: Color,
    progress: () -> Float,
    phase: Float
) {
    Box(
        modifier = Modifier
            .size(width = width, height = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                drawRect(rest)
                val t = (progress() + 1f - phase) % 1f
                val grown: Float
                val alpha: Float
                if (t < BADGE_FILL_GROW) {
                    val x = t / BADGE_FILL_GROW
                    // Eases out, so the fill races in and slows as it reaches the end.
                    grown = 1f - (1f - x) * (1f - x)
                    alpha = 1f
                } else {
                    grown = 1f
                    alpha = 1f - (t - BADGE_FILL_GROW) / (1f - BADGE_FILL_GROW)
                }
                drawRect(
                    color = fill.copy(alpha = fill.alpha * alpha),
                    size = Size(size.width * grown, size.height)
                )
            }
    )
}

/** How loud a badge is, from a plain measurement to the point of the row. */
enum class BadgeTone {
    /** Codecs and other facts that are read only when something else has been decided. */
    NEUTRAL,

    /** The file size, which has a colour of its own because it is what lists are scanned for. */
    SIZE,

    /** The bitrate, tinted rather than filled so it sits between the two. */
    ACCENT,

    /** The audio track that will be muxed in, which is the one thing here that is a choice. */
    SOLID
}

/** Small pill under a format's headline. */
@Composable
fun MetaBadge(
    text: String,
    icon: ImageVector? = null,
    tone: BadgeTone = BadgeTone.NEUTRAL
) {
    val container = when (tone) {
        BadgeTone.SOLID -> MaterialTheme.colorScheme.primary
        BadgeTone.SIZE -> SizeBadgeContainer
        BadgeTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val content = when (tone) {
        BadgeTone.SOLID -> MaterialTheme.colorScheme.onPrimary
        BadgeTone.SIZE -> SizeBadgeContent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = content
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = content
        )
    }
}
