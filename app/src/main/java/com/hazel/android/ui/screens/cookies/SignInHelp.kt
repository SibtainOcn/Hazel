package com.hazel.android.ui.screens.cookies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.TravelExplore
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.theme.accentInk
import com.hazel.android.ui.theme.accentTint

/** Firefox's page for Cookie-Editor, an add-on that exports a site's cookies, on Android too. */
private const val COOKIE_EDITOR_URL = "https://addons.mozilla.org/firefox/addon/cookie-editor/"

/**
 * On top of Cookies: one-tap sign-in for common sites, and what to do when a sign-in page does
 * not work inside Hazel. Sites already signed in stay, marked, so another account can be added.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickSignInCard(signedIn: (home: String) -> Boolean, onPick: (name: String, home: String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(28.dp), color = accentTint(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)) {
                CookieBadge(Icons.AutoMirrored.Filled.Login)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        stringResource(R.string.cookies_section_quick),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentInk()
                    )
                    Text(
                        stringResource(R.string.cookies_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            QuickSiteChips(signedIn = signedIn, onPick = onPick)
            Surface(
                onClick = { helpOpen = true },
                shape = RoundedCornerShape(20.dp),
                color = scheme.background.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = accentInk(), modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.cookies_help_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
    if (helpOpen) SignInHelpDialog(onDismiss = { helpOpen = false })
}

/** The same help from the sign-in page's menu, where the page is not working. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SignInHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        icon = { CookieBadge(Icons.Filled.TravelExplore) },
        title = {
            Text(
                stringResource(R.string.cookies_help_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.cookies_help_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HelpSteps()
            }
        },
        confirmButton = {
            TextButton(onClick = { openCookieEditor(context) }) {
                Text(stringResource(R.string.cookies_help_get_addon), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.no_results_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HelpSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.cookies_help_step_browser, R.string.cookies_help_step_export, R.string.cookies_help_step_import)
            .forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        stringResource(step),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
    }
}

private fun openCookieEditor(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COOKIE_EDITOR_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.cookies_help_no_browser, Toast.LENGTH_SHORT).show()
    }
}
