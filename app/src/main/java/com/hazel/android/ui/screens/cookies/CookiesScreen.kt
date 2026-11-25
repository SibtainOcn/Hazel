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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.AccountState
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SignInCheck
import com.hazel.android.ui.components.DropdownMenu
import com.hazel.android.ui.components.DropdownMenuItem
import com.hazel.android.ui.components.ExpressiveTextField
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.components.MenuTone
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.screens.more.ActionSettingRow
import com.hazel.android.ui.screens.more.ConfirmSettingDialog
import com.hazel.android.ui.screens.more.ConnectedRows
import com.hazel.android.ui.screens.more.SettingsSection
import com.hazel.android.ui.screens.more.SwitchSettingRow
import com.hazel.android.ui.theme.sheetButtonColor
import com.hazel.android.ui.theme.tonalFill
import com.hazel.android.util.CookieExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saved sign-ins as accounts, one per site, each with its state read from the site's own
 * login cookie. A set is captured through [CookieWebViewActivity] and reused by every
 * download from that site; signing in again replaces it in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val useCookies by remember(context) { CookieRepository.getUseCookies(context) }
        .collectAsState(initial = false)
    // Null until the store has answered, so the list never flashes empty first.
    val entries by remember(context) { CookieRepository.getEntries(context) }
        .collectAsState(initial = null)

    var openId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingSignOut by remember { mutableStateOf<CookieEntry?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    // The list follows the store, so the result only lets the sign-in page close.
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val signIn = { url: String, title: String -> signInLauncher.launch(CookieWebViewActivity.intent(context, url, title)) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    val (fileName, text) = withContext(Dispatchers.IO) {
                        (queryFileName(context, uri) ?: "cookies.txt") to
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.bufferedReader(Charsets.UTF_8).readText()
                            }.orEmpty()
                    }
                    val imported = text.isNotBlank() && CookieRepository.importText(context, text, fileName)
                    toast(context, resources.getString(if (imported) R.string.cookies_toast_imported else R.string.cookies_toast_file_no_data))
                } catch (_: Exception) {
                    toast(context, resources.getString(R.string.cookies_toast_import_failed))
                } finally {
                    importing = false
                }
            }
        }
    }

    CookiesBody(
        useCookies = useCookies,
        entries = entries,
        importing = importing,
        onBack = onBack,
        onUseCookies = { on -> scope.launch { CookieRepository.setUseCookies(context, on) } },
        onSignIn = { adding = true },
        onImportFile = { filePickerLauncher.launch(arrayOf("text/*", "*/*")) },
        onImportClipboard = {
            scope.launch {
                val imported = CookieRepository.importText(
                    context, readClipboard(context), resources.getString(R.string.cookies_imported_title)
                )
                toast(context, resources.getString(if (imported) R.string.cookies_toast_imported else R.string.cookies_toast_no_data))
            }
        },
        onExport = {
            scope.launch {
                val text = CookieRepository.exportText(context)
                if (text.isBlank()) {
                    toast(context, resources.getString(R.string.cookies_toast_nothing_to_export))
                } else {
                    writeClipboard(context, text)
                    toast(context, resources.getString(R.string.cookies_toast_exported))
                }
            }
        },
        onDeleteAll = { confirmDeleteAll = true },
        onOpen = { openId = it.id },
        onSignInAgain = { entry -> signIn(entry.url, entry.title) },
        onQuickSite = { name, home -> signIn(home, name) }
    )

    if (adding) {
        AddSignInSheet(
            onContinue = { url, title ->
                adding = false
                signIn(url, title)
            },
            onDismiss = { adding = false }
        )
    }

    entries?.firstOrNull { it.id == openId }?.let { entry ->
        AccountSheet(
            entry = entry,
            onSave = { updated ->
                if (updated != entry) scope.launch { CookieRepository.upsert(context, updated) }
                openId = null
            },
            onSignInAgain = { url, title ->
                openId = null
                signIn(url, title)
            },
            onCopy = {
                writeClipboard(context, CookieRepository.FILE_HEADER + "\n" + entry.content)
                toast(context, resources.getString(R.string.cookies_toast_entry_copied))
            },
            onEnabledChange = { on -> scope.launch { CookieRepository.setEnabled(context, entry.id, on) } },
            onSignOut = { pendingSignOut = entry },
            onDismiss = { openId = null }
        )
    }

    pendingSignOut?.let { entry ->
        ConfirmSettingDialog(
            title = stringResource(R.string.cookies_sign_out_title, displayName(entry)),
            body = stringResource(R.string.cookies_sign_out_body),
            confirm = stringResource(R.string.cookies_sign_out),
            onConfirm = {
                pendingSignOut = null
                openId = null
                CookieExtractor.clearCookies(CookieRepository.cookieKeys(entry))
                scope.launch { CookieRepository.delete(context, entry.id) }
            },
            onDismiss = { pendingSignOut = null }
        )
    }

    if (confirmDeleteAll) {
        ConfirmSettingDialog(
            title = stringResource(R.string.cookies_delete_all_title),
            body = stringResource(R.string.cookies_delete_all_body),
            confirm = stringResource(R.string.cookies_delete_all_confirm),
            onConfirm = {
                confirmDeleteAll = false
                CookieExtractor.clearAll()
                scope.launch { CookieRepository.deleteAll(context) }
            },
            onDismiss = { confirmDeleteAll = false }
        )
    }
}

