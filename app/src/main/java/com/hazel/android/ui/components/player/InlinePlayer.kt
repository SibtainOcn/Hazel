package com.hazel.android.ui.components.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import com.hazel.android.ui.components.DropdownMenu
import com.hazel.android.ui.components.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.layout.Layout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.languageLabel
import com.hazel.android.download.playback.AudioTrack
import com.hazel.android.download.playback.PlaybackController
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import kotlinx.coroutines.delay

/**
 * A player for [url] that lives as long as the composable holding it, and pauses when the
 * app leaves the screen.
 */
@Composable
fun rememberPlaybackController(url: String, startAtMs: Long = 0L): PlaybackController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(url) {
        PlaybackController(context, scope, url).also { it.start(startAtMs) }
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return controller
}

/**
 * The player shown in place of a card's artwork: the picture, and a few controls over it
 * that fade away while it plays.
 *
 * The [controller] and the full screen state belong to the caller, above any list, so
 * turning the screen for full screen cannot take the player down with the card.
 *
 * @param onDownload shows a Download action over the picture when given.
 */
@Composable
fun InlinePlayer(
    controller: PlaybackController,
    thumbnail: String?,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    closeIcon: ImageVector = Icons.Filled.Close,
    closeLabel: String? = null,
    showControls: Boolean = true
) {
    Box(modifier = modifier.background(Color.Black)) {
        PlayerArea(
            controller = controller,
            thumbnail = thumbnail,
            attached = !fullscreen,
            fullscreen = false,
            onToggleFullscreen = onFullscreen,
            onClose = onClose,
            onDownload = onDownload,
            modifier = Modifier.fillMaxSize(),
            closeIcon = closeIcon,
            closeLabel = closeLabel,
            showControls = showControls
        )
    }
}

/**
 * The same player over the whole screen, with the system bars hidden.
 *
 * The screen turns with the picture: landscape for a wide one, either side, whatever the
 * rotation lock says, and portrait for a tall one. So the status bar, menus and dialogs
 * turn with it. The activities handle the turn themselves, so playback carries on, and the
 * orientation in force before is put back on exit. A size not yet known counts as wide.
 */
@Composable
fun FullscreenPlayer(
    controller: PlaybackController,
    thumbnail: String?,
    onExit: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    val activity = LocalContext.current.findActivity()
    val ratio = controller.aspectRatio
    val tall = ratio > 0f && ratio < 1f
    val before = remember(activity) { activity?.requestedOrientation }
    DisposableEffect(activity) {
        onDispose { if (activity != null && before != null) activity.requestedOrientation = before }
    }
    SideEffect {
        val wanted = if (tall) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (activity != null && activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        LaunchedEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            PlayerArea(
                controller = controller,
                thumbnail = thumbnail,
                attached = true,
                fullscreen = true,
                onToggleFullscreen = onExit,
                onClose = null,
                onDownload = onDownload,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            )
        }
    }
}

