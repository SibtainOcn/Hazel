package com.hazel.android.ui.screens.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.ui.screens.download.HoldDialog
import com.hazel.android.ui.screens.history.DownloadPropertiesSheet
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The long-press card of a gallery file, as on Home: Play (a video) or Full screen (an
 * image) and Download, plus Open and Properties once the file was saved.
 */
@Composable
internal fun GalleryHoldDialog(
    post: GalleryPost,
    item: GalleryItem,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var properties by remember { mutableStateOf<Pair<HistoryEntry, Boolean>?>(null) }
    // Read once per hold, off the main thread: the record and whether its file is still there.
    val saved by produceState<Pair<HistoryEntry, Boolean>?>(null, post, item) {
        value = runCatching {
            val history = DownloadHistoryRepository.getHistory(context).first()
            withContext(Dispatchers.Default) { savedEntry(post, item, history) }
                ?.let { it to MediaPresence.exists(context, it.fileUri) }
        }.getOrNull()
    }

    properties?.let { (entry, present) ->
        DownloadPropertiesSheet(
            entry = entry,
            present = present,
            onDismiss = { properties = null; onDismiss() },
            onRemove = {
                properties = null
                onDismiss()
                scope.launch { DownloadHistoryRepository.remove(context, entry.id) }
            },
            onDelete = null
        )
        return
    }

    val title = item.title.ifBlank { stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image) }
    HoldDialog(
        title = title,
        subtitle = item.author.ifBlank { post.author },
        thumbnail = item.thumbnail ?: item.url.takeUnless { item.isVideo },
        fullScreen = !item.isVideo,
        onDismiss = onDismiss,
        onPlay = { onDismiss(); onView() },
        onDownload = { onDismiss(); onDownload() },
        onOpen = saved?.takeIf { it.second }?.first?.let { entry ->
            { onDismiss(); MediaOpener.play(context, entry.fileUri, entry.mimeType) }
        },
        onProperties = saved?.let { found -> { properties = found } }
    )
}

/**
 * The history record of [item], or null when none can be told apart from its post's other
 * files. A gallery download records the post's link on every file it saved, and a video read
 * by yt-dlp records the post page or the file link, so the record is picked by kind and,
 * when the post saved several files of that kind, by the file's own name.
 */
internal fun savedEntry(post: GalleryPost, item: GalleryItem, history: List<HistoryEntry>): HistoryEntry? {
    val links = setOfNotNull(post.source, item.postUrl, item.url, item.playUrl).filter { it.isNotBlank() }
    val candidates = history.filter { it.url in links && it.isVideo == item.isVideo }
    if (candidates.isEmpty()) return null
    val sameKind = post.items.count { it.isVideo == item.isVideo }
    if (sameKind == 1) return candidates.first()
    val stem = item.url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
    if (stem.length < 4) return null
    return candidates.firstOrNull { it.fileName.substringBeforeLast('.').contains(stem) }
}
