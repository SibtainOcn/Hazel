package com.hazel.android.ui.screens.queue

import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.hazel.android.R
import com.hazel.android.data.QueuedDownload
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.data.toPlan
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.InfoCache
import com.hazel.android.download.QueueAdjust
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.rememberSaveDirPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A queued link's own download sheet, opened from the queue to see or change its choice.
 *
 * The same sheet a link is downloaded from, on the same terms as a link adjusted inside a
 * set: its current choice is selected, and the action hands the choice back rather than
 * starting a second download. The sheet shows the link's last read where the cache still
 * holds its formats, and reads them again otherwise, so the whole list is offered.
 *
 * Its settings are its own while the sheet is open, so changing a field here changes this
 * link and not the defaults every other download starts from.
 */
@Composable
fun QueuedItemSheet(
    item: QueuedDownload,
    downloadViewModel: DownloadViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val record = remember(item) { item.toPlan() }

    // The record holds the choice alone, so the sheet stands on it only until the link's whole
    // list is in: from the cache where its last read has formats, otherwise read again.
    var reading by remember(item.url) { mutableStateOf(true) }
    val cached by produceState(initialValue = record.info, item.url) {
        val held = withContext(Dispatchers.IO) {
            runCatching { InfoCache.metadataFor(item.url) }.getOrNull()
        }?.takeIf { it.hasResolvedFormats }
        value = held ?: downloadViewModel.formatsFor(item.url) ?: record.info
        reading = false
    }
    // The queued choice as the list names it, so its row is the one shown picked.
    val initialFormat = remember(cached, record) {
        (cached.videoFormats + cached.audioFormats).firstOrNull { it.formatId == record.format.formatId }
            ?: record.format
    }

    var options by remember(item) { mutableStateOf(item.options) }
    var pending by remember { mutableStateOf<PendingAdjust?>(null) }
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())
    val pickSaveDir = rememberSaveDirPicker(saveDirs)
    // The folders as the sheet opened, so a folder picked here can be told from one that
    // was already the default.
    val openedDirs by produceState<SaveDirs?>(initialValue = null) {
        value = runCatching { SettingsRepository.getSaveDirs(context).first() }.getOrNull()
    }

    FormatSheet(
        info = cached,
        options = options,
        onOptionsChange = { options = it },
        saveDirs = saveDirs,
        initialFormat = initialFormat,
        isLoadingFormats = reading,
        initialAudioLanguage = item.audioLanguage,
        confirmAsApply = true,
        onPickSaveDir = pickSaveDir,
        onResetSaveDir = { isVideo -> scope.launch { SettingsRepository.resetSaveDir(context, isVideo) } },
        // The one-off fields are not offered when adjusting, so the link's own cut and live
        // settings are kept as they were queued rather than cleared.
        onDownload = { format, audioLanguage, title, author, _ ->
            val plan = DownloadPlan(cached, format, title.ifBlank { item.title }, author.ifBlank { item.author }, audioLanguage)
            // The link stays in its own folder, unless a folder was picked here or it now saves
            // as the other kind; then it goes where the sheet shows, as a new download would.
            val shown = saveDirs.of(format.hasVideo)
            val picked = openedDirs?.let { it.of(format.hasVideo) != shown } == true
            val treeUri = if (!picked && format.hasVideo == item.hasVideo) item.treeUri else shown.uri
            scope.launch {
                when (downloadViewModel.adjustQueued(item.url, plan, options, treeUri)) {
                    // Would start the link over: asked first, with the sheet still up.
                    QueueAdjust.NEEDS_CONFIRM -> pending = PendingAdjust(plan, options, treeUri)
                    else -> onDismiss()
                }
            }
        },
        onDismiss = onDismiss
    )

    // Beside the sheet rather than inside it, so closing the dialog cannot leave the sheet
    // ignoring touches.
    pending?.let { adjust ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.queue_adjust_restart_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.queue_adjust_restart_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        downloadViewModel.adjustQueued(item.url, adjust.plan, adjust.options, adjust.treeUri, confirmed = true)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.queue_adjust_restart_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.history_clear_dialog_cancel))
                }
            }
        )
    }
}

/** A change from the sheet held while the user is asked whether to start the link over. */
private class PendingAdjust(
    val plan: DownloadPlan,
    val options: com.hazel.android.download.DownloadOptions,
    val treeUri: String
)
