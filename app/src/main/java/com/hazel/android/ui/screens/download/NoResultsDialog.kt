package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What the user sees when a link could not be read.
 *
 * The full text yt-dlp returned is shown rather than a summary, because it usually names
 * the exact reason and is what a bug report needs. When the reason is a missing sign-in the
 * dialog offers to collect cookies for the site, which is the one action that can actually
 * fix it. Otherwise it offers to go ahead anyway, since yt-dlp can often download a link
 * whose metadata request failed.
 */
@Composable
fun NoResultsDialog(
    message: String,
    canFetchCookies: Boolean,
    canContinue: Boolean,
    onCopyLog: () -> Unit,
    onGetCookies: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No results", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onCopyLog) { Text("Copy log") } },
        dismissButton = {
            when {
                canFetchCookies -> TextButton(onClick = onGetCookies) { Text("Get cookies") }
                canContinue -> TextButton(onClick = onContinueAnyway) { Text("Continue anyway") }
                else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

/**
 * Whether a failure looks like it would be solved by signing in.
 *
 * yt-dlp says so in different ways depending on the site, so the check covers the phrasings
 * that all mean the same thing: the request needs an authenticated session.
 */
fun isCookieRelated(message: String): Boolean {
    val lower = message.lowercase()
    return COOKIE_MARKERS.any { it in lower }
}

private val COOKIE_MARKERS = listOf(
    "cookie",
    "sign in",
    "log in",
    "login",
    "confirm your age",
    "age-restricted",
    "private video",
    "members-only",
    "account"
)
