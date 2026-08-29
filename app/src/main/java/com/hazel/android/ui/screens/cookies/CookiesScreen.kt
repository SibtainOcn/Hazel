package com.hazel.android.ui.screens.cookies

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import kotlinx.coroutines.launch

/**
 * Manages the cookie sets yt-dlp uses to reach content that needs a signed-in session.
 *
 * One set is stored per site. A set is captured by signing in through
 * [CookieWebViewActivity], stays until it is deleted, and is reused by every download, so
 * signing in once covers everything from that site. Signing in again from an existing entry
 * replaces its contents in place, which is how an expired session is refreshed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val useCookies by CookieRepository.getUseCookies(context).collectAsState(initial = false)
    val entries by CookieRepository.getEntries(context).collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<CookieEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CookieEntry?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // The list refreshes on its own through the flow, so nothing has to be done with the
    // result beyond letting the sign-in screen close.
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Cookies",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import from clipboard") },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                val text = readClipboard(context)
                                val imported = CookieRepository.importText(
                                    context, text, "Imported cookies"
                                )
                                toast(
                                    context,
                                    if (imported) "Cookies imported"
                                    else "Clipboard holds no cookie data"
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export to clipboard") },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                val text = CookieRepository.exportText(context)
                                if (text.isBlank()) {
                                    toast(context, "Nothing to export")
                                } else {
                                    writeClipboard(context, text)
                                    toast(context, "Copied to clipboard")
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete all") },
                        onClick = {
                            menuOpen = false
                            confirmDeleteAll = true
                        }
                    )
                }
            }
        }

        // ── Master switch ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier
                    .clickable {
                        scope.launch { CookieRepository.setUseCookies(context, !useCookies) }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use cookies", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pass saved sign-ins to the downloader",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Switch(
                    checked = useCookies,
                    onCheckedChange = { enabled ->
                        scope.launch { CookieRepository.setUseCookies(context, enabled) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { creating = true },
            enabled = useCookies,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("New cookie")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Cookie,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No cookies saved",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Add one to download age-restricted, private, or members-only media.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    CookieRow(
                        entry = entry,
                        onClick = { editing = entry },
                        onLongClick = { pendingDelete = entry },
                        onEnabledChange = { enabled ->
                            scope.launch {
                                CookieRepository.setEnabled(context, entry.id, enabled)
                            }
                        }
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        CookieEditSheet(
            entry = editing,
            onSave = { updated ->
                scope.launch { CookieRepository.upsert(context, updated) }
                editing = null
                creating = false
            },
            onSignIn = { url, title ->
                editing = null
                creating = false
                signInLauncher.launch(CookieWebViewActivity.intent(context, url, title))
            },
            onCopy = { entry ->
                writeClipboard(context, CookieRepository.FILE_HEADER + "\n" + entry.content)
                toast(context, "Copied to clipboard")
            },
            onDelete = { entry ->
                editing = null
                creating = false
                pendingDelete = entry
            },
            onDismiss = {
                editing = null
                creating = false
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete cookies", fontWeight = FontWeight.Bold) },
            text = { Text("Remove the saved sign-in for ${entry.url.ifBlank { entry.title }}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { CookieRepository.delete(context, entry.id) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all cookies", fontWeight = FontWeight.Bold) },
            text = { Text("Every saved sign-in will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { CookieRepository.deleteAll(context) }
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CookieRow(
    entry: CookieEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.url.ifBlank { entry.title.ifBlank { "Cookies" } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.title.isNotBlank() && entry.url.isNotBlank()) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    entry.preview(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = entry.enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/**
 * Adds a cookie set or edits an existing one.
 *
 * For a new entry only the address is needed, and the action opens the sign-in page. For an
 * existing entry the stored cookies are shown, the label and address can be corrected
 * without touching the cookies themselves, and the action re-runs the sign-in to replace
 * them, which is how a set that has stopped working is refreshed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookieEditSheet(
    entry: CookieEntry?,
    onSave: (CookieEntry) -> Unit,
    onSignIn: (url: String, title: String) -> Unit,
    onCopy: (CookieEntry) -> Unit,
    onDelete: (CookieEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var url by remember { mutableStateOf(entry?.url?.ifBlank { "https://" } ?: "https://") }
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }

    val urlValid = url.startsWith("http://") || url.startsWith("https://")
    val hasHost = urlValid && url.removePrefix("https://").removePrefix("http://").isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            if (entry != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cookies",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onCopy(entry) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy cookies")
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete cookies",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                isError = url.isNotBlank() && !urlValid,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry != null) {
                    OutlinedButton(
                        onClick = {
                            onSave(entry.copy(url = url.trim(), title = title.trim()))
                        }
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    onClick = { onSignIn(url.trim(), title.trim()) },
                    enabled = hasHost,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (hasHost) 0.15f else 0.06f
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Cookie,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (entry == null) "Get cookies" else "Update",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString().orEmpty()
}

private fun writeClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Hazel cookies", text))
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
