package com.hazel.android.ui.screens.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.BatchAudioFormats
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.GenericFormats
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.MediaProbe
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.ui.screens.download.rememberSaveDirPicker
import kotlinx.coroutines.launch

/** The quality names the yt-dlp sheets show before a link is read, in the user's language. */
internal data class VideoLabels(val bestVideo: String, val bestAudio: String, val worst: String)

/** Gallery videos are told apart by their listed address, which no sheet shows as a ytdl: link. */
internal fun videoKey(item: GalleryItem): String = item.url.removePrefix("ytdl:")

/**
 * The post's own page when it holds just this video and a reader opens it (a reel, a one-video
 * post), so yt-dlp offers its full quality list. Stories have no page of their own.
 */
internal fun ownPage(post: GalleryPost, item: GalleryItem): String? =
    item.postUrl?.takeIf { post.items.size == 1 && it.startsWith("http") && "/stories/" !in it }

/** A name for a file the site left untitled, as stories are. */
internal fun videoTitle(item: GalleryItem): String =
    item.title.ifBlank { "${item.author.ifBlank { item.site }} ${item.index}".trim() }

/** A video's file link as the yt-dlp sheet downloads it, with the post kept for history. */
internal fun fileInfo(item: GalleryItem, file: String, labels: VideoLabels) = MediaInfo(
    url = file,
    title = videoTitle(item),
    uploader = item.author,
    thumbnail = item.thumbnail,
    durationSeconds = 0,
    videoFormats = listOf(MediaProbe.BEST_VIDEO.copy(label = labels.bestVideo)),
    audioFormats = BatchAudioFormats.ladder(labels.bestAudio, labels.worst),
    pageUrl = item.postUrl?.takeIf { it.startsWith("http") }
)

/**
 * The yt-dlp download sheet for gallery videos, opened on the Gallery tab: the single video
 * sheet for one, the sheet for several links for more. Each video is read as it opens; a
 * choice made before its read lands is downloaded once it does.
 */
@Composable
internal fun GalleryVideoSheet(
    post: GalleryPost,
    items: List<GalleryItem>,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val labels = VideoLabels(
        bestVideo = stringResource(R.string.batch_quality_best),
        bestAudio = stringResource(R.string.audio_quality_best),
        worst = stringResource(R.string.batch_quality_worst)
    )
    val unavailable = stringResource(R.string.gallery_video_unavailable)
    val reads = remember(post, items) { items.associate { videoKey(it) to viewModel.videoInfo(post, it, labels) } }
    // A key with a null value was read and could not be had.
    val resolved = remember(reads) { mutableStateMapOf<String, MediaInfo?>() }
    LaunchedEffect(reads) {
        reads.forEach { (key, read) -> launch { resolved[key] = read.await() } }
    }
    var handedOff by remember(reads) { mutableStateOf(false) }
    DisposableEffect(reads) {
        onDispose { if (!handedOff) reads.values.forEach { it.cancel() } }
    }

    val options by remember(context) { SettingsRepository.getDownloadOptions(context) }
        .collectAsState(initial = DownloadOptions())
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())
    val pickSaveDir = rememberSaveDirPicker(saveDirs)
    val resetSaveDir: (Boolean) -> Unit = { isVideo -> scope.launch { SettingsRepository.resetSaveDir(context, isVideo) } }
    val changeOptions: (DownloadOptions) -> Unit = { changed ->
        scope.launch { SettingsRepository.setDownloadOptions(context, changed) }
    }

    fun shown(item: GalleryItem): MediaInfo {
        val key = videoKey(item)
        return resolved[key]?.copy(url = key)
            ?: GenericFormats.placeholder(key, labels.bestVideo, labels.bestAudio, labels.worst)
                .copy(title = videoTitle(item), uploader = item.author, thumbnail = item.thumbnail)
    }

    if (items.size == 1) {
        val item = items.first()
        val key = videoKey(item)
        FormatSheet(
            info = shown(item),
            options = options,
            onOptionsChange = changeOptions,
            saveDirs = saveDirs,
            isReadingLink = key !in resolved,
            formatsError = unavailable.takeIf { key in resolved && resolved[key] == null },
            onPickSaveDir = pickSaveDir,
            onResetSaveDir = resetSaveDir,
            onDownload = { format, audioLanguage, title, author, oneOff ->
                handedOff = true
                val plan = DownloadPlan(shown(item), format, title, author, audioLanguage)
                viewModel.downloadVideos(context, listOf(reads.getValue(key) to plan), options.with(oneOff), saveDirs)
                onDismiss()
            },
            onDismiss = onDismiss
        )
        return
    }

    val removed = remember(reads) { mutableStateListOf<String>() }
    val shownItems = items.filter { videoKey(it) !in removed }
    BatchDownloadSheet(
        results = shownItems.map(::shown),
        options = options,
        onOptionsChange = changeOptions,
        saveDirs = saveDirs,
        onPickSaveDir = pickSaveDir,
        onResetSaveDir = resetSaveDir,
        onResolveFormats = {},
        readingUrls = reads.keys - resolved.keys,
        sourceUrl = post.items.firstNotNullOfOrNull { it.postUrl },
        onRemove = { info ->
            removed += info.url
            if (removed.size == items.size) onDismiss()
        },
        onDownload = { plans ->
            handedOff = true
            val picks = plans.mapNotNull { plan -> reads[plan.info.url]?.let { it to plan } }
            val picked = picks.map { it.first }.toSet()
            reads.values.filter { it !in picked }.forEach { it.cancel() }
            viewModel.downloadVideos(context, picks, options, saveDirs)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}