/** The screen itself, apart from the store, so it can be drawn on its own. */
@Composable
internal fun CookiesBody(
    useCookies: Boolean,
    entries: List<CookieEntry>?,
    importing: Boolean,
    onBack: () -> Unit,
    onUseCookies: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onImportFile: () -> Unit,
    onImportClipboard: () -> Unit,
    onExport: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpen: (CookieEntry) -> Unit,
    onSignInAgain: (CookieEntry) -> Unit,
    onQuickSite: (name: String, home: String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.cookies_back))
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
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.cookies_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cookies_menu_export)) },
                        icon = Icons.Filled.IosShare,
                        tone = MenuTone.Info,
                        enabled = !entries.isNullOrEmpty(),
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cookies_menu_delete_all), color = MaterialTheme.colorScheme.error) },
                        icon = Icons.Filled.DeleteForever,
                        tone = MenuTone.Danger,
                        enabled = !entries.isNullOrEmpty(),
                        onClick = { menuOpen = false; onDeleteAll() }
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.cookies_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
            )

            Text(
                stringResource(R.string.cookies_section_general),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
            )
            ConnectedRows(
                listOf {
                    SwitchSettingRow(
                        icon = Icons.Filled.Cookie,
                        title = stringResource(R.string.cookies_use_title),
                        summary = stringResource(R.string.cookies_use_description),
                        checked = useCookies,
                        onCheckedChange = onUseCookies
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AddButtons(importing = importing, onSignIn = onSignIn, onImportFile = onImportFile, onImportClipboard = onImportClipboard)
            Spacer(modifier = Modifier.height(16.dp))

            val saved = entries
            if (saved == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    HazelLoadingIndicator(size = 40.dp)
                }
            } else {
                if (saved.isNotEmpty()) {
                    if (!useCookies) {
                        Text(
                            stringResource(R.string.cookies_off_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    SettingsSection(
                        title = stringResource(R.string.cookies_section_accounts),
                        rows = saved.map { entry ->
                            { AccountCard(entry, dimmed = !useCookies || !entry.enabled, onClick = { onOpen(entry) }, onSignInAgain = { onSignInAgain(entry) }) }
                        }
                    )
                }
                val unsaved = SignInBrowser.QUICK_SITES.filter { (_, home) -> CookieRepository.entryFor(saved, home) == null }
                if (unsaved.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cookies_section_quick),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
                    )
                    QuickSiteChips(sites = unsaved, onPick = onQuickSite)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Sign in (filled) and Import (tonal, file or clipboard) as one connected button group. */
@Composable
private fun AddButtons(importing: Boolean, onSignIn: () -> Unit, onImportFile: () -> Unit, onImportClipboard: () -> Unit) {
    var importMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        GroupButton(
            label = stringResource(R.string.cookies_add_sign_in),
            icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp)) },
            filled = true,
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            onClick = onSignIn,
            modifier = Modifier.weight(1.4f)
        )
        Box(modifier = Modifier.weight(1f)) {
            GroupButton(
                label = stringResource(R.string.cookies_import),
                icon = {
                    if (importing) HazelLoadingIndicator(size = 18.dp)
                    else Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                filled = false,
                enabled = !importing,
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                onClick = { importMenu = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cookies_menu_import_file)) },
                    icon = Icons.Filled.UploadFile,
                    tone = MenuTone.Accent,
                    onClick = { importMenu = false; onImportFile() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cookies_menu_import)) },
                    icon = Icons.Filled.ContentPaste,
                    tone = MenuTone.Info,
                    onClick = { importMenu = false; onImportClipboard() }
                )
            }
        }
    }
}

