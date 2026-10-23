package com.hazel.android.ui.screens.history

import com.hazel.android.ui.theme.tonalFill
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.screens.more.ChoiceRow
import com.hazel.android.ui.screens.more.groupShape
import com.hazel.android.ui.components.FlatIconButton
import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewAgenda
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.hazel.android.ui.components.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.ui.components.FastScrollbar
import com.hazel.android.ui.components.gridColumns
import com.hazel.android.ui.components.gridItems
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.HistorySort
import com.hazel.android.data.HistoryStatus
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.CardTag
import com.hazel.android.ui.components.formatDateTime
import com.hazel.android.ui.components.rememberPresence
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaPresence
import kotlinx.coroutines.launch
import android.app.Activity
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import com.hazel.android.ui.screens.download.FeedbackToast
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.window.Dialog
import com.hazel.android.ui.theme.glassBackdrop
import com.hazel.android.util.MediaDeleter

/**
 * What has finished downloading.
 *
 * Only finished files live here; what is running, waiting or failed has the queue screen.
 * The row under the title holds the view settings a person changes while looking at the
 * list rather than in a menu: the layout, the order and its direction, and whether audio or
 * video is shown. Layout and order are remembered between visits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val history by remember(context) { DownloadHistoryRepository.getHistory(context) }.collectAsState(initial = emptyList())
    val listLayout by remember(context) { SettingsRepository.getHistoryListLayout(context) }
        .collectAsState(initial = false)
    val sortSetting by remember(context) { SettingsRepository.getHistorySort(context) }
        .collectAsState(initial = HistorySort.NEWEST to false)
    val (sort, reversed) = sortSetting

    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(HistoryStatus.ALL) }

    var sortMenuOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var statusSheetOpen by remember { mutableStateOf(false) }
    var confirmRemoveAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    var actionFor by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    var properties by remember { mutableStateOf<HistoryEntry?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var consentIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val presence = rememberPresence(history)

    val dismissSearch = {
        query = ""
        keyboard?.hide()
        searchOpen = false
    }

    val visible = remember(history, sort, reversed, query, showAudio, showVideo, status, presence.toMap()) {
        history
            .asSequence()
            // Neither chip on, or both, is everything; one on narrows to that kind.
            .filter { showAudio == showVideo || it.isVideo == showVideo }
            .filter { entry ->
                val present = presence[entry.id] ?: true
                when (status) {
                    HistoryStatus.ALL -> true
                    HistoryStatus.PRESENT -> present
                    HistoryStatus.DELETED -> !present
                }
            }
            .filter { entry ->
                query.isBlank() || entry.title.contains(query, ignoreCase = true) ||
                    entry.author.contains(query, ignoreCase = true)
            }
            .toList()
            .let { entries ->
                val ordered = when (sort) {
                    HistorySort.NEWEST -> entries.sortedByDescending { it.completedAt }
                    HistorySort.TITLE -> entries.sortedBy { it.title.lowercase() }
                    HistorySort.SIZE -> entries.sortedByDescending { it.sizeBytes }
                }
                if (reversed) ordered.reversed() else ordered
            }
    }

    LaunchedEffect(visible) {
        selected = HistorySelection.prune(selected, visible)
        if (visible.isEmpty()) selecting = false
    }
    val exitSelection = {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { exitSelection() }

    var feedback by remember { mutableStateOf<DeleteFeedback?>(null) }
    var shownFeedback by remember { mutableStateOf(DeleteFeedback("", true)) }
    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        shownFeedback = current
        delay(2000)
        feedback = null
    }
    fun toast(text: String, ok: Boolean = true) {
        feedback = DeleteFeedback(text, ok)
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ids = consentIds
        consentIds = emptySet()
        if (result.resultCode == Activity.RESULT_OK && ids.isNotEmpty()) {
            scope.launch {
                DownloadHistoryRepository.removeAll(context, ids)
                MediaPresence.forget()
                toast(resources.getQuantityString(R.plurals.history_toast_files_deleted, ids.size, ids.size))
            }
        }
    }

    val deleteFiles: (List<HistoryEntry>) -> Unit = { entries ->
        scope.launch {
            val gone = entries.filter { presence[it.id] == false }.mapTo(HashSet()) { it.id }
            val outcome = DownloadHistoryRepository.deleteFiles(context, entries, gone)
            val request = MediaDeleter.consentRequest(context, outcome.consentUris)
            val asked = request != null && runCatching {
                consentIds = outcome.consentIds
                consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure { consentIds = emptySet() }.isSuccess
            val failed = outcome.failed + if (asked) 0 else outcome.consentIds.size
            when {
                failed > 0 -> toast(resources.getQuantityString(R.plurals.history_toast_files_delete_failed, failed, failed), ok = false)
                outcome.deleted > 0 -> toast(
                    resources.getQuantityString(R.plurals.history_toast_files_deleted, outcome.deleted, outcome.deleted)
                )
                outcome.removedIds.isNotEmpty() -> toast(
                    resources.getQuantityString(R.plurals.history_toast_removed, outcome.removedIds.size, outcome.removedIds.size)
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionHeader(
                count = selected.size,
                allChosen = HistorySelection.allChosen(selected, visible),
                onClose = exitSelection,
                onToggleAll = {
                    selected = if (HistorySelection.allChosen(selected, visible)) emptySet()
                    else HistorySelection.all(visible)
                },
                onAct = {
                    HistorySelection.targets(selected, visible).takeIf { it.isNotEmpty() }?.let { actionFor = it }
                }
            )
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.history_search_action),
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { statusSheetOpen = true }) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = stringResource(R.string.history_filter_action),
                    tint = if (status != HistoryStatus.ALL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.history_menu_action))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_select)) },
                        enabled = visible.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            keyboard?.hide()
                            selected = emptySet()
                            selecting = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_select_all)) },
                        enabled = visible.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            keyboard?.hide()
                            selected = HistorySelection.all(visible)
                            selecting = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_remove_all)) },
                        enabled = history.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            confirmRemoveAll = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_remove_deleted)) },
                        enabled = history.any { presence[it.id] == false },
                        onClick = {
                            menuOpen = false
                            val gone = history.filter { presence[it.id] == false }.mapTo(mutableSetOf()) { it.id }
                            scope.launch { DownloadHistoryRepository.removeAll(context, gone) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_remove_duplicates)) },
                        enabled = history.size > 1,
                        onClick = {
                            menuOpen = false
                            val repeats = DownloadHistoryRepository.duplicatesIn(history)
                            scope.launch { DownloadHistoryRepository.removeAll(context, repeats) }
                        }
                    )
                }
            }
        }

        // ── Search ──
        AnimatedVisibility(
            visible = searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClose = { dismissSearch() }
            )
        }

        // ── View settings ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlatIconButton(
                icon = if (listLayout) Icons.Filled.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = stringResource(R.string.history_layout_toggle),
                onClick = { scope.launch { SettingsRepository.setHistoryListLayout(context, !listLayout) } }
            )
            VerticalDivider(modifier = Modifier.height(24.dp))
            Box {
                FlatChip(
                    label = stringResource(sort.labelRes),
                    onClick = { sortMenuOpen = true },
                    leading = {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.history_sort_reverse),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (reversed) 180f else 0f)
                        )
                    }
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    HistorySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            trailingIcon = if (option == sort) {
                                {
                                    Icon(
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(if (reversed) 180f else 0f)
                                    )
                                }
                            } else null,
                            onClick = {
                                sortMenuOpen = false
                                // Picking the order already in use flips its direction.
                                val flip = if (option == sort) !reversed else false
                                scope.launch { SettingsRepository.setHistorySort(context, option, flip) }
                            }
                        )
                    }
                }
            }
            VerticalDivider(modifier = Modifier.height(24.dp))
            KindChip(
                label = stringResource(R.string.properties_kind_audio),
                icon = Icons.Filled.MusicNote,
                selected = showAudio,
                onClick = { showAudio = !showAudio }
            )
            KindChip(
                label = stringResource(R.string.properties_kind_video),
                icon = Icons.Filled.Videocam,
                selected = showVideo,
                onClick = { showVideo = !showVideo }
            )
        }

        // ── The list ──
        // While the search field is open, a tap anywhere on the list closes it again, the
        // way a tap outside any field hands the screen back.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(
                        if (history.isEmpty()) R.string.history_empty_initial else R.string.history_empty_filtered
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            val shrink = rememberScrollShrink()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val spacing = if (listLayout) 10.dp else 12.dp
                // A wide screen lays the entries out side by side, inside the list's padding.
                val columns = gridColumns(maxWidth - 40.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(shrink),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    gridItems(visible, columns, spacing, key = { it.id }) { entry ->
                        val present = presence[entry.id] ?: true
                        val open: () -> Unit = {
                            scope.launch {
                                if (MediaPresence.refresh(context, entry.fileUri)) {
                                    MediaOpener.play(context, entry.fileUri, entry.isVideo)
                                } else {
                                    presence[entry.id] = false
                                    toast(resources.getString(R.string.history_toast_file_gone), ok = false)
                                }
                            }
                        }
                        val toggle = { selected = HistorySelection.toggle(selected, entry.id) }
                        val actions = EntryActions(
                            onOpen = if (selecting) toggle else open,
                            onHold = if (selecting) toggle else { { actionFor = listOf(entry) } },
                            onProperties = { properties = entry },
                            onRemove = { scope.launch { DownloadHistoryRepository.remove(context, entry.id) } },
                            onDeleteFile = { confirmDelete = listOf(entry) }
                        )
                        val mark = if (selecting) entry.id in selected else null
                        Box(modifier = Modifier.scrollShrink(shrink)) {
                            if (listLayout) HistoryRow(entry, present, mark, actions)
                            else HistoryCard(entry, present, mark, actions)
                        }
                    }
                }
                FastScrollbar(listState, Modifier.align(Alignment.TopEnd), PaddingValues(top = 8.dp, bottom = 24.dp))
            }
        }
        if (searchOpen && !selecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { dismissSearch() }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            FeedbackToast(
                shownFeedback.message,
                icon = if (shownFeedback.ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                iconTint = if (shownFeedback.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        }
    }

    if (statusSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { statusSheetOpen = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            dragHandle = null
        ) {
            Column(modifier = Modifier.padding(top = 20.dp).navigationBarsPadding()) {
                Text(
                    stringResource(R.string.history_filter_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HistoryStatus.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            label = stringResource(option.labelRes),
                            description = null,
                            chosen = option == status,
                            role = Role.RadioButton,
                            shape = groupShape(index, HistoryStatus.entries.size),
                            onClick = {
                                status = option
                                statusSheetOpen = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text(stringResource(R.string.history_clear_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveAll = false
                    scope.launch { DownloadHistoryRepository.clear(context) }
                }) { Text(stringResource(R.string.history_clear_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) {
                    Text(stringResource(R.string.history_clear_dialog_cancel))
                }
            }
        )
    }

    properties?.let { entry ->
        DownloadPropertiesSheet(
            entry = entry,
            present = presence[entry.id] ?: true,
            onDismiss = { properties = null }
        )
    }

    actionFor?.let { entries ->
        HistoryActionDialog(
            entries = entries,
            anyPresent = entries.any { presence[it.id] != false },
            onDismiss = { actionFor = null },
            onProperties = { properties = entries.single() },
            onRemove = {
                val ids = entries.mapTo(HashSet()) { it.id }
                scope.launch {
                    DownloadHistoryRepository.removeAll(context, ids)
                    toast(resources.getQuantityString(R.plurals.history_toast_removed, ids.size, ids.size))
                }
                exitSelection()
            },
            onDelete = { confirmDelete = entries }
        )
    }

    confirmDelete?.let { entries ->
        val goneCount = entries.count { presence[it.id] == false }
        val fileCount = entries.size - goneCount
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = {
                Text(
                    stringResource(
                        if (entries.size == 1) R.string.history_delete_dialog_title else R.string.history_delete_many_title
                    ),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (entries.size == 1) stringResource(R.string.history_delete_dialog_body, entries.single().fileName)
                    else listOfNotNull(
                        pluralStringResource(R.plurals.history_delete_many_body, fileCount, fileCount).takeIf { fileCount > 0 },
                        pluralStringResource(R.plurals.history_delete_many_gone, goneCount, goneCount).takeIf { goneCount > 0 }
                    ).joinToString("\n\n")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    deleteFiles(entries)
                    exitSelection()
                }) { Text(stringResource(R.string.history_delete_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.history_delete_dialog_cancel))
                }
            }
        )
    }
}

/** The top bar while picking rows: how many are picked, select all, and what to do with them. */
@Composable
private fun SelectionHeader(
    count: Int,
    allChosen: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onAct: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_select_close))
        }
        Text(
            pluralStringResource(R.plurals.history_selected_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleAll) {
            Icon(
                if (allChosen) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                contentDescription = stringResource(
                    if (allChosen) R.string.history_select_none else R.string.history_menu_select_all
                ),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onAct, enabled = count > 0) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_select_act))
        }
    }
}

