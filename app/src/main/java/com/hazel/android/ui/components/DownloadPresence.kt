package com.hazel.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hazel.android.data.HistoryEntry
import com.hazel.android.util.MediaPresence

/**
 * Which of [entries] still have their file on the device, kept current while the screen is
 * open.
 *
 * The answer was previously worked out once, the first time a row appeared, and then never
 * again for as long as the app ran. Deleting a download happens in another app, so the one
 * moment the answer is certain to be stale is the moment the user comes back, which is
 * exactly when it was not being re-read. Every held answer is dropped on each return to the
 * foreground and the list is asked again.
 *
 * The map is returned as it is written to, so a screen that discovers a file is gone while
 * acting on it can say so here rather than waiting for the next return.
 */
@Composable
fun rememberPresence(entries: List<HistoryEntry>): SnapshotStateMap<Long, Boolean> {
    val context = LocalContext.current
    val presence = remember { mutableStateMapOf<Long, Boolean>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Counted up rather than observed directly, so the read below has something to key on.
    var round by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MediaPresence.forget()
                round++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(entries, round) {
        entries.forEach { entry ->
            presence[entry.id] = MediaPresence.exists(context, entry.fileUri)
        }
    }

    return presence
}
