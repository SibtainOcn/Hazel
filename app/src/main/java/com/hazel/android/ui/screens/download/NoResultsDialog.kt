package com.hazel.android.ui.screens.download

import com.hazel.android.ui.components.ActionsRow
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * Beside copying the log, at most one action is offered, and only one that can fix what the
 * engine reported ([failureKind]): signing in where it asked for an account, adding cookies
 * where the source refused an anonymous request, and going ahead where the read failed for
 * no reason that rules the download out. Media that is gone, or a site that is not
 * supported, has no fix to offer, so the dialog only says so.
 */
@Composable
fun NoResultsDialog(
    message: String,
    canFetchCookies: Boolean,
    canContinue: Boolean,
    /** Whether there is a site to sign in to, for a source that refused the request. */
    canAddCookies: Boolean = false,
    /** The link that failed, copied by the link button. No button without one. */
    link: String? = null,
    onCopyLog: () -> Unit,
    onGetCookies: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val kind = remember(message) { failureKind(message) }
    val signIn = kind == FailureKind.SIGN_IN && canFetchCookies
    val addCookies = kind == FailureKind.REFUSED && canAddCookies
    val goAhead = kind == FailureKind.OTHER && canContinue
    val context = LocalContext.current

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
                stringResource(if (signIn) R.string.no_results_sign_in_title else R.string.no_results_error_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        when {
                            signIn -> R.string.no_results_sign_in_body
                            kind == FailureKind.GONE -> R.string.no_results_gone_body
                            kind == FailureKind.UNSUPPORTED -> R.string.no_results_unsupported_body
                            kind == FailureKind.REFUSED -> R.string.no_results_refused_body
                            goAhead -> R.string.no_results_continue_body
                            else -> R.string.no_results_generic_body
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
            // The link on its own at the start, the answers to the failure at the end. The
            // answers move to a line of their own when they do not fit beside the link.
            ActionsRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = 4.dp,
                start = if (link.isNullOrBlank()) null else { {
                    IconButton(onClick = {
                        com.hazel.android.util.copyToClipboard(context, link)
                        android.widget.Toast.makeText(
                            context, context.getString(R.string.sheet_link_copied), android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = stringResource(R.string.failed_copy_url),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } }
            ) {
                TextButton(onClick = onCopyLog) {
                    Text(stringResource(R.string.no_results_copy_log), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    signIn -> TextButton(onClick = onGetCookies) {
                        Text(stringResource(R.string.no_results_sign_in), fontWeight = FontWeight.SemiBold)
                    }
                    addCookies -> TextButton(onClick = onGetCookies) {
                        Text(stringResource(R.string.no_results_add_cookies), fontWeight = FontWeight.SemiBold)
                    }
                    goAhead -> TextButton(onClick = onContinueAnyway) {
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

/** What a failed read came down to, as far as what the user can do about it. */
internal enum class FailureKind {
    /** The source wants an account. Signing in answers it. */
    SIGN_IN,

    /** The media is not there: removed, deleted, or never existed. */
    GONE,

    /** No extractor knows the site. */
    UNSUPPORTED,

    /** The source turned an anonymous request away. Cookies may get past it. */
    REFUSED,

    /** Anything else. The download itself may still work. */
    OTHER
}

/**
 * Sorts the engine's report into a [FailureKind].
 *
 * The order is the point. A region block reads "not available" too, so it goes first. Media
 * removed for good comes before a sign-in, since "the account associated with this video has
 * been terminated" names an account without one helping. A plain "unavailable" comes after
 * it, since a site that wants a login often says its content "is not available" as well.
 */
internal fun failureKind(message: String): FailureKind {
    val lower = message.lowercase()
    return when {
        "unsupported url" in lower -> FailureKind.UNSUPPORTED
        REGION_MARKERS.any { it in lower } -> FailureKind.REFUSED
        REMOVED_MARKERS.any { it in lower } -> FailureKind.GONE
        isCookieRelated(message) -> FailureKind.SIGN_IN
        // A rate limit can read "Video unavailable" for media that is there.
        RATE_LIMIT_MARKERS.any { it in lower } -> FailureKind.REFUSED
        UNAVAILABLE_MARKERS.any { it in lower } -> FailureKind.GONE
        REFUSED_MARKERS.any { it in lower } -> FailureKind.REFUSED
        else -> FailureKind.OTHER
    }
}

private val REGION_MARKERS = listOf("in your country", "geo restrict", "geo-restrict", "not available in your")

/** Gone for good, whatever account asks for it. */
private val REMOVED_MARKERS = listOf(
    "has been removed",
    "been deleted",
    "no longer available",
    "has been terminated"
)

private val RATE_LIMIT_MARKERS = listOf("try again later", "rate-limit", "rate limit")

/** Not there, where no sign-in was asked for. */
private val UNAVAILABLE_MARKERS = listOf(
    "video unavailable",
    "video is unavailable",
    "video is not available",
    "does not exist",
    "http error 404",
    ": not found",
    "no video could be found",
    "no video formats found",
    "no media found"
)

private val REFUSED_MARKERS = listOf(
    "http error 403",
    "403: forbidden",
    "http error 429",
    "too many requests",
    "http error 401"
)

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
    "logged in",
    "logged-in",
    "confirm your age",
    "age-restricted",
    "private video",
    "members-only",
    "account"
)
