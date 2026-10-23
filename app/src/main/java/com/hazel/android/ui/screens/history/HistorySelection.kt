package com.hazel.android.ui.screens.history

import com.hazel.android.data.HistoryEntry

/** Selection on the downloads list, keyed by entry id and limited to what the list shows. */
internal object HistorySelection {

    fun toggle(selected: Set<Long>, id: Long): Set<Long> =
        if (id in selected) selected - id else selected + id

    fun all(visible: List<HistoryEntry>): Set<Long> = visible.mapTo(LinkedHashSet()) { it.id }

    fun allChosen(selected: Set<Long>, visible: List<HistoryEntry>): Boolean =
        visible.isNotEmpty() && visible.all { it.id in selected }

    /** Drops ids that are no longer on the list, so hidden or removed rows are never acted on. */
    fun prune(selected: Set<Long>, visible: List<HistoryEntry>): Set<Long> {
        if (selected.isEmpty()) return selected
        val ids = visible.mapTo(HashSet()) { it.id }
        return selected.filterTo(LinkedHashSet()) { it in ids }
    }

    /** The entries an action applies to: only selected ones that are on the list now. */
    fun targets(selected: Set<Long>, visible: List<HistoryEntry>): List<HistoryEntry> =
        visible.filter { it.id in selected }
}
