package com.hazel.android.ui.components.player

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.screens.history.DownloadPropertiesSheet
import com.hazel.android.util.FileTagReader
import com.hazel.android.util.FileTags
import com.hazel.android.util.MediaOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file on the device to play in Hazel: a download, or a file another app opened in Hazel. */
data class OfflineMedia(
    val uri: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val isVideo: Boolean,
    val mimeType: String,
    /** The download's record, for Properties and Show file; null for another app's file. */
    val entry: HistoryEntry? = null
) {
    companion object {
        fun of(entry: HistoryEntry) = OfflineMedia(
            uri = entry.fileUri,
            title = entry.title,
            artist = entry.author,
            thumbnail = entry.thumbnail,
            isVideo = entry.isVideo,
            mimeType = entry.mimeType,
            entry = entry
        )
    }
}

/** The one file playing in the app; the app's root shows its player while it is set. */
object OfflinePlayback {
    private val _media = MutableStateFlow<OfflineMedia?>(null)
    val media: StateFlow<OfflineMedia?> = _media.asStateFlow()

    fun play(media: OfflineMedia) {
        _media.value = media
    }

    /** Plays [entry] in Hazel; an image is not played and goes to the device's viewer instead. */
    fun play(context: Context, entry: HistoryEntry) {
        if (entry.isImage) MediaOpener.play(context, entry.fileUri, entry.mimeType) else play(OfflineMedia.of(entry))
    }

    fun close() {
        _media.value = null
    }

    /** The player of the file playing now, so a screen showing that file can show it as playing. */
    var active by androidx.compose.runtime.mutableStateOf<com.hazel.android.download.playback.PlaybackController?>(null)
        private set

    /** The player playing [fileUri], if it is the one playing now. */
    fun playerOf(fileUri: String) = active?.takeIf { it.url == fileUri }

    internal fun attach(controller: com.hazel.android.download.playback.PlaybackController) {
        active = controller
    }

    internal fun detach(controller: com.hazel.android.download.playback.PlaybackController) {
        if (active === controller) active = null
    }
}

/**
 * Plays [media] in Hazel: a video full screen, sound in the music sheet. Both keep the saved
 * file's actions (Open with, Show file, Share, Properties) at hand, and sound plays on with the
 * app left, with the media notification.
 */
