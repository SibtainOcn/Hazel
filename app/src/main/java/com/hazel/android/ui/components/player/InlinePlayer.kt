package com.hazel.android.ui.components.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
 * @param onDownload shows a Download action over the picture when given.
 */
@Composable
fun InlinePlayer(
    url: String,
    thumbnail: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null
) {
    val controller = rememberPlaybackController(url)
    var fullscreen by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black)) {
        PlayerArea(
            controller = controller,
            thumbnail = thumbnail,
            attached = !fullscreen,
            fullscreen = false,
            onToggleFullscreen = { fullscreen = true },
            onClose = onClose,
            onDownload = onDownload,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (fullscreen) {
        FullscreenPlayer(
            controller = controller,
            thumbnail = thumbnail,
            onExit = { fullscreen = false },
            onDownload = onDownload
        )
    }
}

@Composable
private fun FullscreenPlayer(
    controller: PlaybackController,
    thumbnail: String?,
    onExit: () -> Unit,
    onDownload: (() -> Unit)?
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(controller.aspectRatio) {
        val previous = activity?.requestedOrientation
        // Wide pictures turn the screen; tall ones are already the right way up.
        if (controller.aspectRatio >= 1f) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (activity != null && previous != null) activity.requestedOrientation = previous
        }
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
                .background(Color.Black)
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
    modifier: Modifier = Modifier
) {
    var controlsShown by remember { mutableStateOf(true) }
    val loading = controller.phase == PlaybackController.Phase.LOADING ||
        (controller.isBuffering && !controller.isPlaying)

    // Controls step aside while the picture plays, and come back on a tap.
    LaunchedEffect(controlsShown, controller.isPlaying) {
        if (controlsShown && controller.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsShown = false
        }
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { controlsShown = !controlsShown }
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

        AnimatedVisibility(
            visible = controlsShown || !controller.isPlaying,
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    controller: PlaybackController,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = controller.durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else controller.positionMs.toFloat().coerceIn(0f, duration)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(shown.toLong())} · ${formatClock(controller.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            GlassIcon(
                icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                description = stringResource(
                    if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                onClick = onToggleFullscreen,
                background = Color.Transparent
            )
        }
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                controller.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration,
            enabled = controller.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
