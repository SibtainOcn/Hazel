package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
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
    val useCookies by flow.collectAsState(initial = false)
    return useCookies
}

/**
 * The cookies option of a download sheet.
 *
 * Holds the app's own "Use cookies" switch, says whether a sign-in is saved for the site the
 * link is on, and offers to sign in to it. Signing in opens the same sign-in page the "Sign
 * in" answer to a failed read opens, which saves the site's cookies and turns them on.
 * [url] is a link from the site in question; blank where the sheet has none to offer.
 */
@Composable
fun CookiesDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useCookies = rememberUseCookies()
    val entriesFlow = remember(context) { CookieRepository.getEntries(context) }
    val entries by entriesFlow.collectAsState(initial = emptyList())

    val siteRoot = remember(url) { url.takeIf { it.isNotBlank() }?.let(::siteRootOf) }
    val host = remember(siteRoot) { siteRoot?.let(::hostOf) }
    val signedIn = host != null && entries.any { it.enabled && hostOf(it.url).endsWith(host) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Cookie, contentDescription = null) },
        title = { Text(stringResource(R.string.cookies_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ToggleRow(
                    label = stringResource(R.string.cookies_use_title),
                    checked = useCookies,
                    onCheckedChange = { enabled ->
                        scope.launch { CookieRepository.setUseCookies(context, enabled) }
                    }
                )
                Text(
                    stringResource(R.string.cookies_use_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (host != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        if (signedIn) stringResource(R.string.sheet_cookies_site_saved, host)
                        else stringResource(R.string.sheet_cookies_site_missing, host),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (signedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            if (siteRoot != null) {
                FlatChip(
                    label = stringResource(R.string.no_results_sign_in),
                    selected = true,
                    onClick = {
                        context.startActivity(CookieWebViewActivity.intent(context, siteRoot))
                        onDismiss()
                    }
                )
            }
        },
        dismissButton = {
            FlatChip(label = stringResource(R.string.options_chapters_dismiss), onClick = onDismiss)
        }
    )
}

/** The host of an address without its "www.", for matching a link to a saved sign-in. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        .removePrefix("www.")
        .lowercase()
