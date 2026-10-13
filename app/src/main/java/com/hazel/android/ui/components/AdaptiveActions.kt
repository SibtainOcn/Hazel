package com.hazel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Rows of actions that keep their look wherever their labels fit, and give way rather than
 * squeeze a label into a column of letters where they do not: a long translation, a large
 * system font, a narrow phone. On an ordinary phone in English they lay out exactly as a
 * plain Row would.
 */

/** Least room between the item at the start and the actions at the end. */
private val StartGap = 8.dp
/** Room between lines once the actions have moved onto lines of their own. */
private val LineGap = 8.dp

/**
 * An optional item at the start and actions at the end, as a Row with a weighted spacer
 * between them would place them. When they do not all fit on one line, the start item keeps
 * the first line and the actions move to the next, at the end; actions too wide for even a
 * line of their own wrap onto further lines, still at the end. No action is ever narrowed.
 *
 * [spacing] is the room between two actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionsRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    start: (@Composable () -> Unit)? = null,
    actions: @Composable FlowRowScope.() -> Unit
) {
    Layout(
        content = {
            if (start != null) Box { start() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(LineGap),
                itemVerticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        },
        modifier = modifier,
        measurePolicy = ActionsRowPolicy
    )
}

/**
 * Made once and shared: it reads no state. Each child is measured once; whether the actions
 * fit beside the start item comes from their max intrinsic width, which is their width on one
 * line.
 */
private val ActionsRowPolicy = MeasurePolicy { measurables, constraints ->
    val width = constraints.maxWidth
    if (measurables.size == 1) {
        // Only as wide as the actions, unless told to fill, so it can sit beside others.
        val actions = measurables[0].measure(Constraints(maxWidth = width))
        val own = maxOf(actions.width, constraints.minWidth)
        layout(own, actions.height) { actions.placeRelative(own - actions.width, 0) }
    } else {
        val (startMeasurable, actionsMeasurable) = measurables
        val start = startMeasurable.measure(Constraints(maxWidth = width))
        val gap = StartGap.roundToPx()
        val oneLine = actionsMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        if (start.width + gap + oneLine <= width) {
            val actions = actionsMeasurable.measure(Constraints(maxWidth = width - start.width - gap))
            val height = maxOf(start.height, actions.height)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                actions.placeRelative(width - actions.width, (height - actions.height) / 2)
            }
        } else {
            val actions = actionsMeasurable.measure(Constraints(maxWidth = width))
            val below = start.height + LineGap.roundToPx()
            layout(width, below + actions.height) {
                start.placeRelative(0, 0)
                actions.placeRelative(width - actions.width, below)
            }
        }
    }
}

/**
 * Actions sharing the width equally, as weighted children of a Row, while every label fits
 * its share on one line. When one would not, each takes the full width on a line of its own,
 * in the same order, so none is cut short.
 */
@Composable
fun EqualWidthActions(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val policy = remember(spacing) { EqualWidthPolicy(spacing) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

/** One per spacing in use; reads no state. Each child is measured once. */
private class EqualWidthPolicy(private val spacing: Dp) : MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>,
        constraints: Constraints
    ): androidx.compose.ui.layout.MeasureResult {
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val count = measurables.size
        if (count == 0) return layout(width, 0) {}
        val share = (width - gap * (count - 1)) / count
        val fits = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= share }

        return if (fits) {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(share)) }
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, (height - it.height) / 2)
                    x += share + gap
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}
