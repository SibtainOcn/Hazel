package com.hazel.android.ui.screens.download

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * What the user sees when a link could not be read.
 *
 * Two things are shown, and they are different in kind. The first is a plain sentence saying
 * what happened, which is what most people need. Under it, unedited, is what the engine
 * actually printed: it usually names the exact reason, it is what a bug report needs, and
 * summarising it would throw away the only copy.
 *
 * The actions are ordered by what can actually fix it. Where the reason is a missing
 * sign-in, collecting cookies is offered, because that is the one thing that resolves it.
 * Otherwise going ahead is offered, since a link whose metadata read failed can often still
 * be downloaded.
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
        // The darkest tone rather than a lifted panel, and the screen's width less a margin.
        // A log reads as a block of monospaced text, and a narrow dialog wraps it into
        // something no one can follow.
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        icon = {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                if (canFetchCookies) "Sign-in needed" else "Could not read this link",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    when {
                        canFetchCookies ->
                            "The source wants an account before it will hand this over. " +
                                    "Signing in saves cookies for the site and tries again."
                        canContinue ->
                            "The details could not be read, but the download itself may " +
                                    "still work. Going ahead offers the best available quality."
                        else -> "The source refused the request."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "What the engine reported",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            message.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            // Monospaced and free to run wide, because the output is
                            // aligned text and rewrapping it hides the shape of the error.
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCopyLog) {
                    Text("Copy log", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(4.dp))
                when {
                    canFetchCookies -> TextButton(onClick = onGetCookies) {
                        Text("Sign in", fontWeight = FontWeight.SemiBold)
                    }
                    canContinue -> TextButton(onClick = onContinueAnyway) {
                        Text("Try anyway", fontWeight = FontWeight.SemiBold)
                    }
                    else -> TextButton(onClick = onDismiss) {
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                }
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
