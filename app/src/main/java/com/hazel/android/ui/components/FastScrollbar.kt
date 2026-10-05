package com.hazel.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long the thumb stays lit after the list stops moving. */
private const val REST_DELAY_MS = 1500L

private val THUMB_HEIGHT = 52.dp

/** The width a finger can grab: the list's side margin, so it never sits over a card. */
private val TOUCH_WIDTH = 20.dp

private val THUMB_REST_WIDTH = 4.dp
private val THUMB_ACTIVE_WIDTH = 6.dp
private val THUMB_HELD_WIDTH = 8.dp

/** How much of the surface is mixed into the accent: a softer shade than the solid accent. */
private const val SOFTEN = 0.3f

/** The thumb at rest: there to be found, faint enough to stay out of the way. */
private const val REST_ALPHA = 0.3f
private const val ACTIVE_ALPHA = 0.85f

/**
 * A scrollbar on a list's end edge that can be held and dragged to move through a long list
 * in one stroke.
 *
 * The thumb is always there on a list long enough to scroll: thin and faint at rest, lit in a
 * soft shade of the accent while the list moves, and in the full accent, a little wider, while
 * it is held. It has no track.
 *
 * Only the thumb takes touches. The edge it runs along still scrolls the list as usual.
 *
 * Place it over the list, in the same box, aligned to the end. [trackPadding] keeps the
 * thumb's travel clear of the list's padding and of anything floating over the list's end.
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = 8.dp)
) {
    val scrollable by remember(state) { derivedStateOf { state.canScrollBackward || state.canScrollForward } }
    if (!scrollable) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbPx = with(density) { THUMB_HEIGHT.toPx() }

    var trackHeight by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf(false) }
    // Where the finger has the thumb while it is held; the list's own position otherwise.
    var heldFraction by remember { mutableFloatStateOf(0f) }
    val listFraction by remember(state) { derivedStateOf { state.scrollFraction() } }
    val fraction = if (held) heldFraction else listFraction

    var active by remember { mutableStateOf(false) }
    val moving = state.isScrollInProgress || held
    LaunchedEffect(moving) {
        if (moving) {
            active = true
        } else {
            delay(REST_DELAY_MS)
            active = false
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val soft = lerp(accent, MaterialTheme.colorScheme.surface, SOFTEN)
    val color by animateColorAsState(
        when {
            held -> accent
            active -> soft.copy(alpha = ACTIVE_ALPHA)
            else -> soft.copy(alpha = REST_ALPHA)
        },
        tween(if (active) 150 else 400),
        label = "thumbColor"
    )
    val width by animateDpAsState(
        when {
            held -> THUMB_HELD_WIDTH
            active -> THUMB_ACTIVE_WIDTH
            else -> THUMB_REST_WIDTH
        },
        label = "thumbWidth"
    )

    // Read by the gesture, written by placement: the thumb moves under the finger, and a
    // touch is reported relative to where the thumb was last placed.
    val placedTop = remember { FloatArray(1) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(trackPadding)
            .width(TOUCH_WIDTH)
            .onSizeChanged { trackHeight = it.height }
    ) {
        val travel = (trackHeight - thumbPx).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .offset {
                    val top = fraction * travel
                    placedTop[0] = top
                    IntOffset(0, top.roundToInt())
                }
                .size(TOUCH_WIDTH, THUMB_HEIGHT)
                .then(
                    if (travel <= 0f) Modifier
                    else Modifier.pointerInput(state, travel) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val grab = down.position.y
                            heldFraction = listFraction
                            held = true
                            scope.launch { state.stopScroll() }
                            drag(down.id) { change ->
                                change.consume()
                                val y = placedTop[0] + change.position.y - grab
                                heldFraction = (y / travel).coerceIn(0f, 1f)
                                state.scrollToFraction(heldFraction)
                            }
                            held = false
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(width)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
}

/** Average height of an item on screen, spacing included: the estimate for the ones that are not. */
private fun LazyListState.averageItemSize(): Float {
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return 0f
    return (items.sumOf { it.size } + layoutInfo.mainAxisItemSpacing * items.size).toFloat() / items.size
}

/** How far the list can scroll in all, estimated from the items on screen. */
private fun LazyListState.estimatedMaxScroll(average: Float): Float {
    val info = layoutInfo
    val content = info.beforeContentPadding + info.afterContentPadding +
        average * info.totalItemsCount - info.mainAxisItemSpacing
    return (content - info.viewportSize.height).coerceAtLeast(1f)
}

/** How far down the list is, 0 at the top and 1 at the end. Exact at both ends. */
private fun LazyListState.scrollFraction(): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val average = averageItemSize()
    val scrolled = first.index * average - first.offset
    return (scrolled / estimatedMaxScroll(average)).coerceIn(0f, 1f)
}

/** Moves the list to [fraction] of its length. */
private fun LazyListState.scrollToFraction(fraction: Float) {
    val count = layoutInfo.totalItemsCount
    val average = averageItemSize()
    if (count == 0 || average <= 0f) return
    if (fraction >= 1f) {
        requestScrollToItem(count - 1)
        return
    }
    val target = fraction * estimatedMaxScroll(average)
    val index = (target / average).toInt().coerceIn(0, count - 1)
    requestScrollToItem(index, (target - index * average).roundToInt())
}