@Composable
private fun GroupButton(
    label: String,
    icon: @Composable () -> Unit,
    filled: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (filled) scheme.onPrimary else scheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (filled) scheme.primary else sheetButtonColor,
        contentColor = ink,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One account: the site mark, the name with its state, and one line on its expiry. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountCard(entry: CookieEntry, dimmed: Boolean, onClick: () -> Unit, onSignInAgain: () -> Unit) {
    val status = remember(entry) { CookieRepository.statusOf(entry) }
    val site = remember(entry) { CookieRepository.siteOf(entry) }
    val name = displayName(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiteMark(site = site, name = name, dimmed = dimmed)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            AccountTitle(name = name, state = status.state, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                accountLine(status, entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (status.state == AccountState.EXPIRED) {
            Spacer(modifier = Modifier.width(10.dp))
            val (fill, ink) = stateColors(AccountState.EXPIRED)
            val label = stringResource(R.string.cookies_sign_in_again)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(fill)
                    .clickable(role = Role.Button, onClick = onSignInAgain)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun displayName(entry: CookieEntry): String {
    val site = remember(entry) { SignInBrowser.nameOf(CookieRepository.siteOf(entry)) ?: CookieRepository.summaryOf(entry).site }
    return entry.title.ifBlank { site.ifBlank { stringResource(R.string.cookies_row_fallback_name) } }
}

/** Common sites as one-tap sign-ins, so signing in never has to wait for a failed download. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickSiteChips(
    sites: List<Pair<String, String>> = SignInBrowser.QUICK_SITES,
    onPick: (name: String, home: String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        sites.forEach { (name, home) ->
            Surface(onClick = { onPick(name, home) }, shape = CircleShape, color = tonalFill(0.6f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    SiteMark(site = CookieRepository.siteOfUrl(home), name = name, size = 26.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** Takes a site address, or one tap on a common site, and opens its sign-in page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSignInSheet(onContinue: (String, String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var url by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val ready = hasHost(withScheme(url))
    val submit = { if (ready) onContinue(withScheme(url), "") }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.cookies_add_sign_in), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.cookies_add_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            QuickSiteChips(onPick = { name, home -> onContinue(home, name) })
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.cookies_field_url)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = submit,
                enabled = ready,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cookies_add_continue), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * One account: name and address can be corrected without touching the cookies, the sign-in
 * can be checked with the site, refreshed, looked at, switched off for downloads or signed out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    entry: CookieEntry,
    onSave: (CookieEntry) -> Unit,
    onSignInAgain: (url: String, title: String) -> Unit,
    onCopy: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val summary = remember(entry) { CookieRepository.summaryOf(entry) }
    val status = remember(entry) { CookieRepository.statusOf(entry) }
    val site = remember(entry) { CookieRepository.siteOf(entry) }
    var title by remember { mutableStateOf(entry.title) }
    var url by remember { mutableStateOf(entry.url) }
    var showCookies by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val name = title.ifBlank { SignInBrowser.nameOf(site) ?: summary.site.ifBlank { stringResource(R.string.cookies_row_fallback_name) } }
    val now = System.currentTimeMillis()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteMark(site = site, name = name, size = 52.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            summary.site.takeIf { it.isNotBlank() },
                            pluralStringResource(R.plurals.cookies_count, summary.count, summary.count)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                StatePill(status.state)
            }

            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.cookies_field_title)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExpressiveTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.cookies_field_url)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))

            val expiresMs = status.expiresAt?.times(1000)
            val checkSummary = when {
                entry.checkedAt <= 0 -> stringResource(R.string.cookies_check_never)
                entry.refused -> stringResource(R.string.cookies_check_refused, relative(entry.checkedAt, now))
                else -> stringResource(R.string.cookies_check_ok, relative(entry.checkedAt, now))
            }
            ConnectedRows(
                listOf(
                    {
                        ActionSettingRow(
                            icon = Icons.Filled.Schedule,
                            title = stringResource(R.string.cookies_login_expires),
                            summary = when {
                                status.state == AccountState.SIGNED_OUT -> stringResource(R.string.cookies_no_login)
                                expiresMs == null -> stringResource(R.string.cookies_session_only)
                                else -> DateUtils.formatDateTime(context, expiresMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) +
                                    " · " + relative(expiresMs, now)
                            },
                            onClick = {}
                        )
                    },
                    {
                        ActionSettingRow(
                            icon = Icons.Filled.VerifiedUser,
                            title = stringResource(R.string.cookies_check),
                            summary = checkSummary,
                            enabled = !checking,
                            onClick = {},
                            trailing = {
                                if (checking) {
                                    HazelLoadingIndicator(size = 28.dp)
                                } else {
                                    FilledTonalButton(onClick = {
                                        checking = true
                                        scope.launch {
                                            SignInCheck.run(context, entry)
                                                .onSuccess { refused -> CookieRepository.markChecked(context, entry.id, refused) }
                                                .onFailure { toast(context, resources.getString(R.string.cookies_check_failed)) }
                                            checking = false
                                        }
                                    }) { Text(stringResource(R.string.cookies_check_button)) }
                                }
                            }
                        )
                    },
                    {
                        ActionSettingRow(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.cookies_sign_in_again),
                            summary = stringResource(R.string.cookies_sign_in_again_summary),
                            enabled = hasHost(withScheme(url)),
                            onClick = { onSignInAgain(withScheme(url), title.trim()) }
                        )
                    },
                    {
                        Column {
                            ActionSettingRow(
                                icon = if (showCookies) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                title = stringResource(if (showCookies) R.string.cookies_hide else R.string.cookies_show),
                                summary = stringResource(R.string.cookies_show_summary),
                                onClick = { showCookies = !showCookies },
                                trailing = {
                                    IconButton(onClick = onCopy) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cookies_sheet_copy), modifier = Modifier.size(20.dp))
                                    }
                                }
                            )
                            AnimatedVisibility(visible = showCookies, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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
                    },
                    {
                        SwitchSettingRow(
                            icon = Icons.Filled.Download,
                            title = stringResource(R.string.cookies_use_for_downloads),
                            summary = stringResource(R.string.cookies_use_for_downloads_summary),
                            checked = entry.enabled,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            SplitButtons(
                startLabel = stringResource(R.string.cookies_sign_out),
                onStart = onSignOut,
                startDanger = true,
                endLabel = stringResource(R.string.cookies_sheet_save),
                onEnd = {
                    onSave(entry.copy(url = url.trim().let { if (it.isBlank()) it else withScheme(it) }, title = title.trim()))
                }
            )
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
    return clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
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
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