/** What a held row, or the picked rows, can do: leave the files and clear the list, or delete the files too. */
@Composable
private fun HistoryActionDialog(
    entries: List<HistoryEntry>,
    anyPresent: Boolean,
    onDismiss: () -> Unit,
    onProperties: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit
) {
    val single = entries.size == 1
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.glassBackdrop(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    if (single) entries.single().title
                    else pluralStringResource(R.plurals.history_selected_count, entries.size, entries.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                )
                val options = buildList<Triple<Int, ImageVector, () -> Unit>> {
                    if (single) add(Triple(R.string.history_card_properties, Icons.Filled.Info, onProperties))
                    add(Triple(R.string.history_card_remove, Icons.Filled.RemoveCircleOutline, onRemove))
                    if (anyPresent) add(Triple(R.string.history_card_delete, Icons.Filled.DeleteForever, onDelete))
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEachIndexed { index, (label, icon, action) ->
                        val destructive = icon == Icons.Filled.DeleteForever
                        ChoiceRow(
                            label = stringResource(label),
                            description = when (label) {
                                R.string.history_card_remove -> stringResource(R.string.history_remove_hint)
                                R.string.history_card_delete -> stringResource(R.string.history_delete_hint)
                                else -> null
                            },
                            chosen = false,
                            role = Role.Button,
                            shape = groupShape(index, options.size),
                            leading = {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (destructive) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                onDismiss()
                                action()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A new instance per message, so the same text twice still shows twice. */
private class DeleteFeedback(val message: String, val ok: Boolean)

/** What can be done with one finished download, whichever layout draws it. */
private class EntryActions(
    val onOpen: () -> Unit,
    val onHold: () -> Unit,
    val onProperties: () -> Unit,
    val onRemove: () -> Unit,
    val onDeleteFile: () -> Unit
)

@Composable
private fun KindChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FlatChip(label = label, onClick = onClick, icon = icon, selected = selected)
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.history_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.history_search_clear),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The entry's options, anchored to whatever opened them. */
@Composable
private fun EntryMenu(
    expanded: Boolean,
    present: Boolean,
    actions: EntryActions,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_card_properties)) },
            onClick = {
                onDismiss()
                actions.onProperties()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_card_remove)) },
            onClick = {
                onDismiss()
                actions.onRemove()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_card_delete)) },
            enabled = present,
            onClick = {
                onDismiss()
                actions.onDeleteFile()
            }
        )
    }
}

