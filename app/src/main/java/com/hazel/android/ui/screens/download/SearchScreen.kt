package com.hazel.android.ui.screens.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.util.LinkKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full screen link entry.
 *
 * More than one link can be queued before anything is read: typing a link and confirming it
 * with the plus button parks it as a chip and clears the field for the next one, so a batch
 * is built up in one pass. Searching resolves everything that was queued, including whatever
 * is still in the field.
 *
 * Previously used links sit underneath. Tapping one queues it; the arrow beside it puts it
 * back in the field instead, for when it needs editing first.
 */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onSearch: (List<String>) -> Unit,
    onClearResults: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val history by SearchHistoryRepository.getHistory(context).collectAsState(initial = emptyList())

    var text by remember { mutableStateOf(initialQuery) }
    var queued by remember { mutableStateOf(listOf<String>()) }

    // Everything already downloaded, so a repeat can be raised before the link is read.
    val downloaded by DownloadHistoryRepository.getHistory(context)
        .collectAsState(initial = emptyList())

    // The links waiting on an answer, paired with the copy already on the device.
    var pendingDuplicate by remember {
        mutableStateOf<Pair<List<String>, HistoryEntry>?>(null)
    }

    var menuOpen by remember { mutableStateOf(false) }

    // Links already queued are hidden from the list, and what is being typed filters it.
    val suggestions = remember(history, text, queued) {
        history.filter { it !in queued && it.contains(text.trim(), ignoreCase = true) }
    }

    fun queueCurrent() {
        val entry = text.trim()
        if (entry.isNotBlank() && entry !in queued) queued = queued + entry
        text = ""
    }

    fun startSearch(links: List<String>) {
        keyboard?.hide()
        scope.launch {
            if (!SettingsRepository.getIncognito(context).first()) {
                links.forEach { SearchHistoryRepository.record(context, it) }
            }
        }
        onSearch(links)
    }

    fun submit() {
        val all = (queued + text.trim()).filter { it.isNotBlank() }.distinct()
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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Shown as a dialog rather than as screen content so it covers the app bar and the
    // navigation bar too. Link entry takes over the whole screen while it is open.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = true) { onDismiss() }

        pendingDuplicate?.let { (links, existing) ->
            AlreadyDownloadedDialog(
                entry = existing,
                onDownloadAgain = {
                    pendingDuplicate = null
                    startSearch(links)
                },
                onDismiss = { pendingDuplicate = null }
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Input row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Search or insert URL") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() })
                )

                // With nothing typed there is nothing to add or clear, so the space the
                // those controls occupy carries the menu instead. It is also the only
                // moment the menu is worth offering: its actions clear things, and clearing
                // is not what someone half way through typing a link is after.
                if (text.isBlank()) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear results") },
                                onClick = {
                                    menuOpen = false
                                    onClearResults()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear search history") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { SearchHistoryRepository.clear(context) }
                                }
                            )
                        }
                    }
                }

                // Parks the typed link so another can be entered.
                if (text.isNotBlank()) {
                    IconButton(onClick = { queueCurrent() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add another link")
                    }
                }
                if (text.isNotBlank() || queued.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            text = ""
                            queued = emptyList()
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ── Queued links ──
            if (queued.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        queued.forEach { entry ->
                            InputChip(
                                selected = false,
                                onClick = { queued = queued - entry },
                                label = {
                                    Text(
                                        entry,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(InputChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }

                    IconButton(onClick = { submit() }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search all",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            // ── History ──
            if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        if (history.isEmpty()) "Links you use will appear here"
                        else "No matching links",
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
                            onRemove = {
                                scope.launch { SearchHistoryRepository.remove(context, entry) }
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}

/**
 * One remembered link. The row searches it, the arrow puts it in the field for editing, and
 * the cross forgets it.
 */
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
            .clickable(onClick = onUse)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Forget",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = onFill, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.CallMade,
                contentDescription = "Edit before searching",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
    Spacer(modifier = Modifier.height(0.dp))
}
