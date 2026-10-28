package com.hazel.android.ui.components.player

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.playback.PlaybackController
import com.hazel.android.ui.components.rememberAnimationsOn
import com.hazel.android.ui.screens.history.CookieAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Where the one playing video is shown. */
enum class PlayerPlace { CARD, MINI, LARGE }

/**
 * Moves the playing video between its card, the mini player and the large view, so a
 * scroll or a download never stops it.
 *
 * The card reports where it is on every layout; the video goes to the mini player once
 * most of the card has scrolled away or a download takes the card over, and flies back
 * when the card is in full view again. Every call names the link it is about, so a card
 * leaving for another link's sake changes nothing.
 */
@Stable
class PlayerDock {
    var place by mutableStateOf(PlayerPlace.CARD)
        private set

    /** True while the mini player flies back into its card. */
    var docking by mutableStateOf(false)
        private set

    /** Where the playing card is, in window coordinates; read by the flight back. */
    var cardBounds by mutableStateOf<Rect?>(null)
        private set

    /** The list's own area, in window coordinates; not state, as it moves with no one reading. */
    var viewport: Rect? = null

    /** Where the mini player rests, in screen coordinates, for the large view to grow from. */
    var miniOnScreen: Rect? = null

    private var url: String? = null

    /** A new link starts playing in its card. */
    fun start(link: String?) {
        url = link
        place = PlayerPlace.CARD
        docking = false
        cardBounds = null
        largeReady = false
    }

    fun onCardMoved(link: String, bounds: Rect, canDock: Boolean, fullscreen: Boolean) {
        if (link != url) return
        cardBounds = bounds
        val view = viewport ?: return
        val visible = visibleFraction(bounds, view)
        when {
            place == PlayerPlace.CARD && !fullscreen && visible < MINI_BELOW -> place = PlayerPlace.MINI
            place == PlayerPlace.MINI && !docking && canDock && visible >= DOCK_FROM -> docking = true
            docking && (!canDock || visible < MINI_BELOW) -> docking = false
        }
    }

    fun onCardGone(link: String, fullscreen: Boolean) {
        if (link != url) return
        // The last spot is kept: the mini player flies out from it.
        docking = false
        if (place == PlayerPlace.CARD && !fullscreen) place = PlayerPlace.MINI
    }

    /** The card is taken over by a download. */
    fun toMini(link: String) {
        if (link == url && place == PlayerPlace.CARD) place = PlayerPlace.MINI
    }

    /**
     * True once the large view has drawn and taken the video. Until then the mini player
     * stays on screen holding it, so opening never shows an empty frame.
     */
    var largeReady by mutableStateOf(false)
        private set

    fun expand() {
        if (place != PlayerPlace.MINI) return
        docking = false
        largeReady = false
        place = PlayerPlace.LARGE
    }

    fun largeShown() {
        if (place == PlayerPlace.LARGE) largeReady = true
    }

    fun collapse() {
        if (place != PlayerPlace.LARGE) return
        largeReady = false
        place = PlayerPlace.MINI
    }

    /** The flight back has landed; the card shows the video again. */
    fun docked() {
        if (!docking) return
        docking = false
        place = PlayerPlace.CARD
    }

    companion object {
        /** Below this share of the card in view, the video moves to the mini player. */
        const val MINI_BELOW = 0.4f
        /** At this share in view, it goes back into the card. */
        const val DOCK_FROM = 0.95f

        /** How much of [card]'s height lies inside [view]. */
        fun visibleFraction(card: Rect, view: Rect): Float {
            if (card.height <= 0f) return 0f
            val shown = min(card.bottom, view.bottom) - max(card.top, view.top)
            return (shown / card.height).coerceIn(0f, 1f)
        }
    }
}

private val MiniShape = RoundedCornerShape(20.dp)
private val LargeShape = RoundedCornerShape(28.dp)
private val MiniMargin = 12.dp
private val MiniMaxWidth = 208.dp
private val Fly = spring<Rect>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

/** The mini player's width on a screen [hostWidth] wide: about half a phone's width. */
internal fun miniWidth(hostWidth: androidx.compose.ui.unit.Dp) = minOf(MiniMaxWidth, hostWidth * 0.56f)

/**
 * The mini player, bottom left of the area it is placed over: the video, a play button on an
 * expressive shape, a close button, and a hairline of progress. A tap anywhere else opens
 * the large view. It flies in from the card and back into it.
 */