/** A finished download at full width, its artwork behind its details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(entry: HistoryEntry, present: Boolean, mark: Boolean?, actions: EntryActions) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = tonalFill(0.3f),
        border = if (mark == true) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .combinedClickable(
                    onClick = { if (mark != null || present) actions.onOpen() },
                    onLongClick = actions.onHold
                )
        ) {
            Artwork(entry, present, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.65f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
            )

            Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 48.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.author.isNotBlank()) {
                    Text(
                        entry.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (mark == true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)))
            }
            if (mark != null) {
                SelectMark(mark, Modifier.align(Alignment.TopEnd).padding(12.dp), onImage = true)
            } else Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.history_card_options),
                        tint = Color.White
                    )
                }
                EntryMenu(menuOpen, present, actions) { menuOpen = false }
            }

            // One row across the bottom, the date taking what the tags leave, so a long
            // date on a narrow screen is cut short instead of drawn over the tags.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (entry.isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                formatDuration(entry.durationSeconds).takeIf { it.isNotBlank() }?.let { CardTag(it) }
                if (entry.sizeBytes > 0) CardTag(formatFileSize(entry.sizeBytes))
                if (!present) DeletedTag()
                Text(
                    formatDateTime(entry.completedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 2.dp)
                )
            }
        }
    }
}

/** A finished download as one compact row: artwork at the side, details beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(entry: HistoryEntry, present: Boolean, mark: Boolean?, actions: EntryActions) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (mark == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                )
                .combinedClickable(
                    onClick = { if (mark != null || present) actions.onOpen() },
                    onLongClick = actions.onHold
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Artwork(entry, present, Modifier.fillMaxSize())
                formatDuration(entry.durationSeconds).takeIf { it.isNotBlank() }?.let {
                    CardTag(it, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // The tags wrap rather than share one line: a long author in a Row took the
                // whole width and squeezed the date to a single character per line, which
                // stretched the row to many times its height.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val chipColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    val chipText = MaterialTheme.colorScheme.primary
                    if (entry.author.isNotBlank()) {
                        CardTag(entry.author, background = chipColor, foreground = chipText)
                    }
                    CardTag(formatDateTime(entry.completedAt), background = chipColor, foreground = chipText)
                    if (!present) DeletedTag()
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (mark != null) SelectMark(mark)
            else Icon(
                if (entry.isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                contentDescription = stringResource(
                    if (entry.isVideo) R.string.properties_kind_video else R.string.properties_kind_audio
                ),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The tick on a row while picking: filled when picked, an empty ring when not. */
@Composable
private fun SelectMark(chosen: Boolean, modifier: Modifier = Modifier, onImage: Boolean = false) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (chosen) MaterialTheme.colorScheme.primary else if (onImage) Color.Black.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                2.dp,
                when {
                    chosen -> MaterialTheme.colorScheme.primary
                    onImage -> Color.White.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (chosen) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun Artwork(entry: HistoryEntry, present: Boolean, modifier: Modifier) {
    if (entry.thumbnail != null) {
        AsyncImage(
            model = entry.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (present) 1f else 0.7f,
            // A file that is gone keeps its row, drained of colour, so the record still says
            // what was downloaded without looking like something that will open.
            colorFilter = if (present) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = modifier
        )
    }
}

@Composable
private fun DeletedTag() {
    CardTag(
        stringResource(R.string.history_tag_deleted),
        background = MaterialTheme.colorScheme.error,
        foreground = MaterialTheme.colorScheme.onError
    )
}
