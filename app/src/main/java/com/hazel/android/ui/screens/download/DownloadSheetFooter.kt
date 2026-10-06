package com.hazel.android.ui.screens.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.FlatChip
import kotlinx.coroutines.launch

/**
 * A round link button. Tapping it asks for [LinkOptionsDialog], so a tap meant to copy does
 * not also send the user to another app.
 *
 * The dialog is not shown from here on purpose. This button sits inside a bottom sheet,
 * which is a window of its own, and a dialog composed inside that window could leave the
 * sheet ignoring touches once it closed, until the sheet was closed and opened again. The
 * sheet keeps the dialog's state and composes it next to itself, the way its other dialogs
 * are, so the dialog's window is a sibling of the sheet's rather than its child.
 */
@Composable
fun SheetLinkButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Link,
            contentDescription = stringResource(R.string.sheet_link_options),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * The round incognito switch. It is the same setting the rest of the app reads, so turning
 * it on here turns it on everywhere, and what it did is said through [onFeedback].
 */
@Composable
fun IncognitoButton(
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incognito by remember(context) { SettingsRepository.getIncognito(context) }.collectAsState(initial = false)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (incognito) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .clickable {
                val enabled = !incognito
                scope.launch { SettingsRepository.setIncognito(context, enabled) }
                onFeedback(if (enabled) "Incognito: Enabled" else "Incognito: Disabled")
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.incognito),
            contentDescription = if (incognito) {
                "Incognito on, this download is not recorded"
            } else {
                "Incognito off"
            },
            modifier = Modifier.size(22.dp),
            tint = if (incognito) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/** The short message a sheet shows after a tap, in the inverse tone so it reads over anything. */
@Composable
internal fun FeedbackToast(message: String, modifier: Modifier = Modifier) {
    // Sized to its words, like a system toast, so a one-word message is not a banner.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 4.dp
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * The one dialog every link in the app opens: the address with Copy, and Open when there is
 * a single address. Given several [links], Copy takes all of them, one to a line.
 */
@Composable
fun LinkOptionsDialog(
    links: List<String>,
    onFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.sheet_link_copied)
    val openFailedMessage = stringResource(R.string.sheet_link_open_failed)
    val all = links.joinToString("\n")
    LinkDialog(
        text = all,
        onCopy = {
            copySheetLink(context, all)
            onDismiss()
            onFeedback(copiedMessage)
        },
        onOpen = links.singleOrNull()?.let { url ->
            {
                onDismiss()
                if (!openSheetLink(context, url)) onFeedback(openFailedMessage)
            }
        },
        onDismiss = onDismiss
    )
}

/**
 * What the link button offers: the address itself, so it can be checked before anything is
 * done with it, and what to do with it as plain chips. [onOpen] is null where there is no
 * single address to open.
 */
@Composable
private fun LinkDialog(
    text: String,
    onCopy: () -> Unit,
    onOpen: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Link, contentDescription = null) },
        title = { Text(stringResource(R.string.sheet_link_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatChip(label = stringResource(R.string.sheet_link_copy), onClick = onCopy)
                if (onOpen != null) {
                    FlatChip(label = stringResource(R.string.sheet_link_open), onClick = onOpen, selected = true)
                }
            }
        }
    )
}

/** Long enough to read, short enough that it is gone before it is in the way. */
internal const val FEEDBACK_MS = 2_000L

internal fun copySheetLink(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Hazel link", text))
}

/**
 * Opens the address where the user would expect to see it.
 *
 * The site's own app first, since a link to a video is a video someone wants to watch and
 * the app is where the account, the history and the controls are. The browser is the answer
 * when that app is not installed, and a device with neither is left alone: the address is
 * already on the clipboard by the time this is called.
 */
internal fun openSheetLink(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val host = uri.host.orEmpty().removePrefix("www.").lowercase()

    if (host.endsWith("youtube.com") || host.endsWith("youtu.be")) {
        val inApp = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(YOUTUBE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(inApp) }.isSuccess) return true
    }

    val anywhere = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(anywhere) }.isSuccess
}

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