@Composable
fun MiniPlayer(
    dock: PlayerDock,
    controller: PlaybackController,
    thumbnail: String?,
    fullscreen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animate = rememberAnimationsOn()
    val density = LocalDensity.current
    var hostWindow by remember { mutableStateOf(Offset.Zero) }
    var hostScreen by remember { mutableStateOf(Offset.Zero) }
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var shown by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                hostWindow = it.boundsInWindow().topLeft
                hostScreen = it.positionOnScreen()
            }
    ) {
        val rest = with(density) {
            val w = miniWidth(maxWidth).toPx()
            val h = w * 9f / 16f
            val m = MiniMargin.toPx()
            Rect(Offset(m, constraints.maxHeight - m - h), Size(w, h))
        }
        SideEffect { dock.miniOnScreen = rest.translate(hostScreen) }

        val place = dock.place
        val docking = dock.docking
        LaunchedEffect(place, docking, rest) {
            when {
                docking -> snapshotFlow { dock.cardBounds }.filterNotNull().collectLatest { card ->
                    val target = card.translate(-hostWindow)
                    if (animate) rect.animateTo(target, Fly) else rect.snapTo(target)
                    dock.docked()
                }
                place == PlayerPlace.MINI -> {
                    if (!shown) {
                        // From the card where it was, else up from below the screen's edge.
                        val from = dock.cardBounds?.translate(-hostWindow) ?: rest.translate(Offset(0f, rest.height * 1.5f))
                        rect.snapTo(if (animate) from else rest)
                        shown = true
                    }
                    if (animate) rect.animateTo(rest, Fly) else rect.snapTo(rest)
                }
                place == PlayerPlace.LARGE -> {
                    // The large view grows from here and lands here again on the way back.
                    rect.snapTo(rest)
                    shown = true
                }
                else -> shown = false
            }
        }

        // While the large view opens, the mini player keeps the video until it is taken.
        val holding = place == PlayerPlace.LARGE && !dock.largeReady
        if (shown && (place == PlayerPlace.MINI || docking || holding)) {
            MiniCard(
                dock = dock,
                controller = controller,
                thumbnail = thumbnail,
                attached = !fullscreen,
                controls = !docking,
                onClose = onClose,
                modifier = Modifier.placedAt { rect.value }
            )
        }
    }
}

