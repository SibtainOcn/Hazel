package com.hazel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key as keyed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How many 16:9 cards sit side by side across [width], the list's width inside its padding.
 *
 * A phone held upright has one column; held sideways, unfolded, or a tablet upright, two; a
 * tablet on its side three, and a wider screen four, so no card is drawn narrower than a
 * phone's. One card stretched across a tablet stood half the screen tall, and a list showed
 * three at a time. The steps are by width alone, so every list on a screen agrees.
 */
fun gridColumns(width: Dp): Int = when {
    width < 560.dp -> 1
    width < 900.dp -> 2
    width < 1300.dp -> 3
    else -> 4
}

/**
 * [items] laid out [columns] to a row in a lazy list.
 *
 * A row of cards rather than a lazy grid, so the screens keep their list state, and with it
 * the fast scrollbar, the scroll shrink and the scroll position they restore. With one column
 * it is plain [items], each card its own entry, just as before. Each card stays keyed by its
 * own [key] inside the row, so what it remembers follows it when the rows reflow.
 */
inline fun <T> LazyListScope.gridItems(
    items: List<T>,
    columns: Int,
    spacing: Dp,
    noinline key: (T) -> Any,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    if (columns <= 1) {
        items(items, key = key) { itemContent(it) }
        return
    }
    // Rows are read straight out of [items] by index: nothing is copied or chunked each time
    // the list is composed, however long it is.
    val rows = (items.size + columns - 1) / columns
    items(count = rows, key = { row -> key(items[row * columns]) }) { row ->
        val scope = this
        val start = row * columns
        val end = minOf(start + columns, items.size)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top
        ) {
            for (index in start until end) {
                val item = items[index]
                keyed(key(item)) {
                    Column(modifier = Modifier.weight(1f)) { scope.itemContent(item) }
                }
            }
            // The last row keeps its cards at the same width as the rows above it.
            repeat(columns - (end - start)) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}
