package com.hazel.android.ui.screens.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The line that closes a download sheet: what is being downloaded, and whether it will be
 * remembered.
 *
 * The address is worth showing because everything above it is about one link and nothing
 * above it says which. Tapping it copies it and opens it: copied because the address is
 * often wanted somewhere else, and opened because the other reason to look at a link is to
 * go and see what is behind it. It opens in the app that owns the site where there is one,
 * and in the browser otherwise.
 *
 * Incognito sits beside it because this is where it applies: the sheet is the last moment
 * before a download is recorded, and a switch buried in a bar at the top of the app is not
 * where the decision is made. It is the same setting the rest of the app reads, so turning
 * it on here turns it on everywhere.
 */
@Composable
fun DownloadSheetFooter(
    label: String,
    modifier: Modifier = Modifier,
    /** Copied when the address is tapped. Blank for a sheet covering more than one link. */
    copyText: String = label
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val incognito by SettingsRepository.getIncognito(context).collectAsState(initial = false)

    // What the last tap did. It clears itself, so nothing has to be dismissed and the sheet
    // does not keep an old answer on screen while the user reads the rest of it.
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_MS)
            feedback = null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = copyText.isNotBlank()) {
                        copySheetLink(context, copyText)
                        feedback = if (openSheetLink(context, copyText)) {
                            "Link copied and opened"
                        } else {
                            "Link copied"
                        }
                    }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (incognito) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                    .clickable {
                        val enabled = !incognito
                        scope.launch { SettingsRepository.setIncognito(context, enabled) }
                        feedback = if (enabled) "Incognito: Enabled" else "Incognito: Disabled"
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

        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.inverseSurface
                ) {
                    Text(
                        feedback.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
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