@Composable
fun OfflinePlayer(media: OfflineMedia, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = rememberPlaybackController(
        url = media.uri,
        background = !media.isVideo,
        metadata = {
            MediaMetadata.Builder()
                .setTitle(media.title.ifBlank { null })
                .setArtist(media.artist.ifBlank { null })
                .setArtworkUri(media.thumbnail?.takeIf { it.startsWith("http") }?.let(Uri::parse))
                .build()
        }
    )
    androidx.compose.runtime.DisposableEffect(controller) {
        OfflinePlayback.attach(controller)
        onDispose { OfflinePlayback.detach(controller) }
    }
    var propertiesOpen by remember { mutableStateOf(false) }
    val onProperties = media.entry?.let { { propertiesOpen = true } }

    if (media.isVideo) {
        // Opens full screen; leaving full screen keeps it playing in a sheet, which closes the player.
        var fullscreen by remember { mutableStateOf(true) }
        VideoSheet(media, controller, fullscreen, onFullscreen = { fullscreen = true }, onProperties = onProperties, onClose = onClose)
        if (fullscreen) {
            FullscreenPlayer(
                controller = controller,
                thumbnail = media.thumbnail,
                onExit = { fullscreen = false },
                onBack = onClose,
                barActions = { FileActionGroup(media, onProperties, onPicture = true) }
            )
        }
    } else {
        MusicSheet(media, controller, onProperties, onClose)
    }

    val entry = media.entry
    if (propertiesOpen && entry != null) {
        DownloadPropertiesSheet(
            entry = entry,
            present = true,
            onDismiss = { propertiesOpen = false },
            onRemove = {
                propertiesOpen = false
                scope.launch { DownloadHistoryRepository.remove(context, entry.id) }
            },
            onDelete = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MusicSheet(
    media: OfflineMedia,
    controller: com.hazel.android.download.playback.PlaybackController,
    onProperties: (() -> Unit)?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The file's own tags and cover, read once off the main thread.
    val tags by produceState(FileTags(), media.uri) { value = FileTagReader.read(context, media.uri) }
    val cover by produceState<ImageBitmap?>(null, media.uri) { value = embeddedCover(context, media.uri) }
    val title = media.title.ifBlank { tags.title }
    val artist = media.artist.ifBlank { tags.artist }

    var skip by remember { mutableIntStateOf(0) }
    // The last count, kept while the feedback fades out so it never reads 0.
    var shownSkip by remember { mutableIntStateOf(0) }
    if (skip != 0) shownSkip = skip
    var skipTap by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipTap) {
        if (skipTap == 0) return@LaunchedEffect
        delay(SKIP_FEEDBACK_MS)
        skip = 0
    }
    val skipBy: (Int) -> Unit = { side ->
        controller.seekBy(side * SKIP_MS)
        skip = if (skip != 0 && (skip > 0) == (side > 0)) skip + side * SKIP_SECONDS else side * SKIP_SECONDS
        skipTap++
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.background
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Sized to the sheet, so a small or large screen, or a large font, never crowds it.
            val art = minOf(maxWidth * 0.56f, ART_MAX)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val rest by animateFloatAsState(
                        if (controller.isPlaying) 1f else 0.92f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "artRest"
                    )
                    Box(
                        modifier = Modifier
                            .size(art)
                            .graphicsLayer { scaleX = rest; scaleY = rest }
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val image = cover
                        when {
                            image != null -> Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            media.thumbnail != null -> AsyncImage(
                                model = media.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> ArtworkPlaceholder(Modifier.fillMaxSize())
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = skip != 0,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                    ) {
                        SkipFeedback(seconds = shownSkip)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    title.ifBlank { stringResource(R.string.properties_kind_audio) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotBlank()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val scrub = remember { SeekScrub() }
                // Room kept above the bar for the time under the finger, so nothing moves or clips.
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = SCRUB_ROOM)
                ) {
                    if (scrub.active) {
                        ScrubTime(
                            text = formatClock((scrub.fraction * controller.durationMs).toLong()),
                            fraction = scrub.fraction,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                WaveSeekLine(
                    controller = controller,
                    scrub = scrub,
                    track = scheme.onSurface.copy(alpha = 0.16f),
                    loaded = scheme.onSurface.copy(alpha = 0.16f)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    val played = scrub.played(controller)
                    Text(
                        formatClock((played * controller.durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatClock(controller.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                ) {
                    SkipButton(forward = false) { skipBy(-1) }
                    when (controller.phase) {
                        com.hazel.android.download.playback.PlaybackController.Phase.FAILED -> Text(
                            stringResource(R.string.player_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(PLAY_SIZE)
                        )
                        else -> MorphingPlayButton(
                            playing = controller.isPlaying,
                            onClick = controller::toggle,
                            color = scheme.primary,
                            size = PLAY_SIZE
                        ) {
                            Icon(
                                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (controller.isPlaying) R.string.player_pause else R.string.player_play
                                ),
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    SkipButton(forward = true) { skipBy(1) }
                }

                FileActionGroup(media, onProperties, onPicture = false)
            }
        }
    }
}

/** A video out of full screen: the picture on top, its title and the file's actions under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSheet(
    media: OfflineMedia,
    controller: com.hazel.android.download.playback.PlaybackController,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onProperties: (() -> Unit)?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val tags by produceState(FileTags(), media.uri) { value = FileTagReader.read(context, media.uri) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            val ratio = controller.aspectRatio.takeIf { it > 0f }?.coerceIn(0.56f, 2.4f) ?: (16f / 9f)
            InlinePlayer(
                controller = controller,
                thumbnail = media.thumbnail,
                fullscreen = fullscreen,
                onFullscreen = onFullscreen,
                onClose = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = VIDEO_MAX_HEIGHT)
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                media.title.ifBlank { tags.title }.ifBlank { stringResource(R.string.properties_kind_video) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val artist = media.artist.ifBlank { tags.artist }
            if (artist.isNotBlank()) {
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            FileActionGroup(media, onProperties, onPicture = false)
        }
    }
}

/** Ten seconds back or ahead: the replay arrow, mirrored for ahead, with a quick turn on each tap. */
@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    val label = pluralStringResource(R.plurals.player_skip_seconds, SKIP_SECONDS, SKIP_SECONDS)
    Surface(
        onClick = {
            onClick()
            scope.launch {
                turn.snapTo(0f)
                turn.animateTo(if (forward) 40f else -40f, tween(110))
                turn.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(SKIP_SIZE)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_replay),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        rotationZ = turn.value
                        if (forward) scaleX = -1f
                    }
            )
            Text(
                SKIP_SECONDS.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** What a skip did, over the artwork: arrows running the way it went and how far, adding up on repeats. */
@Composable
private fun SkipFeedback(seconds: Int) {
    val forward = seconds > 0
    val run = rememberInfiniteTransition(label = "skipRun")
    val phase by run.animateFloat(0f, 3f, infiniteRepeatable(tween(SKIP_RUN_MS), RepeatMode.Restart), label = "skipPhase")
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            val arrows = @Composable {
                Row {
                    repeat(3) { i ->
                        // Lit one after another, the way the skip went.
                        val lit = if (forward) i else 2 - i
                        val alpha = (1f - ((phase - lit + 3f) % 3f) / 3f).coerceIn(0.25f, 1f)
                        Icon(
                            if (forward) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowLeft,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).graphicsLayer { this.alpha = alpha }
                        )
                    }
                }
            }
            if (!forward) arrows()
            Text(
                pluralStringResource(R.plurals.player_skip_seconds, kotlin.math.abs(seconds), kotlin.math.abs(seconds)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            if (forward) arrows()
        }
    }
}

private class FileAction(val icon: Painter, val label: String, val onClick: () -> Unit)

/**
 * A saved file's actions as one connected group: large corners on the outside, small where two
 * meet. Under the music the labels show beneath each icon, side by side while they fit and two
 * by two when a long language or a large font would break them; over a picture the group is
 * icons only, so the video stays clear.
 */
@Composable
private fun FileActionGroup(
    media: OfflineMedia,
    onProperties: (() -> Unit)?,
    onPicture: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entry = media.entry
    val actions = listOfNotNull(
        FileAction(painterResource(R.drawable.ic_open_app), stringResource(R.string.media_open_chooser)) {
            MediaOpener.openWith(context, media.uri, media.mimeType)
        },
        entry?.let {
            FileAction(rememberVectorPainter(Icons.Filled.FolderOpen), stringResource(R.string.properties_show_file)) {
                MediaOpener.showFile(context, it.fileUri, it.isVideo, it.isImage)
            }
        },
        FileAction(rememberVectorPainter(Icons.Filled.Share), stringResource(R.string.properties_share)) {
            MediaOpener.share(context, media.uri, media.mimeType)
        },
        onProperties?.let { FileAction(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.history_card_properties), it) }
    )
    if (onPicture) {
        // Plain icons beside full screen, drawn like it, so the picture keeps one quiet control row.
        Row(modifier = modifier) {
            actions.forEach { action ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClickLabel = action.label, onClick = action.onClick)
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.labelMedium
        val tile = (maxWidth - GAP * (actions.size - 1)) / actions.size - 12.dp
        val fits = with(LocalDensity.current) {
            actions.all { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width.toDp() <= tile }
        }
        val rows = if (fits) listOf(actions) else actions.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            rows.forEachIndexed { r, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                ) {
                    row.forEachIndexed { i, action ->
                        Surface(
                            onClick = action.onClick,
                            shape = gridShape(r, rows.size, i, row.size),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                            ) {
                                Icon(action.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    action.label,
                                    style = style,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun gridShape(row: Int, rows: Int, index: Int, count: Int): RoundedCornerShape {
    val first = index == 0
    val last = index == count - 1
    val top = row == 0
    val bottom = row == rows - 1
    return RoundedCornerShape(
        topStart = if (top && first) OUTER else INNER,
        topEnd = if (top && last) OUTER else INNER,
        bottomStart = if (bottom && first) OUTER else INNER,
        bottomEnd = if (bottom && last) OUTER else INNER
    )
}

/** The cover art inside the file, scaled down to what the sheet shows; null when there is none. */
private suspend fun embeddedCover(context: Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, Uri.parse(uri))
        val bytes = retriever.embeddedPicture ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private const val SKIP_SECONDS = 10
private const val SKIP_MS = SKIP_SECONDS * 1000L
private const val SKIP_FEEDBACK_MS = 900L
private const val SKIP_RUN_MS = 900
private const val COVER_PX = 640
private val ART_MAX: Dp = 260.dp
private val VIDEO_MAX_HEIGHT: Dp = 420.dp
private val SCRUB_ROOM: Dp = 40.dp
private val PLAY_SIZE: Dp = 76.dp
private val SKIP_SIZE: Dp = 56.dp
private val OUTER: Dp = 22.dp
private val INNER: Dp = 8.dp
private val GAP: Dp = 3.dp
