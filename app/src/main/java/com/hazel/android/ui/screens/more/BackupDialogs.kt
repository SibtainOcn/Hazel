package com.hazel.android.ui.screens.more

import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.BackupCategory
import com.hazel.android.data.BackupFile
import com.hazel.android.data.BackupRepository
import kotlinx.coroutines.launch

internal val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

/** A backup's date the way the system writes dates and times. */
internal fun backupDate(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(
        context, millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    )

/**
 * Reads a backup, shows what is in it, and restores what the user keeps ticked. Returns the
 * action that starts it from a file; [onDone] runs after a restore that went through.
 */
@Composable
internal fun rememberRestoreFlow(onDone: () -> Unit = {}, onCancel: () -> Unit = {}): (Uri) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var contents by remember { mutableStateOf<BackupRepository.Contents?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    contents?.let { backup ->
        RestorePreviewDialog(
            contents = backup,
            onConfirm = { chosen ->
                contents = null
                if (chosen.isEmpty()) { onCancel(); return@RestorePreviewDialog }
                scope.launch {
                    val ok = runCatching { BackupRepository.restore(context, backup, chosen) }.isSuccess
                    toast(resources.getString(if (ok) R.string.backup_restored else R.string.backup_restore_failed))
                    if (ok) onDone() else onCancel()
                }
            },
            onDismiss = { contents = null; onCancel() }
        )
    }

    return { uri ->
        scope.launch {
            try {
                contents = BackupRepository.read(context, uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (newer: BackupRepository.NewerBackup) {
                toast(resources.getString(R.string.backup_newer, newer.appVersion))
                onCancel()
            } catch (_: Exception) {
                toast(resources.getString(R.string.backup_not_a_backup))
                onCancel()
            }
        }
    }
}

/** What a backup holds and where it came from, each category ticked to be restored. */
@Composable
private fun RestorePreviewDialog(
    contents: BackupRepository.Contents,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val available = BackupCategory.entries.filter { it in contents.categories }
    var ticked by remember(contents) { mutableStateOf(available.toSet()) }
    ChoiceDialog(
        title = stringResource(R.string.backup_preview_title),
        onDismiss = onDismiss,
        buttons = {
            DialogButton(stringResource(R.string.download_cancel), filled = false, onClick = onDismiss, modifier = Modifier.weight(1f))
            DialogButton(stringResource(R.string.backup_restore), filled = true, onClick = { onConfirm(ticked) }, modifier = Modifier.weight(1f))
        }
    ) {
        item {
            Text(
                stringResource(
                    R.string.backup_preview_from,
                    contents.appVersion.ifBlank { "?" },
                    if (contents.createdAt > 0) backupDate(context, contents.createdAt) else "?"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            )
        }
        itemsIndexed(available) { index, category ->
            val on = category in ticked
            val count = contents.counts[category] ?: 0
            ChoiceRow(
                label = stringResource(category.label),
                description = if (category != BackupCategory.SETTINGS) stringResource(R.string.backup_entries, count) else null,
                chosen = on,
                shape = groupShape(index, available.size),
                role = Role.Checkbox,
                onClick = { ticked = if (on) ticked - category else ticked + category }
            )
        }
    }
}

/**
 * Offered once on the first launch of an install with nothing in it. Names the newest backup
 * this install can see, or asks for the file where it cannot see any, as from Android 11 a
 * reinstalled app cannot read what the earlier install wrote.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupRestoreOffer() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offered by remember(context) { BackupRepository.offerRestore(context) }.collectAsState(initial = false)
    if (!offered) return

    val latest by produceState<BackupFile?>(null) { value = BackupRepository.list(context).firstOrNull() }
    val dismiss: () -> Unit = { scope.launch { BackupRepository.dismissRestoreOffer(context) } }
    // Out of the way while the file is picked or its preview is up, and back if that is cancelled.
    var stepping by remember { mutableStateOf(false) }
    val restore = rememberRestoreFlow(onDone = dismiss, onCancel = { stepping = false })
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) restore(uri) else stepping = false
    }

    val found = latest
    if (stepping) return
    ChoiceDialog(
        title = stringResource(R.string.backup_offer_title),
        onDismiss = dismiss,
        buttons = {
            DialogButton(stringResource(R.string.backup_not_now), filled = false, onClick = dismiss, modifier = Modifier.weight(1f))
            DialogButton(
                stringResource(if (found != null) R.string.backup_restore else R.string.backup_offer_choose),
                filled = true,
                onClick = {
                    stepping = true
                    if (found != null) restore(found.uri)
                    else runCatching { picker.launch(BACKUP_MIME_TYPES) }.onFailure { stepping = false }
                },
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        item {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Restore, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
                Text(
                    if (found != null) stringResource(R.string.backup_offer_found, backupDate(context, found.madeAt))
                    else stringResource(R.string.backup_offer_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
    }
}
