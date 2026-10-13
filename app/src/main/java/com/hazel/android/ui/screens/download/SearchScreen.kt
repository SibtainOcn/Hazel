package com.hazel.android.ui.screens.download

import com.hazel.android.ui.components.ActionsRow
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.download.extractor.SearchSuggestions
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.screens.more.groupShape
import com.hazel.android.util.LinkKey
import com.hazel.android.util.MediaOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Full screen search and link entry.
 *
 * Links are read as links; anything else is searched for on the site picked in the chips
 * under the field. More than one link can be queued before anything is read: the plus
 * button parks a typed link in a list under the field and clears the field for the next one.
 *
 * Earlier searches and links sit underneath, with suggestions from Google when those are
 * turned on. Tapping one uses it; the arrow beside it puts it in the field for editing.
 */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onSearch: (List<String>) -> Unit,
    onSearchWords: (String, SearchSource) -> Unit,
    onClearResults: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val focusRequester = remember { FocusRequester() }

    val history by remember(context) { SearchHistoryRepository.getHistory(context) }.collectAsState(initial = emptyList())

    var text by remember { mutableStateOf(initialQuery) }
    var queued by remember { mutableStateOf(listOf<String>()) }

    // Everything already downloaded, so a repeat can be raised before the link is read.
    val downloaded by remember(context) { DownloadHistoryRepository.getHistory(context) }
        .collectAsState(initial = emptyList())

    // The links waiting on an answer, paired with the copy already on the device.
    var pendingDuplicate by remember {
        mutableStateOf<Pair<List<String>, HistoryEntry>?>(null)
    }

    var menuOpen by remember { mutableStateOf(false) }
    // The remembered link a long press asked to remove, until the dialog is answered.
    var removing by remember { mutableStateOf<String?>(null) }
    // The same for a link in the list waiting to be read.
    var removingQueued by remember { mutableStateOf<String?>(null) }

    // Needed only to open the folder a repeat warning refers to.
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())

    val source by remember(context) { SettingsRepository.getSearchSource(context) }.collectAsState(initial = SearchSource.DEFAULT)
    val suggestionsOn by remember(context) { SettingsRepository.getSearchSuggestions(context) }.collectAsState(initial = false)

    // Links already queued are hidden from the list, and what is being typed filters it.
    val suggestions = remember(history, text, queued) {
        history.filter { it !in queued && it.contains(text.trim(), ignoreCase = true) }
    }

    val typingWords = text.isNotBlank() && !isLinks(text)

    // Google's completions for words being typed, asked for once typing pauses.
    var remote by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(text, source, suggestionsOn) {
        if (!suggestionsOn || !typingWords) {
            remote = emptyList()
            return@LaunchedEffect
        }
        delay(SUGGESTION_DELAY_MS)
        remote = SearchSuggestions.forQuery(text, source)
    }
    val remoteShown = remember(remote, suggestions) {
        remote.filter { hint -> suggestions.none { it.equals(hint, ignoreCase = true) } }
    }

    /**
     * Splits what is in the field into the links it holds.
     *
     * Someone with several links to hand copies them together and pastes them together,
     * separated by whatever the place they came from used: a space, a newline, a tab. The
     * field used to take the whole paste as one link, which cannot match an address and was
     * reported as the paste being invalid rather than as several links having arrived.
     */
    fun typedLinks(): List<String> =
        text.split(WHITESPACE).map { it.trim() }.filter { it.isNotBlank() }

    // A link already in the list, even spelled differently, is not added twice. When that is
    // all the field holds it stays put and says so, rather than vanishing as if added.
    fun queueCurrent() {
        val typed = typedLinks()
        val entries = typed.filterNot { link -> queued.any { it == link || LinkKey.sameMedia(it, link) } }
            .distinctBy { LinkKey.canonical(it).ifBlank { it } }
        if (entries.isEmpty() && typed.isNotEmpty()) {
            Toast.makeText(context, resources.getString(R.string.search_link_already_added), Toast.LENGTH_SHORT).show()
            return
        }
        queued = queued + entries
        text = ""
    }

    fun startSearch(links: List<String>) {
        keyboard?.hide()
        HazelApp.instance.applicationScope.launch(Dispatchers.IO) {
            val app = context.applicationContext
            if (!SettingsRepository.getIncognito(app).first()) {
                links.forEach { SearchHistoryRepository.record(app, it) }
            }
        }
        onSearch(links)
    }

    fun searchWords(words: String) {
        keyboard?.hide()
        onSearchWords(words.trim(), source)
    }

    fun submit() {
        if (queued.isEmpty() && text.isNotBlank() && !isLinks(text)) {
            searchWords(text)
            return
        }
        val all = (queued + typedLinks()).filter { it.isNotBlank() }.distinct()
        if (all.isEmpty()) return

        scope.launch {
            // Raised here rather than after the link has been read, because this is where
            // the choice still costs nothing: going back means editing the field that is
            // already open, and leaving it means the seconds of reading are never spent.
            //
            // Matched on what the link points at rather than on how it is spelled, so a
            // share link and an address-bar link for the same video count as one. Only a
            // copy still on the device counts: an entry whose file has since been deleted
            // is no reason to stop anyone downloading it again.
            val existing = all.firstNotNullOfOrNull { link ->
                downloaded
                    .firstOrNull { LinkKey.sameMedia(it.url, link) }
                    ?.takeIf { DownloadHistoryRepository.fileExists(context, it) }
            }

            if (existing != null) pendingDuplicate = all to existing else startSearch(all)
        }
    }

    /**
     * Takes whatever is on the clipboard and reads it straight away.
     *
     * A link almost always arrives by being copied somewhere else, so the field's first act
     * is nearly always a paste followed by a confirm. This is those two steps as one. It
     * goes through [submit], so a paste of several links is split the same way a typed one
     * is and a link already downloaded still raises the same warning.
     */
    fun pasteAndSearch() = scope.launch {
        val pasted = clipboard.getClipEntry()?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
            .orEmpty().trim()
        if (pasted.isBlank()) {
            Toast.makeText(
                context,
                resources.getString(R.string.search_nothing_to_paste),
                Toast.LENGTH_SHORT
            ).show()
            return@launch
        }
        text = pasted
        submit()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Shown as a dialog rather than as screen content so it covers the app bar and the
    // navigation bar too. Link entry takes over the whole screen while it is open.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            // The dialog is told to lay itself out against the real window rather than
            // inside what the system leaves over. Without it the keyboard's height is
            // reported as nothing, which is fine for a screen that only stacks downwards
            // and wrong for the paste control, which hangs off the bottom corner and was
            // being drawn underneath the keyboard. The bars are paid for below instead.
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { onDismiss() }

        pendingDuplicate?.let { (links, existing) ->
            AlreadyDownloadedDialog(
                entry = existing,
                onPlay = { MediaOpener.play(context, existing.fileUri, existing.isVideo) },
                onOpenLocation = {
                    MediaOpener.openLocation(context, saveDirs.of(existing.isVideo).uri, existing.isVideo)
                },
                onDownloadAgain = {
                    pendingDuplicate = null
                    startSearch(links)
                },
                onDismiss = { pendingDuplicate = null }
            )
        }

        removing?.let { entry ->
            RemoveEntryDialog(
                entry = entry,
                onConfirm = {
                    removing = null
                    HazelApp.instance.applicationScope.launch(Dispatchers.IO) {
                        SearchHistoryRepository.remove(context.applicationContext, entry)
                    }
                },
                onDismiss = { removing = null }
            )
        }

        removingQueued?.let { entry ->
            RemoveEntryDialog(
                entry = entry,
                body = stringResource(R.string.search_remove_from_list_body),
                onConfirm = {
                    removingQueued = null
                    queued = queued - entry
                },
                onDismiss = { removingQueued = null }
            )
        }

        if (menuOpen) {
            SearchOptionsSheet(
                onClearResults = onClearResults,
                onDismiss = { menuOpen = false }
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // What the window no longer does for itself. The screen keeps clear of the
                // status bar at the top and the navigation bar at the foot exactly as it
                // did, and the keyboard is now something the layout can see.
                .systemBarsPadding()
        ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Title row: back, the screen's name, and the search options ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.search_back))
                }
                Text(
                    stringResource(R.string.fetch_settings_search),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.search_more))
                }
            }

            // ── The field, with clear inside it and add just after it ──
            // A long link gets nearly the whole row, and the actions on what is typed sit
            // beside it rather than crowding the title row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The same margin both sides, unless add stands in the right one.
                    .padding(start = 12.dp, end = if (text.isNotBlank() && !typingWords) 4.dp else 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Surface(
                modifier = Modifier.weight(1f),
                // The corners of the app's dialogs and cards, rather than a full pill.
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() })
                    )

                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides FIELD_ACTION_SIZE) {
                        if (text.isNotBlank() || queued.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    text = ""
                                    queued = emptyList()
                                },
                                modifier = Modifier.size(FIELD_ACTION_SIZE)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            // Parks the typed link so another can be entered.
            if (text.isNotBlank() && !typingWords) {
                IconButton(onClick = { queueCurrent() }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.search_add_link))
                }
            }
            }

            // ── Where to search ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchSource.entries.forEach { option ->
                    FlatChip(
                        label = stringResource(option.labelRes),
                        selected = option == source,
                        onClick = {
                            scope.launch { SettingsRepository.setSearchSource(context, option) }
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ── Queued links ──
            // How many, and the one action that reads them all, over the links themselves as
            // one group of rows. Holding a row asks before taking it out; a tap does nothing,
            // so a link is never lost to a stray touch.
            if (queued.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pluralStringResource(R.plurals.batch_links, queued.size, queued.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = { submit() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.search_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = QUEUE_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        queued.forEachIndexed { index, entry ->
                            QueuedLinkRow(
                                entry = entry,
                                position = index + 1,
                                shape = groupShape(index, queued.size),
                                onHold = { removingQueued = entry }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            // ── History and suggestions ──
            if (suggestions.isEmpty() && remoteShown.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        if (history.isEmpty()) stringResource(R.string.search_empty_history)
                        else stringResource(R.string.search_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(suggestions, key = { it }) { entry ->
                        HistoryRow(
                            query = entry,
                            onUse = {
                                text = entry
                                submit()
                            },
                            onFill = { text = entry },
                            onRemove = { removing = entry }
                        )
                    }
                    items(remoteShown, key = { "suggestion:$it" }) { hint ->
                        SuggestionRow(
                            query = hint,
                            onUse = { searchWords(hint) },
                            onFill = { text = hint }
                        )
                    }
                    }
                }
            }

            // ── Paste ──
            //
            // A link is nearly always copied somewhere else first, so the opening move on
            // this screen is a paste and then a confirm. This is the two of them at once.
            // It goes through the same submit the field does, so a paste holding several
            // links is split the way a typed one is and a link already downloaded still
            // raises the same warning.
            //
            // Offered only with nothing entered, which is the moment it stands for, and
            // held clear of the keyboard that is up for as long as this screen is.
            if (text.isBlank() && queued.isEmpty()) {
                Surface(
                    onClick = { pasteAndSearch() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .padding(20.dp)
                        .size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.search_paste_and_fetch),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * One link waiting to be read: its place in the list, then the site it is on, with the rest
 * of the address dimmed under it so the two are easy to tell apart at a glance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuedLinkRow(
    entry: String,
    position: Int,
    shape: RoundedCornerShape,
    onHold: () -> Unit
) {
    val (site, rest) = remember(entry) { linkParts(entry) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .combinedClickable(
                onClick = {},
                onLongClick = onHold,
                onLongClickLabel = stringResource(R.string.search_remove)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                position.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                site,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rest.isNotEmpty()) {
                Text(
                    rest,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A link as the site it is on and the rest of its address: `youtu.be` and `/abc?si=x`.
 * The leading `www.` is dropped from the site. Anything that does not read as an address
 * comes back whole, as the site, with nothing after it.
 */
internal fun linkParts(link: String): Pair<String, String> {
    val withScheme = if ("://" in link) link else "https://$link"
    val uri = runCatching { URI(withScheme) }.getOrNull()
    val host = uri?.host?.removePrefix("www.")
    if (uri == null || host.isNullOrBlank()) return link to ""
    val rest = (uri.rawPath ?: "") + (uri.rawQuery?.let { "?$it" } ?: "")
    return host to rest
}

/**
 * Asks before taking a link away, raised by holding its row: from the search history by
 * default, or from wherever [body] says. The entry heads the dialog, so it is plain which
 * one goes.
 */
@Composable
internal fun RemoveEntryDialog(
    entry: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String = stringResource(R.string.search_remove_confirm_body)
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                entry,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = { Text(body) },
        // Copy, Cancel and Remove as one group, which wraps rather than squeezing a label
        // where the three do not fit one line.
        confirmButton = {
            ActionsRow(spacing = 0.dp) {
                TextButton(onClick = {
                    copySheetLink(context, entry)
                    // Android 13 and later show their own notice of a copy.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, context.getString(R.string.search_entry_copied), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.sheet_link_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_cancel)) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.search_remove)) }
            }
        }
    )
}

/**
 * One remembered link. The row searches it, holding it asks to forget it, and the arrow puts
 * it in the field for editing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    query: String,
    onUse: () -> Unit,
    onFill: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onUse, onLongClick = onRemove)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        FillArrow(onFill)
    }
}

/** The arrow at a row's end, pointing up at the field it fills. */
@Composable
private fun FillArrow(onFill: () -> Unit) {
    IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.CallMade,
            contentDescription = stringResource(R.string.search_edit_before),
            modifier = Modifier
                .size(22.dp)
                .rotate(-90f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** A completion offered while typing. The row searches it; the arrow puts it in the field. */
@Composable
private fun SuggestionRow(query: String, onUse: () -> Unit, onFill: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUse)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        FillArrow(onFill)
    }
}

/**
 * Whether [text] is one or more links rather than words to search for: every part of it
 * is an address, with or without its scheme.
 */
internal fun isLinks(text: String): Boolean {
    val parts = text.trim().split(WHITESPACE).filter { it.isNotBlank() }
    return parts.isNotEmpty() && parts.all { part ->
        part.startsWith("http://", ignoreCase = true) ||
            part.startsWith("https://", ignoreCase = true) ||
            part.startsWith("www.", ignoreCase = true) ||
            BARE_ADDRESS.matches(part)
    }
}

/** A host with a dot in it and an optional path, such as youtu.be/abc. */
private val BARE_ADDRESS = Regex("""^[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)*\.[\p{L}]{2,}(:\d+)?([/?#]\S*)?$""")

private const val SUGGESTION_DELAY_MS = 250L

/** The clear button inside the field: smaller than the usual 48 dp, so it sits close. */
private val FIELD_ACTION_SIZE = 36.dp

/** How tall the list of queued links grows before it scrolls, so history stays in view. */
private val QUEUE_MAX_HEIGHT = 236.dp

/** Any run of whitespace, which is what separates one pasted link from the next. */
private val WHITESPACE = Regex("""\s+""")