@Composable
private fun PlayerArea(
    controller: PlaybackController,
    thumbnail: String?,
    attached: Boolean,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    modifier: Modifier = Modifier,
    closeIcon: ImageVector = Icons.Filled.Close,
    closeLabel: String? = null,
    showControls: Boolean = true
) {
    var controlsShown by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    val loading = controller.phase == PlaybackController.Phase.LOADING ||
        (controller.isBuffering && !controller.isPlaying)

    // Controls step aside while the picture plays, and come back on a tap.
    LaunchedEffect(controlsShown, controller.isPlaying, menuOpen, scrubbing) {
        if (controlsShown && controller.isPlaying && !menuOpen && !scrubbing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsShown = false
        }
    }

    // A double tap on either half skips back or ahead, and repeated ones add up.
    var skipSide by remember { mutableIntStateOf(0) }
    var skipSeconds by remember { mutableIntStateOf(0) }
    var skipTap by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipTap) {
        if (skipTap == 0) return@LaunchedEffect
        delay(SKIP_FLASH_MS)
        skipSide = 0
        skipSeconds = 0
    }

    Box(
        modifier = modifier.pointerInput(controller) {
            detectTapGestures(
                onTap = { controlsShown = !controlsShown },
                onDoubleTap = { offset ->
                    val side = if (offset.x < size.width / 2f) -1 else 1
                    controller.seekBy(side * SKIP_MS)
                    skipSeconds = if (skipSide == side) skipSeconds + SKIP_SECONDS else SKIP_SECONDS
                    skipSide = side
                    skipTap++
                }
            )
        }
    ) {
        // Artwork stands in until the first frame, and for sound without a picture.
        if (thumbnail != null && (!controller.hasVideo || controller.phase != PlaybackController.Phase.READY)) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (controller.hasVideo) {
            PlayerSurface(
                player = if (attached) controller.player else null,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (skipSide != 0) {
            SkipFlash(
                forward = skipSide > 0,
                seconds = skipSeconds,
                modifier = Modifier
                    .align(if (skipSide > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.38f)
            )
        }

        val controlsVisible = showControls && (controlsShown || scrubbing || !controller.isPlaying)
        if (!controlsVisible && controller.durationMs > 0) {
            // A hairline of progress stays along the foot while the controls are away.
            ProgressLine(
                played = controller.positionMs.toFloat() / controller.durationMs,
                buffered = controller.bufferedMs.toFloat() / controller.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.35f to Color.Black.copy(alpha = 0.15f),
                            0.65f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
            ) {
                if (onClose != null) {
                    GlassIcon(
                        icon = closeIcon,
                        description = closeLabel ?: stringResource(R.string.player_close),
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
                if (onDownload != null) {
                    Surface(
                        onClick = onDownload,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.format_sheet_download),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.Center)) {
                    when {
                        controller.phase == PlaybackController.Phase.FAILED -> FailedNote(onRetry = controller::retry)
                        loading -> CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.size(44.dp)
                        )
                        else -> MorphingPlayButton(
                            playing = controller.isPlaying,
                            onClick = controller::toggle,
                            color = Color.White
                        ) {
                            Icon(
                                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (controller.isPlaying) R.string.player_pause else R.string.player_play
                                ),
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                SeekBar(
                    controller = controller,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onMenuChange = { menuOpen = it },
                    onScrubChange = { scrubbing = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Time on the left, full screen on the right, and a bar under them: played in the accent
 * as a flowing wave while the video plays, loaded in white, the rest faint. A finger on the
 * bar lays the wave flat and lengthens its pill thumb, and a tap anywhere jumps there.
 */
@Composable
private fun SeekBar(
    controller: PlaybackController,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMenuChange: (Boolean) -> Unit,
    onScrubChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
    val setScrubbing = { value: Boolean ->
        scrubbing = value
        onScrubChange(value)
    }
    val latestScrubChange by rememberUpdatedState(onScrubChange)
    DisposableEffect(Unit) { onDispose { latestScrubChange(false) } }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val duration = controller.durationMs
    val enabled = duration > 0
    val length by rememberUpdatedState(duration)
    val played = when {
        scrubbing -> scrubFraction
        enabled -> (controller.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val buffered = if (enabled) (controller.bufferedMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumb by animateDpAsState(if (scrubbing) 22.dp else 16.dp, label = "seekThumb")
    val accent = MaterialTheme.colorScheme.primary
    // Flows while the video plays; a finger on the bar or a pause lays it flat.
    val (wave, wavePhase) = rememberSeekWave(flowing = controller.isPlaying && !scrubbing)

    Column(modifier = modifier) {
        if (scrubbing) {
            ScrubTime(
                text = formatClock((scrubFraction * duration).toLong()),
                fraction = scrubFraction,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock((played * duration).toLong())} / ${formatClock(duration)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            MoreButton(controller = controller, onMenuChange = onMenuChange)
            GlassIcon(
                icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                description = stringResource(
                    if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                onClick = onToggleFullscreen,
                background = Color.Transparent
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        controller.seekTo((offset.x / size.width * length).toLong())
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            setScrubbing(true)
                        },
                        onDragEnd = {
                            controller.seekTo((scrubFraction * length).toLong())
                            setScrubbing(false)
                        },
                        onDragCancel = { setScrubbing(false) }
                    ) { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            val y = size.height / 2f
            val stroke = if (scrubbing) 4.dp.toPx() else 3.dp.toPx()
            // The track and the loaded part start at the thumb, so nothing runs under the wave.
            drawSeekLine(y, stroke, 1f, Color.White.copy(alpha = 0.25f), from = played)
            drawSeekLine(y, stroke, buffered, Color.White.copy(alpha = 0.45f), from = played)
            drawWavyLine(y, stroke, played, accent, wave.value, wavePhase.value)
            drawPillThumb(size.width * played, y, accent, height = thumb.toPx(), width = 5.dp.toPx())
        }
    }
}

/** The time under the finger while the bar is dragged, held above that point of the bar. */
@Composable
private fun ScrubTime(text: String, fraction: Float, modifier: Modifier = Modifier) {
    Layout(
        content = {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.75f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        },
        modifier = modifier.padding(bottom = 6.dp)
    ) { measurables, constraints ->
        val bubble = measurables.first().measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, bubble.height) {
            val x = (constraints.maxWidth * fraction - bubble.width / 2f)
                .coerceIn(0f, (constraints.maxWidth - bubble.width).toFloat().coerceAtLeast(0f))
            bubble.place(x.toInt(), 0)
        }
    }
}

private enum class PlayerChoice { NONE, QUALITY, AUDIO }

/**
 * More choices for the playing video: its quality and its audio track, each opening a list.
 * The quality is saved as the playback quality, so later videos start at it. A video with
 * one sound track lists it as the original.
 */
@Composable
private fun MoreButton(controller: PlaybackController, onMenuChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cap by remember(context) { SettingsRepository.getPlayQuality(context) }
        .collectAsState(initial = SettingsRepository.DEFAULT_PLAY_QUALITY)
    var menu by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(PlayerChoice.NONE) }
    val latestMenuChange by rememberUpdatedState(onMenuChange)
    LaunchedEffect(menu, open) { latestMenuChange(menu || open != PlayerChoice.NONE) }
    DisposableEffect(Unit) { onDispose { latestMenuChange(false) } }

    val loading = controller.phase == PlaybackController.Phase.LOADING
    val heights = controller.heights.filter { it <= MAX_PLAY_HEIGHT }
    val playingHeight = controller.videoHeight.takeIf { it > 0 } ?: cap
    val tracks = controller.audioTracks
    val playingTrack = tracks.firstOrNull { it.id == controller.audioTrackId }

    @Composable
    fun Value(text: String) {
        if (loading) {
            LinearProgressIndicator(strokeCap = StrokeCap.Round, modifier = Modifier.width(36.dp))
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    Box {
        GlassIcon(
            icon = Icons.Filled.MoreVert,
            description = stringResource(R.string.player_more_options),
            onClick = { menu = true },
            background = Color.Transparent
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // Offered only when this video has more than one quality to choose from.
            if (controller.hasVideo && (loading || heights.size > 1)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.properties_quality), maxLines = 1) },
                    compact = true,
                    icon = Icons.Filled.HighQuality,
                    enabled = !loading,
                    trailingIcon = { Value(stringResource(R.string.player_quality_value, playingHeight)) },
                    onClick = {
                        menu = false
                        open = PlayerChoice.QUALITY
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_audio_track), maxLines = 1) },
                compact = true,
                icon = Icons.Filled.Translate,
                enabled = !loading,
                trailingIcon = {
                    Value(playingTrack?.let { audioTrackLabel(it) } ?: stringResource(R.string.player_audio_original))
                },
                onClick = {
                    menu = false
                    open = PlayerChoice.AUDIO
                }
            )
        }
    }

    val close = { open = PlayerChoice.NONE }
    when (open) {
        PlayerChoice.NONE -> Unit
        PlayerChoice.QUALITY -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_play_quality),
            choices = heights.map { it to stringResource(R.string.player_quality_value, it) },
            selected = playingHeight,
            onSelect = { height ->
                close()
                scope.launch {
                    // Kept on the settings' own steps; the step at or above an odd height
                    // still plays that height here.
                    val step = SettingsRepository.PLAY_QUALITIES.firstOrNull { it >= height }
                        ?: SettingsRepository.PLAY_QUALITIES.last()
                    SettingsRepository.setPlayQuality(context, step)
                    controller.reload()
                }
            },
            onDismiss = close
        )
        PlayerChoice.AUDIO -> {
            val labelled = tracks.map { it to audioTrackLabel(it) }
            // The original first, audio description last, the rest by name.
            val choices = if (labelled.isEmpty()) {
                listOf("" to stringResource(R.string.player_audio_original))
            } else {
                labelled.sortedWith(compareBy({ !it.first.original }, { it.first.descriptive }, { it.second }))
                    .map { (track, label) -> track.id to label }
            }
            SingleChoiceDialog(
                title = stringResource(R.string.player_audio_track),
                choices = choices,
                selected = if (tracks.isEmpty()) "" else controller.audioTrackId.orEmpty(),
                onSelect = { id ->
                    close()
                    controller.selectAudio(id)
                },
                onDismiss = close
            )
        }
    }
}

/** A track's language in the app's language, marked when it is the original or a description. */
@Composable
private fun audioTrackLabel(track: AudioTrack): String {
    val name = track.language?.let(::languageLabel) ?: stringResource(R.string.player_audio_original)
    return when {
        track.descriptive -> stringResource(R.string.player_audio_descriptive, name)
        track.original && track.language != null -> stringResource(R.string.player_audio_original_of, name)
        else -> name
    }
}

/** The bar alone, for while the controls are hidden. */
@Composable
internal fun ProgressLine(played: Float, buffered: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        drawSeekLine(y, size.height, buffered.coerceIn(0f, 1f), Color.White.copy(alpha = 0.35f))
        drawSeekLine(y, size.height, played.coerceIn(0f, 1f), accent)
    }
}

private fun DrawScope.drawSeekLine(y: Float, stroke: Float, fraction: Float, color: Color, from: Float = 0f) {
    if (fraction <= from) return
    drawLine(
        color = color,
        start = Offset(size.width * from, y),
        end = Offset(size.width * fraction, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

/** The rewind or forward mark shown on the side a double tap skipped on. */
@Composable
private fun SkipFlash(forward: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    val shape = if (forward) {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    } else {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Text(
                pluralStringResource(R.plurals.player_skip_seconds, seconds, seconds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FailedNote(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.player_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = onRetry,
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.18f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.player_retry), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun GlassIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.55f)
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Minutes and seconds, with hours once there are any. */
internal fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
/** The tallest quality the player offers; downloads are where anything taller belongs. */
private const val MAX_PLAY_HEIGHT = 1080
private const val SKIP_SECONDS = 5
private const val SKIP_MS = SKIP_SECONDS * 1000L
private const val SKIP_FLASH_MS = 700L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
