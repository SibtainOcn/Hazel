package com.hazel.android.ui.screens.download

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Room between two actions in a row. */
private val ActionGap = 10.dp
/** Room between two actions stacked, or between the heading and the actions under it. */
private val StackGap = 8.dp

/**
 * The download sheet's header: the heading first, then its actions (Play, when the media is
 * already on the device, and the download action), always at the end.
 *
 * The actions sit in a row beside the heading while every word of the heading still fits
 * whole beside them, exactly as the header has always looked. When a word would not, as with
 * a long translation of Play and a large font, the actions stack, Play above the download
 * action. Only when even the stack leaves no room do they move under the heading. No word of
 * the heading is ever broken to make room for a button.
 */
@Composable
internal fun SheetHeaderLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.fillMaxWidth(), measurePolicy = SheetHeaderPolicy)
}

/**
 * Created once: it reads no state, so a recomposition of the sheet hands Compose the same
 * policy and leaves the header's layout as it was. Each child is measured once; the
 * heading's longest word comes from its min intrinsic width, which reuses the text's own
 * cached measurement.
 */
private val SheetHeaderPolicy = MeasurePolicy { measurables, constraints ->
    val width = constraints.maxWidth
    val heading = measurables.first()
    val actions = measurables.drop(1).map { it.measure(Constraints(maxWidth = width)) }
    val actionGap = ActionGap.roundToPx()
    val stackGap = StackGap.roundToPx()
    val longestWord = heading.minIntrinsicWidth(Constraints.Infinity)

    val rowWidth = actions.sumOf { it.width } + actionGap * (actions.size - 1).coerceAtLeast(0)
    val rowHeight = actions.maxOfOrNull { it.height } ?: 0
    val stackWidth = actions.maxOfOrNull { it.width } ?: 0
    val stackHeight = actions.sumOf { it.height } + stackGap * (actions.size - 1).coerceAtLeast(0)

    fun Placeable.PlacementScope.placeRow(top: Int, height: Int) {
        var x = width - rowWidth
        actions.forEach { action ->
            action.placeRelative(x, top + (height - action.height) / 2)
            x += action.width + actionGap
        }
    }

    fun Placeable.PlacementScope.placeStack(top: Int) {
        var y = top
        actions.forEach { action ->
            action.placeRelative(width - action.width, y)
            y += action.height + stackGap
        }
    }

    when {
        // Beside, in a row: the usual look.
        width - rowWidth >= longestWord -> {
            val text = heading.measure(Constraints(maxWidth = width - rowWidth))
            val height = maxOf(text.height, rowHeight)
            layout(width, height) {
                text.placeRelative(0, (height - text.height) / 2)
                placeRow(0, height)
            }
        }

        // Beside, stacked: Play above the download action.
        actions.size > 1 && width - stackWidth >= longestWord -> {
            val text = heading.measure(Constraints(maxWidth = width - stackWidth))
            val height = maxOf(text.height, stackHeight)
            layout(width, height) {
                text.placeRelative(0, (height - text.height) / 2)
                placeStack((height - stackHeight) / 2)
            }
        }

        // Under the heading, in a row if it fits the width, stacked if not.
        else -> {
            val text = heading.measure(Constraints(maxWidth = width))
            val inRow = rowWidth <= width
            val below = text.height + stackGap
            layout(width, below + if (inRow) rowHeight else stackHeight) {
                text.placeRelative(0, 0)
                if (inRow) placeRow(below, rowHeight) else placeStack(below)
            }
        }
    }
}
