package com.hazel.android.ui.screens.download

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazel.android.MainActivity
import com.hazel.android.R
import com.hazel.android.data.AccountState
import com.hazel.android.data.AccountStatus
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SignInCheck
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.ui.screens.cookies.AccountTitle
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.cookies.SignInBrowser
import com.hazel.android.ui.screens.cookies.SiteMark
import com.hazel.android.ui.screens.cookies.SplitButtons
import com.hazel.android.ui.screens.cookies.accountLine
import com.hazel.android.ui.screens.more.ConnectedRows
import com.hazel.android.ui.theme.tonalFill
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.launch

/**
 * Whether saved sign-ins are passed to the downloader, read where a download is set up.
 *
 * The same switch as the one on the Cookies screen, so turning it on here turns it on
 * everywhere, the way the incognito button beside it works.
 */
@Composable
fun rememberUseCookies(): Boolean {
    val context = LocalContext.current
    val flow = remember(context) { CookieRepository.getUseCookies(context) }
    val useCookies by flow.collectAsState(initial = lastUseCookies)
    lastUseCookies = useCookies
    return useCookies
}

/** The last value read, so a sheet opened again shows it at once instead of off for a frame. */
@Volatile private var lastUseCookies = false

/**
 * The cookies option of a download sheet: the sign-in saved for the link's site with its
 * state, the app's "Use cookies" switch, a way to the Cookies screen, and the action the
 * state calls for. [url] is a link from the site in question; blank where there is none.
 */
@Composable
fun CookiesDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useCookies = rememberUseCookies()
    val entries by remember(context) { CookieRepository.getEntries(context) }.collectAsState(initial = emptyList())
    val siteRoot = remember(url) { url.takeIf { it.isNotBlank() }?.let(::siteRootOf) }
    val site = remember(siteRoot) { siteRoot?.let(CookieRepository::siteOfUrl) }
    val entry = remember(entries, url) { if (url.isBlank()) null else CookieRepository.entryFor(entries, url) }
    var checking by remember { mutableStateOf(false) }

    CookiesDialogContent(
        siteName = entry?.title?.takeIf { it.isNotBlank() } ?: SignInBrowser.nameOf(site),
        site = site,
        entry = entry,
        useCookies = useCookies,
        checking = checking,
        onUseCookies = { on -> scope.launch { CookieRepository.setUseCookies(context, on) } },
        onOpenSettings = {
            openCookiesScreen(context)
            onDismiss()
        },
        onSignIn = {
            val target = entry?.url?.takeIf { it.isNotBlank() } ?: siteRoot
            if (target != null) {
                context.startActivity(CookieWebViewActivity.intent(context, target, entry?.title ?: SignInBrowser.nameOf(site).orEmpty()))
            }
            onDismiss()
        },
        onCheck = {
            val saved = entry ?: return@CookiesDialogContent
            checking = true
            scope.launch {
                SignInCheck.run(context, saved)
                    .onSuccess { refused -> CookieRepository.markChecked(context, saved.id, refused) }
                    .onFailure { Toast.makeText(context, R.string.cookies_check_failed, Toast.LENGTH_SHORT).show() }
                checking = false
            }
        },
        onDismiss = onDismiss
    )
}

/** The dialog itself, apart from the store, so it can be drawn on its own. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CookiesDialogContent(
    siteName: String?,
    site: String?,
    entry: CookieEntry?,
    useCookies: Boolean,
    checking: Boolean,
    onUseCookies: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val status = remember(entry) { entry?.let(CookieRepository::statusOf) ?: AccountStatus(AccountState.SIGNED_OUT, null) }
    val expired = status.state == AccountState.EXPIRED
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(if (expired) scheme.error.copy(alpha = 0.18f) else scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Cookie, contentDescription = null, tint = if (expired) scheme.error else scheme.primary)
            }
        },
        title = {
            Text(
                stringResource(if (expired) R.string.cookies_dialog_expired_title else R.string.cookies_dialog_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.cookies_dialog_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = scheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                val rows = buildList<@Composable () -> Unit> {
                    if (siteName != null) add {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            SiteMark(site = site, name = siteName)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                AccountTitle(name = siteName, state = status.state, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    accountLine(status, entry),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    add {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Text(stringResource(R.string.cookies_use_title), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(checked = useCookies, onCheckedChange = onUseCookies)
                        }
                    }
                }
                ConnectedRows(rows, color = tonalFill(0.5f))
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(20.dp),
                    color = tonalFill(0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(MaterialShapes.Cookie6Sided.toShape())
                                .background(scheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.cookies_settings), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                when {
                    siteName == null -> SplitButtons(
                        startLabel = stringResource(R.string.cookies_not_now), onStart = onDismiss,
                        endLabel = stringResource(R.string.cookies_done), onEnd = onDismiss
                    )
                    status.state == AccountState.ACTIVE -> SplitButtons(
                        startLabel = stringResource(R.string.cookies_check_button), onStart = onCheck, startEnabled = !checking,
                        endLabel = stringResource(R.string.cookies_done), onEnd = onDismiss
                    )
                    else -> SplitButtons(
                        startLabel = stringResource(R.string.cookies_not_now), onStart = onDismiss,
                        endLabel = stringResource(if (expired) R.string.cookies_sign_in_again else R.string.no_results_sign_in),
                        onEnd = onSignIn
                    )
                }
            }
        },
        confirmButton = {}
    )
}

/** Opens the Cookies screen through the app's own deep route, from any screen or overlay. */
private fun openCookiesScreen(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_NAVIGATE_TO, "cookies")
        )
    }
}
