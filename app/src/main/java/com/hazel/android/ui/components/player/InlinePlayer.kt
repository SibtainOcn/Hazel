package com.hazel.android.ui.components.player

import android.content.res.Configuration
import android.view.OrientationEventListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.hazel.android.download.playback.PlaybackController
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null
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
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The same player over the whole screen, with the system bars hidden.
 *
 * The app itself never turns. A wide picture on an upright screen is drawn turned a
 * quarter, following which way the phone is tilted, so it fills the screen held sideways
 * whatever the rotation lock says. Turning the app instead restarted it and stopped
 * playback. A tall picture, or a screen already sideways, is drawn as it is.
 */
@Composable
fun FullscreenPlayer(
    controller: PlaybackController,
    thumbnail: String?,
    onExit: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val wide = remember { controller.aspectRatio == 0f || controller.aspectRatio >= 1f }
    val upright = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    val turn = wide && upright

    // Which way to turn: towards whichever side the phone is tipped to. Read from the
    // sensor directly, so it works with the rotation lock on.
    var degrees by remember { mutableFloatStateOf(90f) }
    DisposableEffect(turn) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                when (orientation) {
                    in 60..120 -> degrees = -90f
                    in 240..300 -> degrees = 90f
                }
            }
        }
        if (turn && listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val area = if (turn) {
                Modifier
                    .requiredSize(width = maxHeight, height = maxWidth)
                    .graphicsLayer { rotationZ = degrees }
            } else {
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            }
            PlayerArea(
                controller = controller,
                thumbnail = thumbnail,
                attached = true,
                fullscreen = true,
                onToggleFullscreen = onExit,
                onClose = null,
                onDownload = onDownload,
                modifier = area
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
    modifier: Modifier = Modifier
) {
    var controlsShown by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    val loading = controller.phase == PlaybackController.Phase.LOADING ||
        (controller.isBuffering && !controller.isPlaying)

    // Controls step aside while the picture plays, and come back on a tap.
    LaunchedEffect(controlsShown, controller.isPlaying, menuOpen) {
        if (controlsShown && controller.isPlaying && !menuOpen) {
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

        val controlsVisible = controlsShown || !controller.isPlaying
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
                        icon = Icons.Filled.Close,
                        description = stringResource(R.string.player_close),
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
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        else -> Surface(
                            onClick = controller::toggle,
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
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
                }

                SeekBar(
                    controller = controller,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onMenuChange = { menuOpen = it },
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
 * Time on the left, full screen on the right, and a thin bar under them: played in the
 * accent, loaded in white, the rest faint. The dot on the bar grows while it is dragged,
 * and a tap anywhere on the bar jumps there.
 */
@Composable
private fun SeekBar(
    controller: PlaybackController,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMenuChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
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
    val thumb by animateDpAsState(if (scrubbing) 8.dp else 6.dp, label = "seekThumb")
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock((played * duration).toLong())} / ${formatClock(duration)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            // Offered only when this video has more than one quality to choose from.
            if (controller.hasVideo && controller.heights.count { it <= MAX_PLAY_HEIGHT } > 1) {
                QualityButton(controller = controller, onMenuChange = onMenuChange)
            }
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
                .height(20.dp)
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
                            scrubbing = true
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            controller.seekTo((scrubFraction * length).toLong())
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false }
                    ) { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            val y = size.height / 2f
            val stroke = if (scrubbing) 4.dp.toPx() else 3.dp.toPx()
            drawSeekLine(y, stroke, 1f, Color.White.copy(alpha = 0.25f))
            drawSeekLine(y, stroke, buffered, Color.White.copy(alpha = 0.45f))
            drawSeekLine(y, stroke, played, accent)
            drawCircle(accent, radius = thumb.toPx(), center = Offset(size.width * played, y))
        }
    }
}

/**
 * The quality playing now, and a menu of the qualities this video has, from whichever
 * engine found it. The choice is saved as the playback quality, so later videos start at
 * it; a video without that quality plays the nearest one below, or its smallest.
 */
@Composable
private fun QualityButton(controller: PlaybackController, onMenuChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cap by SettingsRepository.getPlayQuality(context)
        .collectAsState(initial = SettingsRepository.DEFAULT_PLAY_QUALITY)
    var open by remember { mutableStateOf(false) }
    val setOpen = { value: Boolean ->
        open = value
        onMenuChange(value)
    }
    val playing = controller.videoHeight.takeIf { it > 0 } ?: cap

    Box {
        Surface(
            onClick = { setOpen(true) },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.HighQuality,
                    contentDescription = stringResource(R.string.fetch_settings_play_quality),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.player_quality_value, playing),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { setOpen(false) }) {
            controller.heights.filter { it <= MAX_PLAY_HEIGHT }.forEach { height ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_quality_value, height)) },
                    trailingIcon = if (height == playing) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        setOpen(false)
                        scope.launch {
                            // Kept on the settings' own steps; the step at or above an odd
                            // height still plays that height here.
                            val step = SettingsRepository.PLAY_QUALITIES.firstOrNull { it >= height }
                                ?: SettingsRepository.PLAY_QUALITIES.last()
                            SettingsRepository.setPlayQuality(context, step)
                            if (height != playing) controller.reload()
                        }
                    }
                )
            }
        }
    }
}

/** The bar alone, for while the controls are hidden. */
@Composable
private fun ProgressLine(played: Float, buffered: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        drawSeekLine(y, size.height, buffered.coerceIn(0f, 1f), Color.White.copy(alpha = 0.35f))
        drawSeekLine(y, size.height, played.coerceIn(0f, 1f), accent)
    }
}

private fun DrawScope.drawSeekLine(y: Float, stroke: Float, fraction: Float, color: Color) {
    if (fraction <= 0f) return
    drawLine(
        color = color,
        start = Offset(0f, y),
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
