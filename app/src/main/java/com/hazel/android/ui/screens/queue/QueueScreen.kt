package com.hazel.android.ui.screens.queue

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.hazel.android.ui.components.ScrollShrink
import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import androidx.compose.foundation.lazy.rememberLazyListState
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.FailedDownloadRepository
import com.hazel.android.data.QueuedDownload
import com.hazel.android.download.BatchItem
import com.hazel.android.download.BatchState
import com.hazel.android.download.DownloadState
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.MediaCard
import com.hazel.android.util.copyToClipboard
import kotlinx.coroutines.launch

/** The queue screen's tabs, in the order a download passes through them. */
private enum class QueueTab(val labelRes: Int) {
    RUNNING(R.string.queue_tab_running),
    QUEUED(R.string.queue_tab_queued),
    FAILED(R.string.history_tab_failed)
}

/**
 * Everything that is downloading, waiting, or stopped with an error.
 *
 * This is where a download is managed once it has been asked for: paused, resumed, cancelled,
 * taken off the queue, retried. The home screen only reads links and starts downloads, so it
 * stays a place to ask for things rather than a place to watch them. Each tab counts what it
 * holds, so a failure is visible without opening it.
 */
@Composable
fun QueueScreen(downloadViewModel: DownloadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by downloadViewModel.state.collectAsState()
    val queueList by DownloadQueueRepository.getQueue(context).collectAsState(initial = emptyList())
    val failedList by FailedDownloadRepository.getFailed(context).collectAsState(initial = emptyList())

    val running = runningItems(state, queueList)
    val runningUrls = running.mapTo(mutableSetOf()) { it.info.url }
    val waiting = queueList.filterNot { it.url in runningUrls || it.paused }
    val failed = failedList.sortedByDescending { it.failedAt }

    val pagerState = rememberPagerState(pageCount = { QueueTab.entries.size })
    var menuOpen by remember { mutableStateOf(false) }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var confirmClearFailed by remember { mutableStateOf(false) }
    var viewLog by remember { mutableStateOf<FailedDownload?>(null) }

    val anythingInHand = running.isNotEmpty() || waiting.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.queue_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.history_menu_action))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val isMulti = running.size + waiting.size > 1
                    if (state.isDownloading) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (isMulti) R.string.download_pause_all else R.string.download_pause))
                            },
                            enabled = !state.isProcessing,
                            onClick = {
                                menuOpen = false
                                downloadViewModel.pauseDownload()
                            }
                        )
                    } else if (anythingInHand) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (isMulti) R.string.download_resume_all else R.string.download_resume))
                            },
                            onClick = {
                                menuOpen = false
                                downloadViewModel.resumeDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (isMulti) R.string.download_cancel_all else R.string.download_cancel_action))
                        },
                        enabled = anythingInHand,
                        onClick = {
                            menuOpen = false
                            confirmCancelAll = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_queue_clear_all)) },
                        enabled = waiting.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            confirmClearQueue = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_failed_clear_all)) },
                        enabled = failed.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            confirmClearFailed = true
                        }
                    )
                }
            }
        }

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            QueueTab.entries.forEachIndexed { index, tab ->
                val count = when (tab) {
                    QueueTab.RUNNING -> running.size
                    QueueTab.QUEUED -> waiting.size
                    QueueTab.FAILED -> failed.size
                }
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        BadgedBox(
                            badge = {
                                if (count > 0) {
                                    Badge(
                                        containerColor = if (tab == QueueTab.FAILED) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    ) { Text(count.toString()) }
                                }
                            }
                        ) {
                            Text(stringResource(tab.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (QueueTab.entries[page]) {
                QueueTab.RUNNING -> QueueList(
                    isEmpty = running.isEmpty(),
                    emptyIcon = Icons.Filled.Download,
                    emptyText = stringResource(R.string.queue_empty_running)
                ) { shrink ->
                    items(running, key = { "running_${it.info.url}" }) { item ->
                        Box(modifier = Modifier.scrollShrink(shrink)) {
                        MediaCard(
                            info = item.info,
                            isDownloading = item.isDownloading,
                            isProcessing = item.isDownloading && state.isProcessing,
                            processingSteps = if (item.isDownloading) state.processingSteps else emptyList(),
                            processingStep = state.processingStep,
                            progress = if (item.isDownloading) state.progress else 0f,
                            totalBytes = if (item.isDownloading) state.totalBytes else 0L,
                            batchItem = item.batchItem,
                            waitingForWifi = state.waitingForWifi,
                            onCancel = { downloadViewModel.cancelItem(item.info.url) },
                            onPause = downloadViewModel::pauseDownload,
                            onResume = downloadViewModel::resumeDownload
                        )
                        }
                    }
                }

                QueueTab.QUEUED -> QueueList(
                    isEmpty = waiting.isEmpty(),
                    emptyIcon = Icons.Filled.HourglassEmpty,
                    emptyText = stringResource(R.string.queue_empty_queued)
                ) { shrink ->
                    items(waiting, key = { "queued_${it.url}" }) { item ->
                        Box(modifier = Modifier.scrollShrink(shrink)) {
                            QueuedCard(
                                item = item,
                                onRemove = { downloadViewModel.removeQueued(context, item.url) }
                            )
                        }
                    }
                }

                QueueTab.FAILED -> QueueList(
                    isEmpty = failed.isEmpty(),
                    emptyIcon = Icons.Filled.ErrorOutline,
                    emptyText = stringResource(R.string.history_empty_failed)
                ) { shrink ->
                    items(failed, key = { "failed_${it.id}" }) { item ->
                        Box(modifier = Modifier.scrollShrink(shrink)) {
                        FailedCard(
                            item = item,
                            onViewLog = { viewLog = item },
                            onRetry = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.history_retrying_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                                downloadViewModel.retryFailed(context, item)
                            },
                            onDismiss = { scope.launch { FailedDownloadRepository.remove(context, item.id) } }
                        )
                        }
                    }
                }
            }
        }
    }

    viewLog?.let { item ->
        FailureLogDialog(
            item = item,
            onCopy = {
                copyToClipboard(context, item.errorLog)
                Toast.makeText(context, context.getString(R.string.history_failed_log_copied), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewLog = null }
        )
    }

    if (confirmCancelAll) {
        val isMulti = running.size + waiting.size > 1
        ConfirmDialog(
            title = stringResource(if (isMulti) R.string.download_cancel_all else R.string.download_cancel_action),
            body = stringResource(
                if (isMulti) R.string.download_cancel_dialog_body else R.string.download_cancel_single_dialog_body
            ),
            confirm = stringResource(R.string.download_cancel_action),
            onConfirm = {
                confirmCancelAll = false
                downloadViewModel.cancelAllDownloads()
            },
            onDismiss = { confirmCancelAll = false }
        )
    }
    if (confirmClearQueue) {
        ConfirmDialog(
            title = stringResource(R.string.history_queue_clear_all),
            body = stringResource(R.string.history_clear_dialog_body),
            confirm = stringResource(R.string.history_clear_dialog_confirm),
            onConfirm = {
                confirmClearQueue = false
                downloadViewModel.clearQueue(context)
            },
            onDismiss = { confirmClearQueue = false }
        )
    }
    if (confirmClearFailed) {
        ConfirmDialog(
            title = stringResource(R.string.history_failed_clear_all),
            body = stringResource(R.string.history_clear_dialog_body),
            confirm = stringResource(R.string.history_clear_dialog_confirm),
            onConfirm = {
                confirmClearFailed = false
                scope.launch { FailedDownloadRepository.clear(context) }
            },
            onDismiss = { confirmClearFailed = false }
        )
    }
}

/** A download in hand: the one running now, or one stopped part way through. */
private data class RunningItem(
    val info: MediaInfo,
    val isDownloading: Boolean,
    val batchItem: BatchItem?
)

/**
 * What the Running tab holds.
 *
 * The download in progress comes from the run itself. A paused one is still in hand, and so
 * is a pause written down by an earlier session, which lives only on the saved queue until
 * it is resumed.
 */
private fun runningItems(state: DownloadState, queue: List<QueuedDownload>): List<RunningItem> {
    val items = mutableListOf<RunningItem>()
    state.active?.let { active ->
        val batchItem = state.batch.firstOrNull { it.url == active.url }
        if (state.isDownloading || batchItem?.state == BatchState.PAUSED) {
            items += RunningItem(active, state.isDownloading, batchItem)
        }
    }
    queue.filter { it.paused && items.none { running -> running.info.url == it.url } }.forEach { held ->
        items += RunningItem(
            info = MediaInfo(
                url = held.url,
                title = held.title,
                uploader = held.author,
                thumbnail = held.thumbnail,
                durationSeconds = held.durationSeconds,
                videoFormats = emptyList(),
                audioFormats = emptyList()
            ),
            isDownloading = false,
            batchItem = BatchItem(held.url, held.title, BatchState.PAUSED)
        )
    }
    return items
}

@Composable
private fun QueueList(
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyText: String,
    content: androidx.compose.foundation.lazy.LazyListScope.(shrink: ScrollShrink) -> Unit
) {
    if (isEmpty) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                emptyIcon,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                emptyText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val listState = rememberLazyListState()
        val shrink = rememberScrollShrink()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(shrink),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content(shrink) }
    }
}

@Composable
private fun FailureLogDialog(item: FailedDownload, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_failed_error_log), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    item.title.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    SelectionContainer {
                        Text(
                            text = item.errorLog.ifBlank { stringResource(R.string.history_empty_failed) },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text(stringResource(R.string.history_failed_copy_log)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_clear_dialog_cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel_dialog_dismiss)) }
        }
    )
}
