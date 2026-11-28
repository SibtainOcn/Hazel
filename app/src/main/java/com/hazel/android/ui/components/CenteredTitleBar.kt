package com.hazel.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A header row with something at each end and a title in the middle.
 *
 * The title sits in the true centre when it fits between the wider side, slides into the free
 * gap when it doesn't (big fonts, long translations, narrow screens), and is ellipsized by its
 * own Text only as a last resort. Each side gets at most 40% of the width, so neither can push
 * the other off screen. Placement is relative, so right-to-left languages mirror it.
 */
@Composable
fun CenteredTitleBar(
    start: @Composable () -> Unit,
    title: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp
) {
    Layout(contents = listOf(start, title, end), modifier = modifier) { (starts, titles, ends), constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val side = loose.copy(maxWidth = (width * 0.4f).toInt())
        val s = starts.map { it.measure(side) }
        val e = ends.map { it.measure(side) }
        val sw = s.maxOfOrNull { it.width } ?: 0
        val ew = e.maxOfOrNull { it.width } ?: 0
        val free = (width - sw - ew - 2 * gapPx).coerceAtLeast(0)
        val t = titles.map { it.measure(loose.copy(maxWidth = free)) }
        val tw = t.maxOfOrNull { it.width } ?: 0
        val height = maxOf(
            constraints.minHeight,
            s.maxOfOrNull { it.height } ?: 0,
            e.maxOfOrNull { it.height } ?: 0,
            t.maxOfOrNull { it.height } ?: 0
        )
        val centred = width - 2 * (maxOf(sw, ew) + gapPx)
        val titleX = if (tw <= centred) (width - tw) / 2 else sw + gapPx + (free - tw) / 2
        layout(width, height) {
            s.forEach { it.placeRelative(0, (height - it.height) / 2) }
            t.forEach { it.placeRelative(titleX, (height - it.height) / 2) }
            e.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
        }
    }
}
