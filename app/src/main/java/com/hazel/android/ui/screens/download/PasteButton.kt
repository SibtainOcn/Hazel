package com.hazel.android.ui.screens.download

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hazel.android.R
import kotlinx.coroutines.delay

/**
 * When the clipboard holds something worth pasting, a mark that tells one clip from the
 * next; null when there is nothing to paste.
 *
 * Read from the clip's description only, never its text, so checking does not make Android
 * announce that the app read the clipboard. Where the system has already looked at the clip
 * and found no link in it, it counts as nothing to paste; where it has not looked, any text
 * counts. Checked again whenever the app comes back to the front, takes focus, or the clip
 * changes while it is open.
 */
@Composable
fun rememberClipStamp(): Long? {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    var stamp by remember { mutableStateOf(clipboard?.let(::stampOf)) }
    val refresh = { stamp = clipboard?.let(::stampOf) }

    // The clipboard can only be looked at while the app has focus, so a check made before
    // focus lands sees nothing; focus arriving is a reason to look again.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(clipboard, lifecycleOwner) {
        val onClip = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        clipboard?.addPrimaryClipChangedListener(onClip)
        val onResume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(onResume)
        onDispose {
            clipboard?.removePrimaryClipChangedListener(onClip)
            lifecycleOwner.lifecycle.removeObserver(onResume)
        }
    }
    return stamp
}

private fun stampOf(clipboard: ClipboardManager): Long? {
    val description = runCatching { clipboard.primaryClipDescription }.getOrNull() ?: return null
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    ) return null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
        description.getConfidenceScore(TextClassifier.TYPE_URL) <= 0f
    ) return null

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) description.timestamp else 1L
}

/** How long the button keeps its label after a new clip arrives, before it folds to an icon. */
private const val PASTE_LABEL_MS = 1600L

/**
 * The paste button: with its label when a new clip arrives, so it says what it is, then
 * folded down to its icon so it sits quietly over the list. [stamp] is the clip it is for,
 * and a new one opens it out again.
 */
@Composable
fun PasteButton(
    stamp: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLabel by remember { mutableStateOf(true) }
    LaunchedEffect(stamp) {
        showLabel = true
        delay(PASTE_LABEL_MS)
        showLabel = false
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ContentPaste,
                contentDescription = stringResource(R.string.search_paste),
                modifier = Modifier.size(22.dp)
            )
            AnimatedVisibility(
                visible = showLabel,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.search_paste),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
