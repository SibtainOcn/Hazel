package com.hazel.android.ui.screens.cookies

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.CookieSummary
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.screens.more.ActionSettingRow
import com.hazel.android.ui.screens.more.ConfirmSettingDialog
import com.hazel.android.ui.screens.more.SettingsSection
import com.hazel.android.ui.screens.more.SwitchSettingRow
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val useCookies by remember(context) { CookieRepository.getUseCookies(context) }
        .collectAsState(initial = false)
    // Null until the store has answered, so the empty state never flashes before the list.
    val entries by remember(context) { CookieRepository.getEntries(context) }
        .collectAsState(initial = null)

    var editing by remember { mutableStateOf<CookieEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CookieEntry?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    // The list refreshes on its own through the flow, so nothing has to be done with the
    // result beyond letting the sign-in screen close.
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    val fileName = queryFileName(context, uri) ?: "cookies.txt"
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                    val imported = text.isNotBlank() &&
                        CookieRepository.importText(context, text, fileName)
                    toast(
                        context,
                        resources.getString(
                            if (imported) R.string.cookies_toast_imported
                            else R.string.cookies_toast_file_no_data
                        )
                    )
                } catch (_: Exception) {
                    toast(context, resources.getString(R.string.cookies_toast_import_failed))
                } finally {
                    importing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cookies_back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.cookies_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cookies_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cookies_menu_import)) },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                val imported = CookieRepository.importText(
                                    context, readClipboard(context),
                                    resources.getString(R.string.cookies_imported_title)
                                )
                                toast(
                                    context,
                                    resources.getString(
                                        if (imported) R.string.cookies_toast_imported
                                        else R.string.cookies_toast_no_data
                                    )
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cookies_menu_export)) },
                        enabled = !entries.isNullOrEmpty(),
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                val text = CookieRepository.exportText(context)
                                if (text.isBlank()) {
                                    toast(context, resources.getString(R.string.cookies_toast_nothing_to_export))
                                } else {
                                    writeClipboard(context, text)
                                    toast(context, resources.getString(R.string.cookies_toast_exported))
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.cookies_menu_delete_all),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        enabled = !entries.isNullOrEmpty(),
                        onClick = {
                            menuOpen = false
                            confirmDeleteAll = true
                        }
                    )
                }
            }
        }

        Text(
            stringResource(R.string.cookies_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        SettingsSection(
            title = stringResource(R.string.cookies_section_general),
            rows = listOf {
                SwitchSettingRow(
                    icon = Icons.Filled.Cookie,
                    title = stringResource(R.string.cookies_use_title),
                    summary = stringResource(R.string.cookies_use_description),
                    checked = useCookies,
                    onCheckedChange = { enabled ->
                        scope.launch { CookieRepository.setUseCookies(context, enabled) }
                    }
                )
            }
        )

        SettingsSection(
            title = stringResource(R.string.cookies_section_add),
            rows = listOf(
                {
                    ActionSettingRow(
                        icon = Icons.AutoMirrored.Filled.Login,
                        title = stringResource(R.string.cookies_add_sign_in),
                        summary = stringResource(R.string.cookies_add_sign_in_summary),
                        onClick = { adding = true }
                    )
                },
                {
                    ActionSettingRow(
                        icon = Icons.Filled.UploadFile,
                        title = stringResource(R.string.cookies_menu_import_file),
                        summary = stringResource(R.string.cookies_add_import_file_summary),
                        enabled = !importing,
                        onClick = { filePickerLauncher.launch(arrayOf("text/*", "*/*")) },
                        trailing = if (importing) {
                            { HazelLoadingIndicator(size = 28.dp) }
                        } else null
                    )
                }
            )
        )

        val saved = entries
        when {
            saved == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) { HazelLoadingIndicator(size = 40.dp) }

            saved.isEmpty() -> EmptyCookies()

            else -> {
                if (!useCookies) {
                    Text(
                        stringResource(R.string.cookies_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
                SettingsSection(
                    title = stringResource(R.string.cookies_section_saved),
                    rows = saved.map { entry ->
                        {
                            CookieEntryRow(
                                entry = entry,
                                active = useCookies,
                                onClick = { editing = entry },
                                onEnabledChange = { enabled ->
                                    scope.launch { CookieRepository.setEnabled(context, entry.id, enabled) }
                                }
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (adding) {
        AddSignInSheet(
            onContinue = { url ->
                adding = false
                signInLauncher.launch(CookieWebViewActivity.intent(context, url, ""))
            },
            onDismiss = { adding = false }
        )
    }

    editing?.let { entry ->
        CookieDetailSheet(
            entry = entry,
            onSave = { updated ->
                scope.launch { CookieRepository.upsert(context, updated) }
                editing = null
            },
            onSignInAgain = { url, title ->
                editing = null
                signInLauncher.launch(CookieWebViewActivity.intent(context, url, title))
            },
            onCopy = {
                writeClipboard(context, CookieRepository.FILE_HEADER + "\n" + entry.content)
                toast(context, resources.getString(R.string.cookies_toast_entry_copied))
            },
            onDelete = {
                editing = null
                pendingDelete = entry
            },
            onDismiss = { editing = null }
        )
    }

    pendingDelete?.let { entry ->
        ConfirmSettingDialog(
            title = stringResource(R.string.cookies_delete_title),
            body = stringResource(
                R.string.cookies_delete_body,
                entry.title.ifBlank { CookieRepository.summaryOf(entry).site.ifBlank { entry.url } }
            ),
            confirm = stringResource(R.string.cookies_delete_confirm),
            onConfirm = {
                pendingDelete = null
                scope.launch { CookieRepository.delete(context, entry.id) }
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (confirmDeleteAll) {
        ConfirmSettingDialog(
            title = stringResource(R.string.cookies_delete_all_title),
            body = stringResource(R.string.cookies_delete_all_body),
            confirm = stringResource(R.string.cookies_delete_all_confirm),
            onConfirm = {
                confirmDeleteAll = false
                scope.launch { CookieRepository.deleteAll(context) }
            },
            onDismiss = { confirmDeleteAll = false }
        )
    }
}

@Composable
private fun EmptyCookies() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Cookie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                stringResource(R.string.cookies_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.cookies_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** One saved sign-in: the site, how many cookies it holds and when they run out. */
@Composable
private fun CookieEntryRow(
    entry: CookieEntry,
    active: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val summary = remember(entry.content, entry.url) { CookieRepository.summaryOf(entry) }
    val name = entry.title.ifBlank { summary.site.ifBlank { stringResource(R.string.cookies_row_fallback_name) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiteBadge(
            site = summary.site.ifBlank { name },
            modifier = Modifier.alpha(if (active && entry.enabled) 1f else 0.5f)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CookieMeta(summary = summary, showSite = name != summary.site)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = entry.enabled,
            onCheckedChange = onEnabledChange,
            enabled = active
        )
    }
}

/** A round tile with the site's first letter, standing in for an icon the app does not fetch. */
@Composable
private fun SiteBadge(site: String, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            site.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = if (size > 40) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** The site, the cookie count and the expiry, in one line; an expired set says so in red. */
@Composable
private fun CookieMeta(summary: CookieSummary, showSite: Boolean) {
    val now = System.currentTimeMillis()
    val expiresAtMs = summary.expiresAt?.times(1000)
    val expired = expiresAtMs != null && expiresAtMs < now
    val expiry = when {
        expiresAtMs == null -> stringResource(R.string.cookies_session_only)
        expired -> stringResource(R.string.cookies_expired)
        else -> stringResource(
            R.string.cookies_expires,
            DateUtils.getRelativeTimeSpanString(expiresAtMs, now, DateUtils.DAY_IN_MILLIS).toString()
        )
    }
    val parts = buildList {
        if (showSite && summary.site.isNotBlank()) add(summary.site)
        add(pluralStringResource(R.plurals.cookies_count, summary.count, summary.count))
    }
    Row {
        Text(
            parts.joinToString(" · ") + " · ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            expiry,
            style = MaterialTheme.typography.bodySmall,
            color = if (expired) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1
        )
    }
}

/** Takes a site address and opens its sign-in page; nothing else is asked for up front. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSignInSheet(onContinue: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val ready = hasHost(withScheme(url))
    val submit = { if (ready) onContinue(withScheme(url)) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                stringResource(R.string.cookies_add_sign_in),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.cookies_add_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.cookies_field_url)) },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = submit,
                enabled = ready,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cookies_add_continue), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A saved sign-in: its name and address can be corrected without touching the cookies, the
 * cookies can be looked at or copied, and signing in again replaces them, which is how a set
 * that has stopped working is refreshed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookieDetailSheet(
    entry: CookieEntry,
    onSave: (CookieEntry) -> Unit,
    onSignInAgain: (url: String, title: String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = remember(entry) { CookieRepository.summaryOf(entry) }
    var title by remember { mutableStateOf(entry.title) }
    var url by remember { mutableStateOf(entry.url) }
    var showCookies by remember { mutableStateOf(false) }
    val changed = title.trim() != entry.title || url.trim() != entry.url
    val name = title.ifBlank { summary.site.ifBlank { stringResource(R.string.cookies_row_fallback_name) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteBadge(site = summary.site.ifBlank { name }, size = 48)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CookieMeta(summary = summary, showSite = name != summary.site)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.cookies_field_title)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.cookies_field_url)) },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCookies = !showCookies }
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (showCookies) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(if (showCookies) R.string.cookies_hide else R.string.cookies_show),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onCopy) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.cookies_sheet_copy),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showCookies,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .keepFlingInSheet()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSignInAgain(withScheme(url), title.trim()) },
                enabled = hasHost(withScheme(url)),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cookies_sign_in_again), fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.cookies_sheet_delete), color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        onSave(entry.copy(url = url.trim().let { if (it.isBlank()) it else withScheme(it) }, title = title.trim()))
                    },
                    enabled = changed
                ) {
                    Text(stringResource(R.string.cookies_sheet_save))
                }
            }
        }
    }
}

private fun isWebAddress(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

/** A bare `example.com` is taken as https. */
private fun withScheme(url: String): String =
    url.trim().let { if (it.isBlank() || "://" in it) it else "https://$it" }

private fun hasHost(url: String): Boolean =
    isWebAddress(url) && url.trim().removePrefix("https://").removePrefix("http://").isNotBlank()

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

private fun queryFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
