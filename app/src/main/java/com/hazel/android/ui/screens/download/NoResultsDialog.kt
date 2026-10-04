package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.R

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
    /**
     * Whether there is a site to sign in to. Offered on any failure, as a second action
     * where the failure does not already read as a missing sign-in: a source that refuses
     * an anonymous request often gives no hint that a sign-in would answer it.
     */
    canAddCookies: Boolean = false,
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
                stringResource(if (canFetchCookies) R.string.no_results_sign_in_title else R.string.no_results_error_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        when {
                            canFetchCookies -> R.string.no_results_sign_in_body
                            canContinue -> R.string.no_results_continue_body
                            else -> R.string.no_results_refused_body
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.no_results_engine_report),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Seven lines high whatever the report's length, scrolling inside past that, so
                // the dialog keeps its shape. Lines wrap: a phone is too narrow for a log to
                // run wide, and the end of an error line is the part that says why.
                val logStyle = MaterialTheme.typography.bodySmall
                val logHeight = with(LocalDensity.current) { logStyle.lineHeight.toDp() } * LOG_LINES
                val errorColor = MaterialTheme.colorScheme.error
                val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                val report = remember(message, errorColor, textColor) {
                    buildAnnotatedString {
                        val lines = message.trim().lines()
                        lines.forEachIndexed { index, line ->
                            val isError = line.trimStart().startsWith("ERROR:")
                            withStyle(SpanStyle(color = if (isError) errorColor else textColor)) { append(line) }
                            if (index < lines.lastIndex) append('\n')
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .height(logHeight + 24.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(report, style = logStyle, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCopyLog) {
                    Text(stringResource(R.string.no_results_copy_log), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (canAddCookies && !canFetchCookies) {
                    TextButton(onClick = onGetCookies) {
                        Text(stringResource(R.string.no_results_add_cookies))
                    }
                }
                when {
                    canFetchCookies -> TextButton(onClick = onGetCookies) {
                        Text(stringResource(R.string.no_results_sign_in), fontWeight = FontWeight.SemiBold)
                    }
                    canContinue -> TextButton(onClick = onContinueAnyway) {
                        Text(stringResource(R.string.no_results_try_anyway), fontWeight = FontWeight.SemiBold)
                    }
                    else -> TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.no_results_close), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    )
}

/** Lines of the engine's report the dialog shows before it scrolls. */
private const val LOG_LINES = 7

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