@Composable
private fun MiniCard(
    dock: PlayerDock,
    controller: PlaybackController,
    thumbnail: String?,
    attached: Boolean,
    controls: Boolean,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val loading = controller.phase == PlaybackController.Phase.LOADING ||
        (controller.isBuffering && !controller.isPlaying)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .shadow(12.dp, MiniShape)
            .clip(MiniShape)
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MiniShape)
            .clickable(onClickLabel = stringResource(R.string.player_expand), onClick = dock::expand)
    ) {
        if (thumbnail != null && (!controller.hasVideo || controller.phase != PlaybackController.Phase.READY)) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (controller.hasVideo) {
            PlayerSurface(player = if (attached) controller.player else null, modifier = Modifier.fillMaxSize())
        }
        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
                GlassIcon(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.player_close),
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
                Box(modifier = Modifier.align(Alignment.Center)) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = accent,
                            trackColor = accent.copy(alpha = 0.25f),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        MorphingPlayButton(
                            playing = controller.isPlaying,
                            onClick = controller::toggle,
                            color = accent,
                            size = 44.dp
                        ) {
                            Icon(
                                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (controller.isPlaying) R.string.player_pause else R.string.player_play
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
        if (controller.durationMs > 0) {
            ProgressLine(
                played = controller.positionMs.toFloat() / controller.durationMs,
                buffered = controller.bufferedMs.toFloat() / controller.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

/**
 * The video opened large over the screen, with every control and the title under it. It
 * grows out of the mini player and shrinks back into it; a swipe down, a tap outside or
 * back does that.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargePlayer(
    dock: PlayerDock,
    controller: PlaybackController,
    title: String?,
    author: String?,
    thumbnail: String?,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
    onDownload: (() -> Unit)?
) {
    val animate = rememberAnimationsOn()
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { closing = true },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        val scrimAlpha = SCRIM
        // The view draws its own dimming and motion; the window's would run on top. Set once,
        // before the first frame is drawn, so the window is never laid out a second time.
        var windowReady by remember { mutableStateOf(false) }
        if (!windowReady) {
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.apply {
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setWindowAnimations(0)
                }
                windowReady = true
            }
        }
        val density = LocalDensity.current
        var rootScreen by remember { mutableStateOf<Offset?>(null) }
        val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
        val scrim = remember { Animatable(0f) }
        var settled by remember { mutableStateOf(false) }
        var drag by remember { mutableFloatStateOf(0f) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootScreen = it.positionOnScreen() }
        ) {
            val target = with(density) {
                val margin = 12.dp.toPx()
                val w = constraints.maxWidth - 2 * margin
                val ratio = controller.aspectRatio.takeIf { it > 0f } ?: (16f / 9f)
                val h = min(w / ratio, constraints.maxHeight * 0.6f)
                val width = min(w, h * ratio)
                Rect(Offset((constraints.maxWidth - width) / 2f, (constraints.maxHeight - h) / 2f - 48.dp.toPx()), Size(width, h))
            }
            val origin = rootScreen
            val mini = origin?.let { o -> dock.miniOnScreen?.translate(-o) }

            LaunchedEffect(origin, closing) {
                if (origin == null) return@LaunchedEffect
                val small = mini ?: target
                if (!closing) {
                    if (!settled) rect.snapTo(if (animate) small else target)
                    // From here this view draws the video; the mini player hands it over.
                    dock.largeShown()
                    if (animate) {
                        launch { scrim.animateTo(scrimAlpha, spring(stiffness = Spring.StiffnessMediumLow)) }
                        rect.animateTo(target, Fly)
                    } else {
                        scrim.snapTo(scrimAlpha)
                    }
                    settled = true
                } else {
                    settled = false
                    val from = rect.value.translate(Offset(0f, drag))
                    drag = 0f
                    rect.snapTo(from)
                    if (animate) {
                        launch { scrim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                        rect.animateTo(small, Fly)
                    }
                    dock.collapse()
                }
            }
            // A turn of the screen while open lands it on the new spot.
            LaunchedEffect(target) { if (settled && !closing) rect.snapTo(target) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim.value / scrimAlpha * (1f - (drag / (size.height * 0.6f)).coerceIn(0f, 1f)) }
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) { detectTapGestures { closing = true } }
            )

            Box(
                modifier = Modifier
                    .placedAt { rect.value.translate(Offset(0f, drag)) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (drag > size.height * 0.35f) {
                                    closing = true
                                } else {
                                    scope.launch {
                                        Animatable(drag).animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) { drag = value }
                                    }
                                }
                            },
                            onDragCancel = { drag = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                drag = (drag + amount).coerceAtLeast(0f)
                            }
                        )
                    }
                    .clip(LargeShape)
            ) {
                InlinePlayer(
                    controller = controller,
                    thumbnail = thumbnail,
                    // Held back until the mini player lets go, so only one surface draws it.
                    fullscreen = fullscreen || !dock.largeReady,
                    onFullscreen = onFullscreen,
                    onClose = null,
                    onDownload = null,
                    showControls = settled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // The title and channel float above the picture on their own accent label, the way
            // the actions float under it, so the picture itself stays clear.
            if (!title.isNullOrBlank() || !author.isNullOrBlank()) {
                AnimatedVisibility(
                    visible = settled && drag == 0f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.endingAbove { target.top - 12.dp.toPx() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(TitleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val ink = MaterialTheme.colorScheme.onPrimary
                        if (!title.isNullOrBlank()) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!author.isNullOrBlank()) {
                            Text(
                                author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ink.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // The actions alone under the picture, each on its own expressive shape and all in
            // the accent, the way the app's other shaped actions look.
            AnimatedVisibility(
                visible = settled && drag == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.offset { IntOffset(0, (target.bottom + 20.dp.toPx()).toInt()) }
            ) {
                val accent = MaterialTheme.colorScheme.primary
                val ink = MaterialTheme.colorScheme.onPrimary
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (onDownload != null) {
                            CookieAction(
                                icon = Icons.Filled.Download,
                                label = R.string.format_sheet_download,
                                enabled = true,
                                container = accent,
                                content = ink,
                                shape = MaterialShapes.Cookie6Sided.toShape(),
                                onClick = onDownload
                            )
                        }
                        CookieAction(
                            icon = Icons.Filled.KeyboardArrowDown,
                            label = R.string.player_minimize,
                            enabled = true,
                            container = accent,
                            content = ink,
                            shape = MaterialShapes.Pentagon.toShape(),
                            onClick = { closing = true }
                        )
                        CookieAction(
                            icon = Icons.Filled.Close,
                            label = R.string.player_close,
                            enabled = true,
                            container = accent,
                            content = ink,
                            shape = MaterialShapes.Clover4Leaf.toShape(),
                            onClick = onClose
                        )
                    }
                }
            }
        }
    }
}

private const val SCRIM = 0.82f
private val TitleShape = RoundedCornerShape(24.dp)

/** Places what follows so its bottom edge sits at [bottom] px, whatever its height. */
private fun Modifier.endingAbove(bottom: Density.() -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(0, (bottom() - placeable.height).toInt().coerceAtLeast(0))
    }
}

/**
 * Places and sizes what follows at a rectangle read at layout time, so motion never
 * recomposes. It spans its parent, so what it places stays inside its bounds for touch.
 */
private fun Modifier.placedAt(rect: () -> Rect): Modifier = layout { measurable, constraints ->
    val r = rect()
    val placeable = measurable.measure(
        Constraints.fixed(r.width.toInt().coerceAtLeast(0), r.height.toInt().coerceAtLeast(0))
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.toInt(), r.top.toInt()) }
}
