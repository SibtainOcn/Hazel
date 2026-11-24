package com.hazel.android.ui.screens.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.hazel.android.data.HistoryEntry
import com.hazel.android.util.HistoryThumbnails
import kotlinx.coroutines.launch

private enum class Source { LOOKING, COPY, LINK, SYSTEM, NONE }

/**
 * A row's picture: saved copy, then recorded link, then the system thumbnail for the file.
 * Each step is tried only when the previous one has nothing or fails to load. The file is
 * only read while it is present.
 */
@Composable
internal fun HistoryArtworkImage(
    entry: HistoryEntry,
    present: Boolean,
    modifier: Modifier,
    alpha: Float,
    colorFilter: ColorFilter?
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var source by remember(entry.id, entry.thumbnail) { mutableStateOf(Source.LOOKING) }
    var model by remember(entry.id, entry.thumbnail) { mutableStateOf<Any?>(null) }

    fun next(from: Source) {
        when (from) {
            Source.LOOKING, Source.COPY ->
                if (entry.thumbnail != null) {
                    model = entry.thumbnail
                    source = Source.LINK
                } else {
                    model = null
                    source = Source.SYSTEM
                }
            Source.LINK -> {
                model = null
                source = Source.SYSTEM
            }
            Source.SYSTEM, Source.NONE -> {
                model = null
                source = Source.NONE
            }
        }
    }

    LaunchedEffect(entry.id, entry.thumbnail, present, source) {
        when (source) {
            Source.LOOKING -> {
                val kept = HistoryThumbnails.cached(context, entry.id)
                if (kept != null) {
                    model = kept
                    source = Source.COPY
                } else next(Source.COPY)
            }
            Source.SYSTEM -> {
                if (!present) return@LaunchedEffect
                val found = HistoryThumbnails.fromSystem(context, entry.id, entry.fileUri, entry.isVideo)
                if (found != null) model = found else next(Source.SYSTEM)
            }
            else -> Unit
        }
    }

    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            colorFilter = colorFilter,
            onSuccess = {
                // Keep it now, from Coil's bytes, before the link expires.
                if (source == Source.LINK) {
                    scope.launch { HistoryThumbnails.keepFromImageCache(context, entry.id, entry.thumbnail) }
                }
            },
            onError = {
                scope.launch {
                    if (source == Source.COPY || source == Source.SYSTEM) {
                        HistoryThumbnails.discard(context, entry.id)
                    }
                    if (source == Source.SYSTEM) next(Source.NONE) else next(source)
                }
            },
            modifier = modifier
        )
    }
}
